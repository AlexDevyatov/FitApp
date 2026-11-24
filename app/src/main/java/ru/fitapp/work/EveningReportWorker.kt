package ru.fitapp.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import ru.fitapp.FitAppApplication
import ru.fitapp.data.db.MealStatus
import ru.fitapp.data.db.MealWithItems
import ru.fitapp.domain.ReportItem
import ru.fitapp.domain.ReportMeal

/**
 * Вечерний отчёт (§3, §10 PLAN): периодический воркер (24 ч),
 * показывает уведомление «Отчёт за день готов», если за день есть карточки.
 */
class EveningReportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? FitAppApplication ?: return Result.failure()
        if (!app.settingsStore.pushEnabled.first()) return Result.success()

        val today = LocalDate.now().toString()
        val meals: List<MealWithItems> = app.database.mealDao().observeDayWithItems(today).first()
        if (meals.isEmpty()) return Result.success()

        val done = meals.filter { it.meal.status == MealStatus.DONE }
        if (done.isEmpty()) return Result.success()

        ReportNotifier.notifyReportReady(applicationContext)
        return Result.success()
    }
}

/** Отчётные данные дня в доменных типах (используется UI-экраном отчёта). */
object DayReportData {
    fun toReportMeals(meals: List<MealWithItems>): List<ReportMeal> =
        meals.filter { it.meal.status == MealStatus.DONE }
            .map { mealWithItems ->
                ReportMeal(
                    takenAtEpochMs = mealWithItems.meal.takenAt,
                    items = mealWithItems.items.map { item ->
                        ReportItem(
                            kcal = item.kcal,
                            proteinG = item.proteinG,
                            fatG = item.fatG,
                            carbsG = item.carbsG,
                            fiberG = item.fiberG,
                            sugarG = item.sugarG,
                            ultraProcessed = item.ultraProcessed == 1,
                        )
                    },
                )
            }

    fun failedCount(meals: List<MealWithItems>): Int =
        meals.count { it.meal.status == MealStatus.FAILED }
}
