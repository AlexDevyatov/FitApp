package ru.fitapp.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.roundToInt

/** Элемент карточки для расчёта отчёта (только числа, без LLM). */
data class ReportItem(
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val fiberG: Double,
    val sugarG: Double,
    val ultraProcessed: Boolean,
)

/** Приём пищи: время + компоненты. В отчёт передаются только DONE-карточки. */
data class ReportMeal(
    val takenAtEpochMs: Long,
    val items: List<ReportItem>,
)

/** Интервал оценки ±30% (§8.2 PLAN). */
data class Interval(val low: Double, val high: Double) {
    /** Середина интервала: совпадает с суммой оценок по карточкам. */
    val mid: Double get() = (low + high) / 2.0
}

/** Вердикт по энергии (§8.2 PLAN). */
enum class EnergyVerdict(val label: String) {
    LIKELY_DEFICIT("скорее дефицит"),
    LIKELY_EXCESS("скорее перебор"),
    WITHIN_UNCERTAINTY("в пределах неопределённости"),
}

/** Вердикт по нутриенту: выносится только если весь интервал по одну сторону цели. */
enum class NutrientVerdict(val label: String) {
    BELOW("ниже цели"),
    ABOVE("выше цели"),
    UNDETERMINED("не определить"),
}

/** Вердикт по лимиту (сахар): выносится, только если весь интервал по одну сторону лимита. */
enum class LimitVerdict(val label: String) {
    WITHIN("в пределах лимита"),
    ABOVE("выше лимита"),
    UNDETERMINED("не определить"),
}

data class EnergyBlock(val interval: Interval, val targetKcal: Int, val verdict: EnergyVerdict) {
    /** Оценка за день по середине интервала. */
    val midKcal: Double get() = interval.mid

    /** Сколько не хватает до цели; отрицательное значение — перебор. */
    val gapKcal: Double get() = targetKcal - midKcal

    /** Доля цели в процентах: 100 — ровно цель. */
    val percentOfTarget: Int get() = if (targetKcal > 0) (midKcal / targetKcal * 100).roundToInt() else 0
}

data class NutrientBlock(val interval: Interval, val targetG: Int, val verdict: NutrientVerdict) {
    /** Сколько не хватает до цели; отрицательное значение — перебор. */
    val gapG: Double get() = targetG - interval.mid

    val percentOfTarget: Int get() = if (targetG > 0) (interval.mid / targetG * 100).roundToInt() else 0
}

/** Клетчатка/овощи, сахар и ультрапереработанное (§8.3, блок «Состав»). */
data class CompositionBlock(
    val fiberInterval: Interval,
    val sugarInterval: Interval,
    val ultraProcessedItems: Int,
    val totalItems: Int,
)

/** Блок «Режим» (§8.3). */
data class RegimenBlock(
    val mealCount: Int,
    val lastIntake: LocalTime?,
    val lateMeals: Int,
    val longestGapMinutes: Long?,
)

/**
 * Контекст недели: чем сегодняшний день отличается от предыдущих.
 * Считается по уже сохранённым дням, пустые дни в среднее не попадают.
 */
data class DayHistory(
    val avgKcal: Int? = null,
    val daysWithData: Int = 0,
    val prevDayKcal: Int? = null,
) {
    val hasData: Boolean get() = avgKcal != null && daysWithData > 0
}

data class DayReport(
    val energy: EnergyBlock,
    val protein: NutrientBlock,
    val fat: NutrientBlock,
    val carbs: NutrientBlock,
    val composition: CompositionBlock,
    val regimen: RegimenBlock,
    /** «мало данных»: DONE-карточек < 3 или есть FAILED (§8.3). */
    val littleData: Boolean,
    /** Контекст предыдущих дней: среднее и вчерашний день. */
    val history: DayHistory = DayHistory(),
) {
    /** Рекомендуемая клетчатка — 14 г на 1000 ккал (§8.3). */
    val fiberTargetG: Int get() = ReportCalc.fiberTargetG(energy.targetKcal)

    /** Лимит сахара — 10% калорий цели. */
    val sugarLimitG: Int get() = ReportCalc.sugarLimitG(energy.targetKcal)

    /** Сколько клетчатки не хватает до рекомендации (может быть отрицательным). */
    val fiberGapG: Double get() = fiberTargetG - composition.fiberInterval.mid

    /** Насколько сахар выше лимита (может быть отрицательным). */
    val sugarOverLimitG: Double get() = composition.sugarInterval.mid - sugarLimitG

    val fiberVerdict: NutrientVerdict
        get() = ReportCalc.nutrientVerdict(composition.fiberInterval, fiberTargetG)

    val sugarVerdict: LimitVerdict
        get() = ReportCalc.limitVerdict(composition.sugarInterval, sugarLimitG)

    /** Доля ультрапереработанных компонентов, 0..1. */
    val ultraProcessedShare: Double
        get() = if (composition.totalItems > 0) {
            composition.ultraProcessedItems.toDouble() / composition.totalItems
        } else {
            0.0
        }
}

