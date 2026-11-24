package ru.fitapp.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import ru.fitapp.FitAppApplication

/**
 * Планировщик вечернего пуша (§3 PLAN): PeriodicWorkRequest 24 ч
 * с начальной задержкой до ближайшего pushHour (default 21:00).
 * UI дёргает reschedule() после изменения настроек и cancel() при выключении пуша.
 */
object EveningReportScheduler {

    private const val UNIQUE_WORK = "evening-report"

    /** Перезапланировать по текущим настройкам; если пуш выключен — отменить. */
    suspend fun reschedule(context: Context) {
        val app = context.applicationContext as? FitAppApplication ?: return
        val enabled = app.settingsStore.pushEnabled.first()
        if (!enabled) {
            cancel(context)
            return
        }
        val hour = app.settingsStore.pushHour.first()
        scheduleAt(context, hour)
    }

    /** Задержка до ближайшего указанного часа (сегодня или завтра). */
    internal fun initialDelayMillis(now: LocalDateTime, hour: Int): Long {
        var next = now.toLocalDate().atTime(hour.coerceIn(0, 23), 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }

    fun scheduleAt(context: Context, hour: Int) {
        val delay = initialDelayMillis(LocalDateTime.now(), hour)
        val request = PeriodicWorkRequestBuilder<EveningReportWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            // CANCEL_AND_REENQUEUE: сбрасывает начальную задержку при смене часа пуша.
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }
}
