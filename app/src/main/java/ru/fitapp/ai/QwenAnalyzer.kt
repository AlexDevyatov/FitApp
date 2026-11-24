package ru.fitapp.ai

import android.util.Base64
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ru.fitapp.domain.AnalyzeOutcome

/**
 * Реализация DishAnalyzer поверх OpenAI-совместимого API провайдера (§4 PLAN).
 * Ключ никогда не логируется и не попадает в сообщения об ошибках.
 */
class QwenAnalyzer(
    private val apiKeyProvider: () -> String?,
    private val client: OkHttpClient = defaultClient(),
) : DishAnalyzer {

    override suspend fun analyze(jpeg: ByteArray): AnalyzeOutcome = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
            ?: return@withContext AnalyzeOutcome.AuthError("API-ключ не сохранён")

        val body = buildJsonObject {
            put("model", MODEL)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            // content обязан быть МАССИВОМ частей (§4.2)
                            put(
                                "content",
                                buildJsonArray {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", SYSTEM_PROMPT)
                                    })
                                    add(buildJsonObject {
                                        put("type", "image_url")
                                        put("image_url", buildJsonObject {
                                            val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
                                            put("url", "data:image/jpeg;base64,$b64")
                                        })
                                    })
                                },
                            )
                        },
                    )
                },
            )
            put(
                "response_format",
                buildJsonObject {
                    put("type", "json_schema")
                    put("json_schema", buildJsonObject {
                        put("name", "frame")
                        put("strict", true)
                        put("schema", Json.parseToJsonElement(FRAME_JSON_SCHEMA_STRICT).jsonObject)
                    })
                },
            )
            put("max_tokens", MAX_TOKENS)
        }

        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> parseCompletion(text)
                    response.code == 401 -> AnalyzeOutcome.AuthError(
                        errorMessage(text) ?: "Ключ неверен или просрочен",
                    )
                    response.code == 400 -> AnalyzeOutcome.Permanent(
                        "Провайдер отклонил запрос (400): ${errorMessage(text) ?: "битый запрос или схема"}",
                    )
                    response.code == 429 -> AnalyzeOutcome.Retryable(
                        reason = "Лимит запросов (429)",
                        retryAfterMs = retryAfterMs(response.header("Retry-After")),
                    )
                    response.code in 500..599 -> AnalyzeOutcome.Retryable("Ошибка провайдера (${response.code})")
                    else -> AnalyzeOutcome.Retryable("Неожиданный ответ провайдера (${response.code})")
                }
            }
        } catch (e: SSLException) {
            // TLS-ошибка: неизвестный CA, отдельная подсказка (§4.4, §11)
            AnalyzeOutcome.Permanent(
                "Не удалось проверить сертификат сервера (${e.javaClass.simpleName}). " +
                    "На устройстве не установлен доверенный CA-сертификат провайдера.",
            )
        } catch (e: IOException) {
            AnalyzeOutcome.Retryable("Сеть недоступна или провайдер не ответил (${e.javaClass.simpleName})")
        }
    }

    override suspend fun probeKey(): Boolean = probeKeyDetailed() is KeyProbeResult.Valid

    override suspend fun probeKeyDetailed(): KeyProbeResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
            ?: return@withContext KeyProbeResult.InvalidKey("API-ключ не сохранён")
        val request = Request.Builder()
            .url("$BASE_URL/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use when (response.code) {
                        401, 403 -> KeyProbeResult.InvalidKey(
                            errorMessage(text) ?: "Ключ неверен или просрочен",
                        )
                        in 500..599 -> KeyProbeResult.NetworkError("Ошибка провайдера (${response.code})")
                        else -> KeyProbeResult.NetworkError("Неожиданный ответ провайдера (${response.code})")
                    }
                }
                val data = runCatching { Json.parseToJsonElement(text).jsonObject["data"]?.jsonArray }
                    .getOrNull()
                    ?: return@use KeyProbeResult.NetworkError("Ответ провайдера не является JSON каталога моделей")
                val hasModel = data.any { element ->
                    runCatching { element.jsonObject["id"]?.jsonPrimitive?.contentOrNull }.getOrNull() == MODEL
                }
                if (hasModel) KeyProbeResult.Valid else KeyProbeResult.ModelMissing
            }
        } catch (e: SSLException) {
            // TLS-ошибка: неизвестный CA, отдельная подсказка (§4.4, §11)
            KeyProbeResult.TlsError(e.javaClass.simpleName)
        } catch (e: IOException) {
            KeyProbeResult.NetworkError(e.javaClass.simpleName)
        }
    }

    /** Разбор успешного ответа chat/completions: JSON берём из message.content, reasoning_content игнорируем (§4.2). */
    private fun parseCompletion(responseText: String): AnalyzeOutcome {
        val root = runCatching { Json.parseToJsonElement(responseText).jsonObject }.getOrNull()
            ?: return AnalyzeOutcome.Permanent("Ответ провайдера не является JSON")
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return AnalyzeOutcome.Permanent("В ответе провайдера нет choices")
        val finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
        if (finishReason == "length") {
            // §4.2: при length JSON невалиден — это ошибка обработки, а не успех
            return AnalyzeOutcome.Permanent(
                "Модель не закончила ответ: исчерпан лимит токенов (finish_reason=length). " +
                    "Снимок не разобран, попробуйте более простой кадр.",
            )
        }
        val content = choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?: return AnalyzeOutcome.Permanent("В ответе провайдера нет message.content")
        val dto = runCatching { frameJson.decodeFromString(FrameResponseDto.serializer(), content) }
            .getOrElse {
                return AnalyzeOutcome.Permanent("Ответ модели не соответствует схеме разбора: ${it.message}")
            }
        return dto.toOutcome()
    }

    private fun errorMessage(body: String): String? = runCatching {
        Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")
            ?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun retryAfterMs(header: String?): Long? {
        val seconds = header?.trim()?.toIntOrNull() ?: return null
        return if (seconds > 0) seconds * 1000L else null
    }

    companion object {
        const val BASE_URL = "https://token-plan.maas.qwencloudapi.com/compatible-mode/v1"
        const val MODEL = "qwen4-max"
        const val MAX_TOKENS = 600

        /** Системный промпт — §7 PLAN, дословно. Без медицинских утверждений и советов. */
        const val SYSTEM_PROMPT =
            "Ты анализируешь фотографию еды для дневника питания. Определи тип кадра: " +
                "`dish` — одно блюдо или продукт, `plate` — тарелка с несколькими компонентами, " +
                "`label` — этикетка или упаковка с составом, `not_food` — на фото нет еды. " +
                "Разбей кадр на компоненты и для каждого оцени вес в граммах и пищевую ценность " +
                "съеденной порции. Для этикетки бери данные из состава, а не угадывай. " +
                "Не занижай калорийность соусов, масла и обжарки. Уверенность `confidence` — " +
                "честная вероятность того, что разбор верен. `notes` — одна короткая фраза о том, " +
                "что может быть оценено неверно. Отвечай только JSON по схеме."

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
    }
}