/**
 * Расчёт дневного отчёта по §8.2–8.3 PLAN. Всё считается кодом; LLM только формулирует текст.
 */
object ReportCalc {
    /** Каждая оценка кадра несёт ±30% (§8.2). */
    const val UNCERTAINTY = 0.30

    /** Порог «позднего приёма» — 21:00 (§8.3, блок «Режим»). */
    const val LATE_MEAL_HOUR = 21

    /** Минимум DONE-карточек для полного дня (§8.3). */
    const val MIN_DONE_MEALS = 3

    /** Клетчатка: 14 г на 1000 ккал — нижняя граница рекомендаций ВОЗ. */
    const val FIBER_G_PER_1000_KCAL = 14.0

    /** Сахар: не больше 10% калорий цели. */
    const val SUGAR_KCAL_SHARE = 0.10
    const val KCAL_PER_G_SUGAR = 4.0

    fun interval(sum: Double): Interval =
        Interval(low = sum * (1.0 - UNCERTAINTY), high = sum * (1.0 + UNCERTAINTY))

    fun energyVerdict(interval: Interval, targetKcal: Int): EnergyVerdict = when {
        interval.high < targetKcal -> EnergyVerdict.LIKELY_DEFICIT
        interval.low > targetKcal -> EnergyVerdict.LIKELY_EXCESS
        else -> EnergyVerdict.WITHIN_UNCERTAINTY
    }

    fun nutrientVerdict(interval: Interval, targetG: Int): NutrientVerdict = when {
        interval.high < targetG -> NutrientVerdict.BELOW
        interval.low > targetG -> NutrientVerdict.ABOVE
        else -> NutrientVerdict.UNDETERMINED
    }

    fun limitVerdict(interval: Interval, limitG: Int): LimitVerdict = when {
        interval.high < limitG -> LimitVerdict.WITHIN
        interval.low > limitG -> LimitVerdict.ABOVE
        else -> LimitVerdict.UNDETERMINED
    }

    /** Рекомендуемая клетчатка для цели по калориям. */
    fun fiberTargetG(targetKcal: Int): Int =
        (targetKcal / 1000.0 * FIBER_G_PER_1000_KCAL).roundToInt()

    /** Лимит сахара для цели по калориям. */
    fun sugarLimitG(targetKcal: Int): Int =
        (targetKcal * SUGAR_KCAL_SHARE / KCAL_PER_G_SUGAR).roundToInt()

    /**
     * @param doneMeals карточки со статусом DONE (время приёма + компоненты).
     * @param failedCount число карточек со статусом FAILED за день.
     * @param history среднее по прошлым дням (необязательно).
     */
    fun computeDay(
        doneMeals: List<ReportMeal>,
        failedCount: Int,
        targets: DailyTargets,
        zone: ZoneId = ZoneId.systemDefault(),
        history: DayHistory = DayHistory(),
    ): DayReport {
        val allItems = doneMeals.flatMap { it.items }

        val kcalInterval = interval(allItems.sumOf { it.kcal })
        val proteinInterval = interval(allItems.sumOf { it.proteinG })
        val fatInterval = interval(allItems.sumOf { it.fatG })
        val carbsInterval = interval(allItems.sumOf { it.carbsG })
        val fiberInterval = interval(allItems.sumOf { it.fiberG })
        val sugarInterval = interval(allItems.sumOf { it.sugarG })

        val sorted = doneMeals.sortedBy { it.takenAtEpochMs }
        val times = sorted.map { Instant.ofEpochMilli(it.takenAtEpochMs).atZone(zone) }
        val lastIntake = times.lastOrNull()?.toLocalTime()
        val lateMeals = times.count { it.hour >= LATE_MEAL_HOUR }
        var longestGapMinutes: Long? = null
        for (i in 1 until sorted.size) {
            val gapMin = (sorted[i].takenAtEpochMs - sorted[i - 1].takenAtEpochMs) / 60_000L
            longestGapMinutes = max(longestGapMinutes ?: gapMin, gapMin)
        }

        return DayReport(
            energy = EnergyBlock(kcalInterval, targets.kcal, energyVerdict(kcalInterval, targets.kcal)),
            protein = NutrientBlock(proteinInterval, targets.proteinG, nutrientVerdict(proteinInterval, targets.proteinG)),
            fat = NutrientBlock(fatInterval, targets.fatG, nutrientVerdict(fatInterval, targets.fatG)),
            carbs = NutrientBlock(carbsInterval, targets.carbsG, nutrientVerdict(carbsInterval, targets.carbsG)),
            composition = CompositionBlock(
                fiberInterval = fiberInterval,
                sugarInterval = sugarInterval,
                ultraProcessedItems = allItems.count { it.ultraProcessed },
                totalItems = allItems.size,
            ),
            regimen = RegimenBlock(
                mealCount = sorted.size,
                lastIntake = lastIntake,
                lateMeals = lateMeals,
                longestGapMinutes = longestGapMinutes,
            ),
            littleData = sorted.size < MIN_DONE_MEALS || failedCount > 0,
            history = history,
        )
    }
}
