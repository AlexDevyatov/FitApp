package ru.fitapp.work

import java.time.Duration
import java.time.LocalDateTime

/**
 * Чистая арифметика вечернего пуша: когда следующий запуск и не опоздал ли текущий.
 *
 * Вынесено из воркера и планировщика, чтобы проверяться JVM-тестами без Android:
 * именно здесь живут ошибки «пуш пришёл не в 21:00» и «пуш пришёл на следующий день».
 */
object ReportTiming {

    /**
     * Допустимое опоздание. WorkManager и Doze могут сдвинуть запуск на часы;
     * уведомление «отчёт за день» после полуночи уже бессмысленно.
     */
    const val NOTIFY_WINDOW_MINUTES = 180L

    /** Ближайший запуск в указанный час: сегодня, если час ещё не прошёл, иначе завтра. */
    fun nextRun(now: LocalDateTime, hour: Int): LocalDateTime {
        val next = now.toLocalDate().atTime(hour.coerceIn(0, 23), 0)
        return if (next.isAfter(now)) next else next.plusDays(1)
    }

    fun delayMillis(now: LocalDateTime, hour: Int): Long =
        Duration.between(now, nextRun(now, hour)).toMillis()

    /**
     * Показывать ли уведомление: запуск в пределах окна или время запуска неизвестно
     * (старые записи работы без inputData).
     */
    fun shouldNotify(
        nowMs: Long,
        scheduledAtMs: Long,
        windowMinutes: Long = NOTIFY_WINDOW_MINUTES,
    ): Boolean = scheduledAtMs <= 0L || nowMs - scheduledAtMs <= windowMinutes * 60_000L
}
