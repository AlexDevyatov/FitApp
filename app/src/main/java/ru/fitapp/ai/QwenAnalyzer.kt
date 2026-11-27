package ru.fitapp.ai

import android.os.SystemClock
import android.util.Base64
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
 *
 * Один и тот же контракт используется для двух запросов: разбор кадра с фото
 * и текстовый пересчёт КБЖУ по названию (правка названия компонента).
 */
class QwenAnalyzer(
    private val apiKeyProvider: () -> String?,
    private val client: OkHttpClient = defaultClient(),
) : DishAnalyzer {

    override suspend fun analyze(jpeg: ByteArray): AnalyzeOutcome = withContext(Dispatchers.IO) {
        val content = buildJsonArray {
            add(textPart(AnalyzePrompts.SYSTEM_PROMPT))
            add(imagePart(jpeg))
        }
        send(content = content, imageBytes = jpeg.size, promptChars = null)
    }

    override suspend fun estimate(name: String, grams: Double?): AnalyzeOutcome =
        withContext(Dispatchers.IO) {
            val prompt = AnalyzePrompts.estimate(name, grams)
            AiLog.info("estimate: запрос по названию, chars=${prompt.length} grams=$grams")
            val content = buildJsonArray { add(textPart(prompt)) }
            send(content = content, imageBytes = null, promptChars = prompt.length)
        }

    override suspend fun probeKey(): Boolean = probeKeyDetailed() is KeyProbeResult.Valid

    override suspend fun probeKeyDetailed(): KeyProbeResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey == null) {
            AiLog.warn("probe: API-ключ не сохранён")
            return@withContext KeyProbeResult.InvalidKey("API-ключ не сохранён")
        }
        val request = Request.Builder()
            .url("$BASE_URL/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        val startedAt = SystemClock.elapsedRealtime()
        val result: KeyProbeResult = try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                AiLog.http(response.code, SystemClock.elapsedRealtime() - startedAt, text.length)
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
        AiLog.warn("probe result=$result")
        result
    }

    /**
     * Общий путь запроса chat/completions: ключ, тело, HTTP и разбор ошибок.
     * Отличается только содержимое `content` — фото или текст.
     */
    private fun send(content: JsonArray, imageBytes: Int?, promptChars: Int?): AnalyzeOutcome {
        val apiKey = apiKeyProvider()
        if (apiKey == null) {
            AiLog.error("request: API-ключ не сохранён, запрос не отправлен")
            return AnalyzeOutcome.AuthError("API-ключ не сохранён")
        }

        val body = buildJsonObject {
            put("model", MODEL)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            // content обязан быть МАССИВОМ частей (§4.2)
                            put("content", content)
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

        AiLog.request(model = MODEL, imageBytes = imageBytes, promptChars = promptChars, maxTokens = MAX_TOKENS)
        val startedAt = SystemClock.elapsedRealtime()

        val outcome: AnalyzeOutcome = try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                AiLog.http(response.code, SystemClock.elapsedRealtime() - startedAt, text.length)
                when {
                    response.isSuccessful -> parseCompletion(text)
                    response.code == 401 -> {
                        AiLog.error("request: провайдер отверг ключ http=401 body=${AiLog.excerpt(text)}")
                        AnalyzeOutcome.AuthError(errorMessage(text) ?: "Ключ неверен или просрочен")
                    }
                    response.code == 400 -> {
                        AiLog.error("request: провайдер отклонил запрос http=400 body=${AiLog.excerpt(text)}")
                        AnalyzeOutcome.Permanent(
                            "Провайдер отклонил запрос (400): ${errorMessage(text) ?: "битый запрос или схема"}",
                        )
                    }
                    response.code == 429 -> {
                        AiLog.warn("request: лимит запросов http=429 retry_after=${response.header("Retry-After")}")
                        AnalyzeOutcome.Retryable(
                            reason = "Лимит запросов (429)",
                            retryAfterMs = retryAfterMs(response.header("Retry-After")),
                        )
                    }
                    response.code in 500..599 -> {
                        AiLog.warn("request: ошибка провайдера http=${response.code} body=${AiLog.excerpt(text)}")
                        AnalyzeOutcome.Retryable("Ошибка провайдера (${response.code})")
                    }
                    else -> {
                        AiLog.warn("request: неожиданный ответ http=${response.code} body=${AiLog.excerpt(text)}")
                        AnalyzeOutcome.Retryable("Неожиданный ответ провайдера (${response.code})")
                    }
                }
            }
        } catch (e: SSLException) {
            // TLS-ошибка: неизвестный CA, отдельная подсказка (§4.4, §11)
            AiLog.error("request: TLS не проверен (${e.javaClass.simpleName})", e)
            AnalyzeOutcome.Permanent(
                "Не удалось проверить сертификат сервера (${e.javaClass.simpleName}). " +
                    "На устройстве не установлен доверенный CA-сертификат провайдера.",
            )
        } catch (e: IOException) {
            AiLog.warn(
                "request: сеть недоступна или провайдер не ответил " +
                    "(${e.javaClass.simpleName}: ${AiLog.excerpt(e.message.orEmpty())})",
            )
            AnalyzeOutcome.Retryable("Сеть недоступна или провайдер не ответил (${e.javaClass.simpleName})")
        }

        AiLog.outcome(outcome)
        return outcome
    }

    private fun textPart(text: String): JsonObject = buildJsonObject {
        put("type", "text")
        put("text", text)
    }

    private fun imagePart(jpeg: ByteArray): JsonObject = buildJsonObject {
        put("type", "image_url")
        put("image_url", buildJsonObject {
            val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
            put("url", "data:image/jpeg;base64,$b64")
        })
    }

    /** Разбор успешного ответа chat/completions: JSON берём из message.content, reasoning_content игнорируем (§4.2). */
    private fun parseCompletion(responseText: String): AnalyzeOutcome {
        val root = runCatching { Json.parseToJsonElement(responseText).jsonObject }.getOrElse {
            AiLog.error("response: ответ провайдера не JSON: ${AiLog.excerpt(responseText)}")
            return AnalyzeOutcome.Permanent("Ответ провайдера не является JSON")
        }
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: run {
                AiLog.error("response: в ответе нет choices: ${AiLog.excerpt(responseText)}")
                return AnalyzeOutcome.Permanent("В ответе провайдера нет choices")
            }
        val message = runCatching { choice["message"]?.jsonObject }.getOrNull()
        val stats = readCompletionStats(root, choice, message)
        AiLog.completion(stats)

        val content = message?.get("content")?.jsonPrimitive?.contentOrNull
        if (stats.truncated) {
            // §4.2: при length JSON невалиден. Причина почти всегда — reasoning,
            // съевший бюджет max_tokens; длина рассуждения плавает от запуска к запуску,
            // поэтому это Retryable, а не Permanent.
            AiLog.warn(
                "response: ответ обрезан max_tokens=$MAX_TOKENS " +
                    "(completion=${stats.completionTokens}, reasoning=${stats.reasoningTokens}, " +
                    "answer_chars=${stats.answerChars}); хвост: ${AiLog.excerpt(content.orEmpty().takeLast(120))}",
            )
            return AnalyzeOutcome.Retryable(
                "Модель не уложилась в лимит ответа (finish_reason=length). " +
                    "Снимок не разобран, попробуем ещё раз.",
            )
        }
        if (content == null) {
            AiLog.error("response: в ответе нет message.content: ${AiLog.excerpt(responseText)}")
            return AnalyzeOutcome.Permanent("В ответе провайдера нет message.content")
        }
        val dto = runCatching { frameJson.decodeFromString(FrameResponseDto.serializer(), content) }
            .getOrElse {
                AiLog.error(
                    "response: ответ не соответствует схеме (${it.message}): ${AiLog.excerpt(content)}",
                )
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

        /**
         * Бюджет ответа. qwen4-max — reasoning-модель: токены рассуждения входят в
         * max_tokens, а JSON разбора на 3–5 компонентов занимает ~500 токенов.
         * При 600 ответ систематически обрывался на finish_reason=length
         * (наблюдалось: reasoning 426 токенов + обрезанный JSON), поэтому запас большой.
         */
        const val MAX_TOKENS = 3000

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
    }
}