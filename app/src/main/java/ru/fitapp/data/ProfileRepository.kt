package ru.fitapp.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    suspend fun getProfile(): ProfileEntity? = profileDao.get()

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
    }

    /** Пересчитать цели из сохранённой анкеты (например, после изменения дефицита). */
    suspend fun recalculate(deficitPct: Int? = null): TdeeGoals? {
        val profile = profileDao.get() ?: return null
        val sex = runCatching { Sex.valueOf(profile.sex) }.getOrDefault(Sex.FEMALE)
        return Tdee.goals(
            sex = sex,
            weightKg = profile.weightKg,
            heightCm = profile.heightCm.toDouble(),
            ageYears = profile.age,
            activityFactor = profile.activity,
            deficitPct = deficitPct ?: profile.deficitPct,
        )
    }

    companion object {
        fun ProfileEntity.toTargets(): DailyTargets =
            DailyTargets(kcal = targetKcal, proteinG = targetProteinG, fatG = targetFatG, carbsG = targetCarbsG)

        /** Активность анкеты как enum, если значение совпадает с одним из коэффициентов. */
        fun ProfileEntity.activityEnum(): Activity? =
            Activity.entries.firstOrNull { kotlin.math.abs(it.factor - activity) < 1e-9 }
    }
}
