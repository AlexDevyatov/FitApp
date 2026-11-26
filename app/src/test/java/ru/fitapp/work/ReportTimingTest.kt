package ru.fitapp.work

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Арифметика вечернего пуша: «не приходит в 21:00» и «приходит на следующий день»
 * проверяются здесь, без Android и WorkManager.
 */
class ReportTimingTest {

    private val now = LocalDateTime.of(2026, 11, 27, 18, 30)

    @Test
    fun `run today when hour is still ahead`() {
        assertEquals(LocalDateTime.of(2026, 11, 27, 21, 0), ReportTiming.nextRun(now, 21))
    }

    @Test
    fun `run tomorrow when hour has already passed`() {
        assertEquals(LocalDateTime.of(2026, 11, 28, 18, 0), ReportTiming.nextRun(now, 18))
    }

    @Test
    fun `exactly at the hour the next run is tomorrow`() {
        val at21 = LocalDateTime.of(2026, 11, 27, 21, 0)
        assertEquals(LocalDateTime.of(2026, 11, 28, 21, 0), ReportTiming.nextRun(at21, 21))
    }

    @Test
    fun `delay counts minutes until the next run`() {
        // 18:30 → 21:00 = 2 ч 30 мин
        assertEquals(150 * 60_000L, ReportTiming.delayMillis(now, 21))
    }

    @Test
    fun `hour out of range is clamped`() {
        assertEquals(LocalDateTime.of(2026, 11, 27, 23, 0), ReportTiming.nextRun(now, 42))
        assertEquals(LocalDateTime.of(2026, 11, 28, 0, 0), ReportTiming.nextRun(now, -5))
    }

    @Test
    fun `notification is shown within the lateness window`() {
        val scheduled = 1_000_000L
        assertTrue(ReportTiming.shouldNotify(scheduled + 5 * 60_000L, scheduled))
        assertTrue(ReportTiming.shouldNotify(scheduled + ReportTiming.NOTIFY_WINDOW_MINUTES * 60_000L, scheduled))
    }

    @Test
    fun `notification is skipped when the run is late`() {
        val scheduled = 1_000_000L
        assertFalse(ReportTiming.shouldNotify(scheduled + 4 * 60 * 60_000L, scheduled))
    }

    @Test
    fun `unknown schedule time still notifies`() {
        // Старые записи работы без inputData: лучше показать, чем промолчать.
        assertTrue(ReportTiming.shouldNotify(1_000_000L, 0L))
    }
}