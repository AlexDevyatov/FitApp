package ru.fitapp.work

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.common.util.concurrent.ListenableFuture
import java.time.LocalDateTime
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.fitapp.FitAppApplication
import ru.fitapp.core.LogTags

/**
 * Планировщик вечернего пуша (§3, §10 PLAN).
 *
 * Почему не PeriodicWorkRequest: периодическая работа дрейфует (flex-окно, Doze),
 * а после перезагрузки или смены часа первый запуск съезжает. Здесь — цепочка
 * одноразовых запусков: воркер, отработав, ставит следующий на 21:00 следующих суток.
 * Имя работы содержит дату, поэтому текущий запуск не блокирует постановку следующего.
 *
 * Точки входа:
 * - [ensureScheduled] — при старте приложения: работа есть всегда, даже если пользователь
 *   ни разу не открывал настройки (это и была причина «пуш не приходит»);
 * - [reschedule] — смена часа или включение пуша в настройках;
 * - [scheduleNext] — из воркера после срабатывания;
 * - [cancel] — выключение пуша.
 *
 * Логи (tag `FitAppPush`): на когда поставлен запуск и почему он поставлен.
 */
object EveningReportScheduler {

    private const val TAG = LogTags.PUSH

    /** Общий тег всех запусков: по нему пушатся и отменяются все ожидающие работы. */
    private const val WORK_TAG = "evening-report"

    internal const val KEY_HOUR = "hour"
    internal const val KEY_SCHEDULED_AT = "scheduled_at"

    /** Гарантировать наличие запланированного запуска, не сдвигая уже поставленный. */
    suspend fun ensureScheduled(context: Context) {
        val app = context.applicationContext as? FitAppApplication
        if (app == null) {
            Log.e(TAG, "push: контекст не FitAppApplication — автопланирование пропущено")
            return
        }
        if (!app.settingsStore.pushEnabled.first()) {
            Log.i(TAG, "push: пуш выключен — автопланирование не нужно")
            return
        }
        val hour = app.settingsStore.pushHour.first()
        enqueue(context, hour, reason = "старт приложения")
    }

    /** Перезапланировать по текущим настройкам; если пуш выключен — отменить всё. */
    suspend fun reschedule(context: Context) {
        val app = context.applicationContext as? FitAppApplication
        if (app == null) {
            Log.e(TAG, "push: контекст не FitAppApplication — перепланирование пропущено")
            return
        }
        val enabled = app.settingsStore.pushEnabled.first()
        if (!enabled) {
            Log.i(TAG, "push: пуш выключен — ожидающие запуски отменяются")
            cancel(context)
            return
        }
        val hour = app.settingsStore.pushHour.first()
        // Смена часа: старую цепочку убираем целиком, иначе запуск останется на прежнее время.
        cancel(context)
        enqueue(context, hour, reason = "настройки изменены")
    }

    /** Следующий запуск из воркера. */
    suspend fun scheduleNext(context: Context, hour: Int) {
        enqueue(context, hour, reason = "следующий день")
    }

    private suspend fun enqueue(context: Context, hour: Int, reason: String) {
        val now = LocalDateTime.now()
        val delay = ReportTiming.delayMillis(now, hour)
        val scheduledAt = System.currentTimeMillis() + delay
        val name = uniqueName(now, hour)
        val workManager = WorkManager.getInstance(context)

        // KEEP молча игнорирует новый запрос, если такой запуск уже стоит.
        // Тогда честнее сказать «уже стоит», чем печатать work_id, которого нет в очереди.
        val pending = workManager.getWorkInfosForUniqueWork(name).awaitWork()
            .firstOrNull { !it.state.isFinished }
        if (pending != null) {
            Log.i(
                TAG,
                "push: запуск $name уже стоит (state=${pending.state}) — дубликат не ставим " +
                    "(hour=$hour, причина: $reason)",
            )
            return
        }

        val request = OneTimeWorkRequestBuilder<EveningReportWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    KEY_HOUR to hour,
                    KEY_SCHEDULED_AT to scheduledAt,
                ),
            )
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
        Log.i(
            TAG,
            "push: поставлен запуск $name на ${ReportTiming.nextRun(now, hour)} " +
                "через ${delay / 60_000} мин (hour=$hour, причина: $reason) work_id=${request.id}",
        )
    }

    private fun uniqueName(now: LocalDateTime, hour: Int): String =
        "$WORK_TAG-${ReportTiming.nextRun(now, hour).toLocalDate()}"

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        Log.i(TAG, "push: все запуски вечернего отчёта отменены ($WORK_TAG)")
    }

    /**
     * Попытка `ListenableFuture.await()` из work-runtime-ktx разрешается в `Operation.await`
     * и возвращает не список, поэтому ждём результат колбэком: без блокировки потока
     * и без риска заклинить внутренний исполнитель WorkManager.
     */
    private suspend fun <T> ListenableFuture<T>.awaitWork(): T =
        suspendCancellableCoroutine { continuation ->
            addListener(
                {
                    try {
                        continuation.resume(get())
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                },
                Executor { it.run() },
            )
        }
}
