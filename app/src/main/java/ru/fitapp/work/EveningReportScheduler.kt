package ru.fitapp.work

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import ru.fitapp.FitAppApplication
import ru.fitapp.core.LogTags

/**
 * Планировщик вечернего пуша (§3 PLAN): PeriodicWorkRequest 24 ч
 * с начальной задержкой до ближайшего pushHour (default 21:00).
 * UI дёргает reschedule() после изменения настроек и cancel() при выключении пуша.
 *
 * Логи (tag `FitAppPush`): включён ли пуш, на какой час и через сколько минут
 * произойдёт первый запуск. Это первое, что нужно при «пуш не пришёл».
 */
object EveningReportScheduler {

    private const val UNIQUE_WORK = "evening-report"
    private const val TAG = LogTags.PUSH

    /** Перезапланировать по текущим настройкам; если пуш выключен — отменить. */
    suspend fun reschedule(context: Context) {
        val app = context.applicationContext as? FitAppApplication
        if (app == null) {
            Log.e(TAG, "push: контекст не FitAppApplication — перепланирование пропущено")
            return
        }
        val enabled = app.settingsStore.pushEnabled.first()
        if (!enabled) {
            Log.i(TAG, "push: пуш выключен — периодическая работа отменяется")
            cancel(context)
            return
        }
        val hour = app.settingsStore.pushHour.first()
        Log.i(TAG, "push: перепланирование на $hour:00")
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
        Log.i(
            TAG,
            "push: PeriodicWork 24ч поставлен work_id=${request.id} " +
                "первый запуск через ${delay / 60_000} мин (hour=$hour)",
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        Log.i(TAG, "push: PeriodicWork отменён ($UNIQUE_WORK)")
    }
}