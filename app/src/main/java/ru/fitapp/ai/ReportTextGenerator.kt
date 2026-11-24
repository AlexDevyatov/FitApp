package ru.fitapp.ai

import android.os.SystemClock
import java.util.concurrent.TimeUnit
import kotlin.math.abs
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
import ru.fitapp.domain.DayReport
import ru.fitapp.domain.ReportCalc

/**
 * Регулярка чисел для проверки «текст не может содержать новых чисел» (§8.3 PLAN).
 */
private val NUMBER_REGEX = Regex("""\d+(?:[.,]\d+)?""")

/**
 * Проверка текста отчёта: все числа текста должны присутствовать во входном JSON.
 * При нарушении или пустом тексте — fallback, собранный кодом из вердиктов.
 */
object ReportTextSanitizer {

    fun numbersIn(text: String): Set<Double> =
        NUMBER_REGEX.findAll(text)
            .mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }
            .toSet()

    fun containsNewNumbers(text: String, sourceJson: String): Boolean {
        val allowed = numbersIn(sourceJson)
        return numbersIn(text).any { value -> allowed.none { abs(it - value) < 1e-9 } }
    }

    fun sanitize(text: String, sourceJson: String, fallback: String): String =
        if (text.isBlank() || containsNewNumbers(text, sourceJson)) fallback else text.trim()
}

/**
 * Формулирование отчёта через chat/completions (§8.3 PLAN):
 * числа и вердикты считает код, LLM пишет 2–3 фразы на блок и не добавляет новых чисел.
 */
