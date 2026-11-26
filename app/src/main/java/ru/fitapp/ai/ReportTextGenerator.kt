package ru.fitapp.ai

import android.os.SystemClock
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
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
import ru.fitapp.domain.AdviceTone
import ru.fitapp.domain.DayReport
import ru.fitapp.domain.EnergyVerdict
import ru.fitapp.domain.LimitVerdict
import ru.fitapp.domain.NutrientVerdict
import ru.fitapp.domain.NutritionAdvice
import ru.fitapp.domain.ReportCalc
import ru.fitapp.domain.pluralRu

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
 * числа, вердикты и советы считает код, LLM связывает их в связный текст
 * и не добавляет новых чисел.
 */
class ReportTextGenerator(
    private val apiKeyProvider: () -> String?,
    private val client: OkHttpClient = defaultClient(),
) {

    /** Сериализует посчитанный отчёт в JSON-вход для LLM (числа, вердикты, советы). */
    fun buildInputJson(report: DayReport): String {
        val advice = NutritionAdvice.build(report)
        val root = buildJsonObject {
            put("little_data", report.littleData)
            put("energy", buildJsonObject {
                put("low_kcal", round(report.energy.interval.low))
                put("high_kcal", round(report.energy.interval.high))
                put("target_kcal", report.energy.targetKcal)
                put("gap_kcal", round(report.energy.gapKcal))
                put("percent_of_target", report.energy.percentOfTarget)
                put("verdict", report.energy.verdict.label)
            })
            put("protein", nutrientJson(report.protein.interval.low, report.protein.interval.high, report.protein.targetG, report.protein.verdict.label))
            put("fat", nutrientJson(report.fat.interval.low, report.fat.interval.high, report.fat.targetG, report.fat.verdict.label))
            put("carbs", nutrientJson(report.carbs.interval.low, report.carbs.interval.high, report.carbs.targetG, report.carbs.verdict.label))
            put("regimen", buildJsonObject {
                put("meals", report.regimen.mealCount)
                put("last_intake", report.regimen.lastIntake?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "нет")
                put("late_meals_after_21", report.regimen.lateMeals)
                put("longest_gap_min", report.regimen.longestGapMinutes ?: 0)
                put("longest_gap_h", (report.regimen.longestGapMinutes ?: 0) / 60)
            })
            put("composition", buildJsonObject {
                put("fiber_g_low", round(report.composition.fiberInterval.low))
                put("fiber_g_high", round(report.composition.fiberInterval.high))
                put("fiber_target_g", report.fiberTargetG)
                put("fiber_gap_g", round(report.fiberGapG))
                put("fiber_verdict", report.fiberVerdict.label)
                put("sugar_g_low", round(report.composition.sugarInterval.low))
                put("sugar_g_high", round(report.composition.sugarInterval.high))
                put("sugar_limit_g", report.sugarLimitG)
                put("sugar_over_g", round(report.sugarOverLimitG))
                put("sugar_verdict", report.sugarVerdict.label)
                put("ultra_processed_items", report.composition.ultraProcessedItems)
                put("total_items", report.composition.totalItems)
            })
            report.history.avgKcal?.let { avg ->
                put("history", buildJsonObject {
                    put("avg_kcal", avg)
                    put("days_with_data", report.history.daysWithData)
                    report.history.prevDayKcal?.let { put("prev_day_kcal", it) }
                })
            }
            put("advice", buildJsonArray {
                advice.forEach { item ->
                    add(buildJsonObject {
                        put("id", item.id)
                        put("tone", when (item.tone) {
                            AdviceTone.GOOD -> "good"
                            AdviceTone.WARN -> "warn"
                            AdviceTone.TIP -> "tip"
                        })
                        put("title", item.title)
                        put("detail", item.detail)
                    })
                }
            })
        }
        return root.toString()
    }

    private fun nutrientJson(low: Double, high: Double, target: Int, verdict: String) = buildJsonObject {
        put("low_g", round(low))
        put("high_g", round(high))
        put("target_g", target)
        put("gap_g", round(target - (low + high) / 2.0))
        put("percent_of_target", if (target > 0) ((low + high) / 2.0 / target * 100).roundToInt() else 0)
        put("verdict", verdict)
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

    /**
     * Fallback-текст: собирается кодом из вердиктов и разрывов, без LLM (§8.3).
     * Это не «энергия в норме» — в каждом предложении есть разрыв с целью и действие.
     */
    fun buildFallbackText(report: DayReport): String {
        val sb = StringBuilder()
        if (report.littleData) {
            sb.append("Мало данных: готовых карточек ")
            sb.append(report.regimen.mealCount)
            sb.append(" — выводы приблизительные. ")
        }

        sb.append("Энергия: ").append(round(report.energy.interval.low)).append("–")
        sb.append(round(report.energy.interval.high)).append(" ккал при цели ")
        sb.append(report.energy.targetKcal).append(" — ").append(report.energy.verdict.label)
        val energyGap = report.energy.gapKcal
        if (report.energy.verdict != EnergyVerdict.WITHIN_UNCERTAINTY && abs(energyGap) >= 1.0) {
            sb.append(if (energyGap > 0) ", не хватает ≈" else ", выше на ≈")
            sb.append(round(abs(energyGap))).append(" ккал")
        }
        sb.append(". ")

        sb.append("Белок: ").append(round(report.protein.interval.low)).append("–")
        sb.append(round(report.protein.interval.high)).append(" г при цели ")
        sb.append(report.protein.targetG).append(" г — ").append(friendly(report.protein.verdict))
        if (report.protein.verdict == NutrientVerdict.BELOW) {
            sb.append(", добавьте ≈").append(round(report.protein.gapG))
            sb.append(" г: творог, курица, рыба или яйца")
        }
        sb.append(". Жиры: ").append(round(report.fat.interval.low)).append("–")
        sb.append(round(report.fat.interval.high)).append(" г при цели ")
        sb.append(report.fat.targetG).append(" — ").append(friendly(report.fat.verdict))
        sb.append(". Углеводы: ").append(round(report.carbs.interval.low)).append("–")
        sb.append(round(report.carbs.interval.high)).append(" г при цели ")
        sb.append(report.carbs.targetG).append(" — ").append(friendly(report.carbs.verdict)).append(". ")

        sb.append("Клетчатка: ").append(round(report.composition.fiberInterval.low)).append("–")
        sb.append(round(report.composition.fiberInterval.high)).append(" г при цели ")
        sb.append(report.fiberTargetG).append(" г — ").append(friendly(report.fiberVerdict))
        if (report.fiberVerdict == NutrientVerdict.BELOW) {
            sb.append(": овощи в каждый приём пищи закроют разрыв ≈")
            sb.append(round(report.fiberGapG)).append(" г")
        }
        sb.append(". Сахар: ").append(round(report.composition.sugarInterval.low)).append("–")
        sb.append(round(report.composition.sugarInterval.high)).append(" г при лимите ")
        sb.append(report.sugarLimitG).append(" — ").append(report.sugarVerdict.label)
        if (report.sugarVerdict == LimitVerdict.ABOVE) {
            sb.append(", выше на ≈").append(round(report.sugarOverLimitG)).append(" г")
        }
        sb.append(". ")

        sb.append("Режим: ").append(pluralRu(report.regimen.mealCount, "приём", "приёма", "приёмов"))
        sb.append(" пищи")
        report.regimen.lastIntake?.let {
            sb.append(", последний в ").append("%02d:%02d".format(it.hour, it.minute))
        }
        val longestGap = report.regimen.longestGapMinutes
        if (longestGap != null && longestGap >= 360) {
            sb.append(", самый длинный перерыв ").append(longestGap / 60).append(" ч")
        }
        sb.append(". ")
        if (report.regimen.lateMeals > 0) {
            sb.append("После ").append(ReportCalc.LATE_MEAL_HOUR).append(":00 было ")
            sb.append(pluralRu(report.regimen.lateMeals, "приём", "приёма", "приёмов"))
            sb.append(" — ужин лучше сдвинуть на час раньше. ")
        }
        if (report.composition.ultraProcessedItems > 0) {
            sb.append("Ультрапереработанных продуктов: ")
            sb.append(report.composition.ultraProcessedItems).append(" из ")
            sb.append(report.composition.totalItems).append(" — замените один на цельный. ")
        }
        val avg = report.history.avgKcal
        if (avg != null && report.history.daysWithData > 0) {
            sb.append("Среднее за ").append(report.history.daysWithData).append(" дн.: ")
            sb.append(avg).append(" ккал")
            report.history.prevDayKcal?.let { sb.append(", вчера ").append(it).append(" ккал") }
            sb.append(". ")
        }
        return sb.toString().trim()
    }

    /** «Не определить» в тексте читается как отписка; честнее сказать, что цифра в пределах точности. */
    private fun friendly(verdict: NutrientVerdict): String = when (verdict) {
        NutrientVerdict.BELOW -> "ниже цели"
        NutrientVerdict.ABOVE -> "выше цели"
        NutrientVerdict.UNDETERMINED -> "в пределах точности оценки"
    }

    private fun round(value: Double): Long = Math.round(value)

    companion object {
        /**
         * Системный промпт отчёта (§8.3): связный разбор и советы, без арифметики и медицины.
         * Числа брать только из входа, включая числа внутри блока advice.
         */
        const val REPORT_SYSTEM_PROMPT =
            "Ты пишешь вечерний разбор дня питания по готовым числам, вердиктам и советам. " +
                "Собери 4–6 предложений на русском, обращаясь на «вы»: главный вывод по энергии, " +
                "чего не хватает или где перебор по составу, что поправить в режиме, " +
                "и 1–2 конкретных действия на завтра с продуктами из блока advice. " +
                "Опирайся на поле gap (разрыв с целью) и на поле advice — это готовые советы, их и раскрывай. " +
                "НЕ добавляй новых чисел: используй только числа из входного JSON, включая числа внутри advice. " +
                "Числа пиши цифрами без пробелов между разрядами. Не выполняй арифметику. " +
                "Не ставь диагнозов и не давай медицинских предписаний: только еда, порции и режим дня. " +
                "Если little_data равно true — первым предложением скажи, что данных мало и выводы приблизительные. " +
                "Без вступлений вроде «итак» и без пересказа структуры JSON."

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
