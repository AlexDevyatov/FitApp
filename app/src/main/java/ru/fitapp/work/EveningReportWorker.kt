package ru.fitapp.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.first
import ru.fitapp.FitAppApplication
import ru.fitapp.R
import ru.fitapp.core.LogTags
import ru.fitapp.data.db.MealStatus
import ru.fitapp.data.db.MealWithItems
import ru.fitapp.data.prefs.SettingsStore
import ru.fitapp.domain.AdviceTone
import ru.fitapp.domain.DayReport
import ru.fitapp.domain.NutritionAdvice
import ru.fitapp.domain.ReportCalc
import ru.fitapp.domain.ReportItem
import ru.fitapp.domain.ReportMeal

/**
 * Вечерний отчёт (§3, §10 PLAN): одноразовый запуск, поставленный на 21:00.
 *
 * Порядок:
 * 1. пуш выключен — выходим и НЕ ставим следующий запуск;
 * 2. сразу ставим следующий запуск на завтра: цепочка не должна обрываться,
 *    даже если сегодня показывать нечего или запуск опоздал;
 * 3. опоздали больше окна (телефон был выключен) — уведомление пропускаем;
 * 4. нет карточек или ни одной DONE — уведомление не нужно;
 * 5. показываем уведомление с числами дня и главным советом.
 *
 * Логи (tag `FitAppPush`): почему уведомление показано или пропущено — пуш выключен,
 * карточек нет, нет DONE-карточек, запуск опоздал.
 */
class EveningReportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? FitAppApplication
        if (app == null) {
            Log.e(TAG, "evening: applicationContext не FitAppApplication — отчёт не проверен")
            return Result.failure()
        }

        val hour = inputData.getInt(EveningReportScheduler.KEY_HOUR, SettingsStore.DEFAULT_PUSH_HOUR)
        val scheduledAt = inputData.getLong(EveningReportScheduler.KEY_SCHEDULED_AT, 0L)

        if (!app.settingsStore.pushEnabled.first()) {
            Log.i(TAG, "evening: пуш выключен в настройках — уведомление не показываем, следующий запуск не ставим")
            return Result.success()
        }

        // Цепочка важнее одного показа: иначе после пропуска пуш замолкает навсегда.
        runCatching { EveningReportScheduler.scheduleNext(applicationContext, hour) }
            .onFailure { Log.e(TAG, "evening: следующий запуск не поставлен — пуш может замолчать", it) }

        val now = System.currentTimeMillis()
        if (!ReportTiming.shouldNotify(now, scheduledAt)) {
            Log.w(
                TAG,
                "evening: запуск опоздал на ${(now - scheduledAt) / 60_000} мин " +
                    "(окно ${ReportTiming.NOTIFY_WINDOW_MINUTES} мин) — уведомление пропущено, " +
                    "следующий запуск уже поставлен",
            )
            return Result.success()
        }

        val today = LocalDate.now().toString()
        val meals: List<MealWithItems> = app.database.mealDao().observeDayWithItems(today).first()
        Log.i(TAG, "evening: карточек за $today — ${meals.size}")
        if (meals.isEmpty()) {
            Log.i(TAG, "evening: за день нет карточек — уведомление не нужно")
            return Result.success()
        }

        val done = meals.filter { it.meal.status == MealStatus.DONE }
        if (done.isEmpty()) {
            Log.i(
                TAG,
                "evening: нет DONE-карточек, статусы=${meals.groupingBy { it.meal.status }.eachCount()} — " +
                    "уведомление не нужно",
            )
            return Result.success()
        }

        val content = buildContent(app, meals)
        Log.i(
            TAG,
            "evening: отчёт готов, DONE-карточек ${done.size}, " +
                "summary=${content.summary ?: "нет"} — показываем уведомление",
        )
        ReportNotifier.notifyReportReady(
            context = applicationContext,
            summary = content.summary,
            bigText = content.bigText,
        )
        return Result.success()
    }

    /** Числа дня и главный совет для текста уведомления; без целей — только факт готовности. */
    private suspend fun buildContent(app: FitAppApplication, meals: List<MealWithItems>): NotificationContent {
        val targets = app.profileRepository.targets.first() ?: return NotificationContent(null, null)
        val report = ReportCalc.computeDay(
            doneMeals = DayReportData.toReportMeals(meals),
            failedCount = DayReportData.failedCount(meals),
            targets = targets,
        )
        return contentOf(report)
    }

    private fun contentOf(report: DayReport): NotificationContent {
        val advice = NutritionAdvice.build(report)
        val headline = advice.firstOrNull { it.tone == AdviceTone.WARN }?.title
            ?: advice.firstOrNull()?.title
        val summary = if (headline != null) {
            applicationContext.getString(
                R.string.report_notification_summary,
                Math.round(report.energy.interval.low),
                Math.round(report.energy.interval.high),
                headline.lowercase(Locale.forLanguageTag("ru")),
            )
        } else {
            null
        }
        val bigText = buildString {
            if (summary != null) {
                append(summary).append(". ")
            }
            append(advice.take(2).joinToString(" ") { it.detail })
        }.trim().ifBlank { null }
        return NotificationContent(summary, bigText)
    }

    private data class NotificationContent(val summary: String?, val bigText: String?)

    private companion object {
        const val TAG = LogTags.PUSH
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
