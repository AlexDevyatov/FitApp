package ru.fitapp.domain

import kotlin.math.roundToInt

/** Пол для формулы Mifflin–St Jeor (§8.1 PLAN). */
enum class Sex { MALE, FEMALE }

/** Коэффициенты активности (§3 PLAN). */
enum class Activity(val factor: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.5),
    ACTIVE(1.725),
}

/** Рассчитанные цели на день. */
data class DailyTargets(
    val kcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
)

/** Результат расчёта цели из анкеты. */
data class TdeeGoals(
    val bmr: Double,
    val tdee: Double,
    val targets: DailyTargets,
    /** true, если цель поднята до порога безопасности (§8.1). */
    val raisedToSafetyFloor: Boolean,
)

/**
 * Расчёт цели по §8.1 PLAN:
 * BMR — Mifflin–St Jeor, TDEE = BMR * активность,
 * цель = TDEE * (1 − дефицит), дефицит 15–20%,
 * порог безопасности 1200 (жен) / 1500 (муж) — цель не опускается ниже.
 * Белок 1.6 г/кг, жир 27% ккал, углеводы — остаток.
 */
object Tdee {
    const val SAFETY_FLOOR_FEMALE_KCAL = 1200
    const val SAFETY_FLOOR_MALE_KCAL = 1500
    const val PROTEIN_G_PER_KG = 1.6
    const val FAT_KCAL_SHARE = 0.27
    const val MIN_DEFICIT_PCT = 15
    const val MAX_DEFICIT_PCT = 20

    private const val KCAL_PER_G_PROTEIN = 4.0
    private const val KCAL_PER_G_FAT = 9.0
    private const val KCAL_PER_G_CARBS = 4.0

    fun bmr(sex: Sex, weightKg: Double, heightCm: Double, ageYears: Int): Double {
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        return if (sex == Sex.MALE) base + 5 else base - 161
    }

    fun tdee(bmr: Double, activityFactor: Double): Double = bmr * activityFactor

    fun safetyFloor(sex: Sex): Int =
        if (sex == Sex.MALE) SAFETY_FLOOR_MALE_KCAL else SAFETY_FLOOR_FEMALE_KCAL

    /**
     * @param deficitPct дефицит в процентах, зажимается в [MIN_DEFICIT_PCT, MAX_DEFICIT_PCT].
     */
    fun goals(
        sex: Sex,
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
        activityFactor: Double,
        deficitPct: Int,
    ): TdeeGoals {
        val bmr = bmr(sex, weightKg, heightCm, ageYears)
        val tdee = tdee(bmr, activityFactor)
        val deficit = deficitPct.coerceIn(MIN_DEFICIT_PCT, MAX_DEFICIT_PCT) / 100.0
        val rawTarget = tdee * (1.0 - deficit)
        val floor = safetyFloor(sex)
        val raised = rawTarget < floor
        val targetKcal = if (raised) floor else rawTarget.roundToInt()

        val proteinG = (PROTEIN_G_PER_KG * weightKg).roundToInt()
        val fatG = (targetKcal * FAT_KCAL_SHARE / KCAL_PER_G_FAT).roundToInt()
        val restKcal = targetKcal - proteinG * KCAL_PER_G_PROTEIN - fatG * KCAL_PER_G_FAT
        val carbsG = (restKcal.coerceAtLeast(0.0) / KCAL_PER_G_CARBS).roundToInt()

        return TdeeGoals(
            bmr = bmr,
            tdee = tdee,
            targets = DailyTargets(kcal = targetKcal, proteinG = proteinG, fatG = fatG, carbsG = carbsG),
            raisedToSafetyFloor = raised,
        )
    }
}
