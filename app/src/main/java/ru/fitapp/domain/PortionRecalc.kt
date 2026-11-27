package ru.fitapp.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Пищевая ценность порции. Одна структура описывает и «сырой» ответ модели,
 * и текущие числа карточки после правок массы или отметки «С сахаром».
 */
data class Nutrition(
    val kcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val fatG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fiberG: Double = 0.0,
    val sugarG: Double = 0.0,
) {
    /** Пропорциональный пересчёт на другую массу: 100 г → 150 г даёт коэффициент 1.5. */
    fun scaled(factor: Double): Nutrition = copy(
        kcal = kcal * factor,
        proteinG = proteinG * factor,
        fatG = fatG * factor,
        carbsG = carbsG * factor,
        fiberG = fiberG * factor,
        sugarG = sugarG * factor,
    )

    /** Добавленный сахар: 1 г сахара — 4 ккал и 1 г углеводов (§8.2: 4 ккал/г). */
    fun withAddedSugar(sugarG: Double): Nutrition = copy(
        kcal = kcal + sugarG * PortionRecalc.KCAL_PER_G_SUGAR,
        carbsG = carbsG + sugarG,
        sugarG = this.sugarG + sugarG,
    )
}

/**
 * Пересчёт КБЖУ компонента карточки.
 *
 * Числа разбора модели неизменяемы и лежат в `base_*`: любая правка (масса, отметка
 * сахара, новое название) считается от базы, а не от предыдущего результата, поэтому
 * серия правок не накапливает ошибку округления и всегда отменяема.
 */
object PortionRecalc {

    /** Порция сахара, которую добавляет отметка «С сахаром»: две чайные ложки без горки. */
    const val ADDED_SUGAR_G = 10.0

    /** Энергия сахара — 4 ккал/г (как в [ReportCalc]). */
    const val KCAL_PER_G_SUGAR = 4.0

    /** Границы правки массы: защита от опечатки «15000». */
    const val MIN_GRAMS = 1.0
    const val MAX_GRAMS = 5000.0

    /** Массу можно править только тогда, когда модель оценила исходный вес. */
    fun canEditGrams(baseGrams: Double?): Boolean = baseGrams != null && baseGrams > 0.0

    /** Коэффициент пересчёта базы на текущую массу; 1.0 — если массу менять нельзя. */
    fun scaleFactor(baseGrams: Double?, grams: Double?): Double {
        if (baseGrams == null || baseGrams <= 0.0) return 1.0
        if (grams == null || grams <= 0.0) return 1.0
        return grams / baseGrams
    }

    /**
     * Текущие КБЖУ компонента: база модели × коэффициент массы + добавленный сахар.
     * @param addedSugarG сколько сахара добавлено отметкой (0 — без сахара).
     */
    fun effective(
        base: Nutrition,
        baseGrams: Double?,
        grams: Double?,
        addedSugarG: Double = 0.0,
    ): Nutrition {
        val scaled = base.scaled(scaleFactor(baseGrams, grams))
        return if (addedSugarG > 0.0) scaled.withAddedSugar(addedSugarG) else scaled
    }

    /**
     * Разбор введённой массы. Принимает и точку, и запятую; пустая строка, мусор
     * и значения вне [MIN_GRAMS]..[MAX_GRAMS] дают null — вызывающий код показывает ошибку.
     */
    fun parseGrams(raw: String): Double? {
        val normalized = raw.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        val value = normalized.toDoubleOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        if (value < MIN_GRAMS || value > MAX_GRAMS) return null
        return value
    }

    /** Масса для поля ввода: без хвоста «.0», дробная — с одним знаком. */
    fun gramsToInput(grams: Double?): String {
        if (grams == null) return ""
        val rounded = grams.roundToInt()
        return if (abs(grams - rounded) < 0.05) {
            rounded.toString()
        } else {
            String.format(Locale.forLanguageTag("ru"), "%.1f", grams)
        }
    }
}