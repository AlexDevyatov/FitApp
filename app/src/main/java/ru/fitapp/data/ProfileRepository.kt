package ru.fitapp.data

import android.util.Log
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.fitapp.core.LogTags
import ru.fitapp.data.db.ProfileDao
import ru.fitapp.data.db.ProfileEntity
import ru.fitapp.domain.Activity
import ru.fitapp.domain.DailyTargets
import ru.fitapp.domain.Sex
import ru.fitapp.domain.Tdee
import ru.fitapp.domain.TdeeGoals

/** Тонкий слой над DAO анкеты: сохраняет анкету и рассчитанные цели (§8.1 PLAN). */
class ProfileRepository(private val profileDao: ProfileDao) {

    fun observeProfile(): Flow<ProfileEntity?> = profileDao.observe()

    /** Цели текущей анкеты — для расчёта отчёта. */
    val targets: Flow<DailyTargets?> = observeProfile().map { it?.toTargets() }

    suspend fun getProfile(): ProfileEntity? {
        val profile = profileDao.get()
        if (profile == null) Log.i(TAG, "profile: анкеты ещё нет в базе")
        return profile
    }

    suspend fun saveGoals(goals: TdeeGoals, sex: Sex, age: Int, heightCm: Int, weightKg: Double, activity: Double, deficitPct: Int) {
        profileDao.upsert(
            ProfileEntity(
                id = ProfileEntity.SINGLETON_ID,
                sex = sex.name,
                age = age,
                heightCm = heightCm,
                weightKg = weightKg,
                activity = activity,
                deficitPct = deficitPct,
                targetKcal = goals.targets.kcal,
                targetProteinG = goals.targets.proteinG,
                targetFatG = goals.targets.fatG,
                targetCarbsG = goals.targets.carbsG,
            )
        )
        // Вход и выход расчёта в одной строке: по ним видно, откуда взялась неверная цель.
        Log.i(
            TAG,
            "profile: анкета сохранена sex=${sex.name} age=$age height_cm=$heightCm " +
                "weight_kg=$weightKg activity=$activity deficit_pct=$deficitPct bmr=${goals.bmr.roundToInt()} " +
                "tdee=${goals.tdee.roundToInt()} -> kcal=${goals.targets.kcal} " +
                "protein=${goals.targets.proteinG} fat=${goals.targets.fatG} carbs=${goals.targets.carbsG} " +
                "floor_raised=${goals.raisedToSafetyFloor}",
        )
    }

    /** Пересчитать цели из сохранённой анкеты (например, после изменения дефицита). */
    suspend fun recalculate(deficitPct: Int? = null): TdeeGoals? {
        val profile = profileDao.get()
        if (profile == null) {
            Log.w(TAG, "profile: пересчёт цели невозможен — анкеты нет")
            return null
        }
        val sex = runCatching { Sex.valueOf(profile.sex) }.getOrElse {
            Log.w(TAG, "profile: пол в базе не распознан (${profile.sex}), используется FEMALE")
            Sex.FEMALE
        }
        val goals = Tdee.goals(
            sex = sex,
            weightKg = profile.weightKg,
            heightCm = profile.heightCm.toDouble(),
            ageYears = profile.age,
            activityFactor = profile.activity,
            deficitPct = deficitPct ?: profile.deficitPct,
        )
        Log.i(
            TAG,
            "profile: цель пересчитана deficit_pct=${deficitPct ?: profile.deficitPct} " +
                "-> kcal=${goals.targets.kcal} floor_raised=${goals.raisedToSafetyFloor}",
        )
        return goals
    }

    companion object {
        private const val TAG = LogTags.DATA

        fun ProfileEntity.toTargets(): DailyTargets =
            DailyTargets(kcal = targetKcal, proteinG = targetProteinG, fatG = targetFatG, carbsG = targetCarbsG)

        /** Активность анкеты как enum, если значение совпадает с одним из коэффициентов. */
        fun ProfileEntity.activityEnum(): Activity? =
            Activity.entries.firstOrNull { kotlin.math.abs(it.factor - activity) < 1e-9 }
    }
}
