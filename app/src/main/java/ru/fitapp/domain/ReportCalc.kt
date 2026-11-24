package ru.fitapp.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max

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
data class Interval(val low: Double, val high: Double)

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

data class EnergyBlock(val interval: Interval, val targetKcal: Int, val verdict: EnergyVerdict)

data class NutrientBlock(val interval: Interval, val targetG: Int, val verdict: NutrientVerdict)

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

data class DayReport(
    val energy: EnergyBlock,
    val protein: NutrientBlock,
    val fat: NutrientBlock,
    val carbs: NutrientBlock,
    val composition: CompositionBlock,
    val regimen: RegimenBlock,
    /** «мало данных»: DONE-карточек < 3 или есть FAILED (§8.3). */
    val littleData: Boolean,
)

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

    /**
     * @param doneMeals карточки со статусом DONE (время приёма + компоненты).
     * @param failedCount число карточек со статусом FAILED за день.
     */
    fun computeDay(
        doneMeals: List<ReportMeal>,
        failedCount: Int,
        targets: DailyTargets,
        zone: ZoneId = ZoneId.systemDefault(),
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
        )
    }
}