class ReportTextGenerator(
    private val apiKeyProvider: () -> String?,
    private val client: OkHttpClient = defaultClient(),
) {

    /** Сериализует посчитанный отчёт в JSON-вход для LLM (числа + вердикты). */
    fun buildInputJson(report: DayReport): String {
        val root = buildJsonObject {
            put("little_data", report.littleData)
            put("energy", buildJsonObject {
                put("low_kcal", round(report.energy.interval.low))
                put("high_kcal", round(report.energy.interval.high))
                put("target_kcal", report.energy.targetKcal)
                put("verdict", report.energy.verdict.label)
            })
            put("protein", nutrientJson(report))
            put("regimen", buildJsonObject {
                put("meals", report.regimen.mealCount)
                put("last_intake", report.regimen.lastIntake?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "нет")
                put("late_meals_after_21", report.regimen.lateMeals)
                put("longest_gap_min", report.regimen.longestGapMinutes ?: 0)
            })
            put("composition", buildJsonObject {
                put("fiber_g_low", round(report.composition.fiberInterval.low))
                put("fiber_g_high", round(report.composition.fiberInterval.high))
                put("sugar_g_low", round(report.composition.sugarInterval.low))
                put("sugar_g_high", round(report.composition.sugarInterval.high))
                put("ultra_processed_items", report.composition.ultraProcessedItems)
                put("total_items", report.composition.totalItems)
            })
        }
        return root.toString()
    }

    private fun nutrientJson(report: DayReport) = buildJsonObject {
        put("protein_low_g", round(report.protein.interval.low))
        put("protein_high_g", round(report.protein.interval.high))
        put("protein_target_g", report.protein.targetG)
        put("protein_verdict", report.protein.verdict.label)
        put("fat_low_g", round(report.fat.interval.low))
        put("fat_high_g", round(report.fat.interval.high))
        put("fat_target_g", report.fat.targetG)
        put("fat_verdict", report.fat.verdict.label)
        put("carbs_low_g", round(report.carbs.interval.low))
        put("carbs_high_g", round(report.carbs.interval.high))
        put("carbs_target_g", report.carbs.targetG)
        put("carbs_verdict", report.carbs.verdict.label)
    }

    /** Запрос текста отчёта; при любой сетевой ошибке или новых числах — fallback. */
    suspend fun generate(report: DayReport): String = withContext(Dispatchers.IO) {
        val inputJson = buildInputJson(report)
        val fallback = buildFallbackText(report)
        val apiKey = apiKeyProvider()
        if (apiKey == null) {
            AiLog.warn("report: API-ключ не сохранён, отдан fallback")
            return@withContext fallback
        }

        val body = buildJsonObject {
            put("model", QwenAnalyzer.MODEL)
            put(
                "messages",
                buildJsonArray {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", REPORT_SYSTEM_PROMPT)
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", inputJson)
                    })
                },
            )
            put("max_tokens", MAX_TOKENS)
        }

        val request = Request.Builder()
            .url("${QwenAnalyzer.BASE_URL}/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            AiLog.request(
                model = QwenAnalyzer.MODEL,
                imageBytes = null,
                promptChars = inputJson.length,
                maxTokens = MAX_TOKENS,
            )
            val startedAt = SystemClock.elapsedRealtime()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                AiLog.http(response.code, SystemClock.elapsedRealtime() - startedAt, text.length)
                if (!response.isSuccessful) {
                    AiLog.warn("report: провайдер отказал http=${response.code} body=${AiLog.excerpt(text)}")
                    return@use fallback
                }
                val content = extractContent(text)
                if (content == null) {
                    AiLog.warn("report: текста нет, отдан fallback")
                    return@use fallback
                }
                val sanitized = ReportTextSanitizer.sanitize(content, inputJson, fallback)
                if (sanitized == fallback) {
                    AiLog.warn("report: текст отбракован санитайзером (новые числа), отдан fallback")
                }
                sanitized
            }
        } catch (e: Exception) {
            AiLog.warn("report: сеть или разбор упали (${e.javaClass.simpleName}: ${AiLog.excerpt(e.message.orEmpty())})")
            fallback
        }
    }

    private fun extractContent(responseText: String): String? {
        val root = runCatching { Json.parseToJsonElement(responseText).jsonObject }.getOrElse {
            AiLog.error("report: ответ провайдера не JSON: ${AiLog.excerpt(responseText)}")
            return null
        }
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: run {
            AiLog.error("report: в ответе нет choices: ${AiLog.excerpt(responseText)}")
            return null
        }
        val message = runCatching { choice["message"]?.jsonObject }.getOrNull()
        val stats = readCompletionStats(root, choice, message)
        AiLog.completion(stats)
        if (stats.truncated) {
            AiLog.warn(
                "report: ответ обрезан max_tokens=$MAX_TOKENS " +
                    "(completion=${stats.completionTokens}, reasoning=${stats.reasoningTokens})",
            )
            return null
        }
        val content = message?.get("content")?.jsonPrimitive?.contentOrNull
        if (content == null) AiLog.error("report: в ответе нет message.content")
        return content
    }

    /** Fallback-текст: собирается кодом из вердиктов, без LLM (§8.3). */
    fun buildFallbackText(report: DayReport): String {
        val sb = StringBuilder()
        if (report.littleData) {
            sb.append("За день мало данных: готовых карточек недостаточно или часть снимков не обработана. ")
        }
        sb.append("Энергия: ").append(report.energy.verdict.label)
        sb.append(" (").append(round(report.energy.interval.low)).append("–")
        sb.append(round(report.energy.interval.high)).append(" ккал при цели ")
        sb.append(report.energy.targetKcal).append("). ")
        sb.append("Состав: белок ").append(report.protein.verdict.label)
        sb.append(", жиры ").append(report.fat.verdict.label)
        sb.append(", углеводы ").append(report.carbs.verdict.label).append(". ")
        sb.append("Режим: ").append(report.regimen.mealCount).append(" приёмов пищи")
        report.regimen.lastIntake?.let {
            sb.append(", последний в ").append("%02d:%02d".format(it.hour, it.minute))
        }
        sb.append(".")
        if (report.regimen.lateMeals > 0) {
            sb.append(" После ").append(ReportCalc.LATE_MEAL_HOUR).append(":00 было ")
            sb.append(report.regimen.lateMeals).append(" приёмов.")
        }
        if (report.composition.ultraProcessedItems > 0) {
            sb.append(" Ультрапереработанных продуктов: ")
            sb.append(report.composition.ultraProcessedItems).append(".")
        }
        return sb.toString()
    }

    private fun round(value: Double): Long = Math.round(value)

    companion object {
        /** Системный промпт отчёта (§8.3): только формулировки, без арифметики и медицины. */
        const val REPORT_SYSTEM_PROMPT =
            "Ты формулируешь дневной отчёт питания по готовым числам и вердиктам. " +
                "Напиши на русском по 2–3 фразы на каждый блок: Энергия, Состав, Режим. " +
                "НЕ добавляй новых чисел — используй только те, что есть во входном JSON. " +
                "Не выполняй арифметику, не давай медицинских советов и предписаний. " +
                "Если little_data равно true — начни с того, что данных за день мало."

        /** Тот же бюджет, что у разбора фото: reasoning-модель тратит его и на рассуждение. */
        private const val MAX_TOKENS = 3000
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
    }
}
