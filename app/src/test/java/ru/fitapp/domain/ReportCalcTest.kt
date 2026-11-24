package ru.fitapp.domain

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportCalcTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val targets = DailyTargets(kcal = 2000, proteinG = 128, fatG = 59, carbsG = 229)

    private fun epochMs(hour: Int, minute: Int): Long =
        LocalDateTime.of(2025, 3, 10, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun meal(kcal: Double, hour: Int, minute: Int = 0) = ReportMeal(
        takenAtEpochMs = epochMs(hour, minute),
        items = listOf(
            ReportItem(kcal = kcal, proteinG = 20.0, fatG = 10.0, carbsG = 30.0, fiberG = 5.0, sugarG = 4.0, ultraProcessed = false),
        ),
    )

    // --- границы интервала энергии (§8.2) ---

    @Test
    fun `high below target is likely deficit`() {
        // сумма 1400 → интервал 980..1820; high < 2000
        val report = ReportCalc.computeDay(List(1) { meal(1400.0, 12) }, 0, targets, zone)
        assertEquals(EnergyVerdict.LIKELY_DEFICIT, report.energy.verdict)
        assertEquals(980.0, report.energy.interval.low, 1e-9)
        assertEquals(1820.0, report.energy.interval.high, 1e-9)
    }

    @Test
    fun `low above target is likely excess`() {
        // сумма 3000 → интервал 2100..3900; low > 2000
        val report = ReportCalc.computeDay(List(1) { meal(3000.0, 12) }, 0, targets, zone)
        assertEquals(EnergyVerdict.LIKELY_EXCESS, report.energy.verdict)
    }

    @Test
    fun `interval crossing target is within uncertainty`() {
        // сумма 1600 → 1120..2080; цель 2000 внутри
        val report = ReportCalc.computeDay(List(1) { meal(1600.0, 12) }, 0, targets, zone)
        assertEquals(EnergyVerdict.WITHIN_UNCERTAINTY, report.energy.verdict)
    }

    @Test
    fun `boundary high equals target is still uncertainty (strict less-than)`() {
        // сумма 1000, цель 1300 → high = 1300 == цель → НЕ «скорее дефицит»
        val t = targets.copy(kcal = 1300)
        val report = ReportCalc.computeDay(List(1) { meal(1000.0, 12) }, 0, t, zone)
        assertEquals(EnergyVerdict.WITHIN_UNCERTAINTY, report.energy.verdict)
    }

    @Test
    fun `boundary low equals target is still uncertainty (strict greater-than)`() {
        // сумма 2000, цель 1400 → low = 1400 == цель → НЕ «скорее перебор»
        val t = targets.copy(kcal = 1400)
        val report = ReportCalc.computeDay(List(1) { meal(2000.0, 12) }, 0, t, zone)
        assertEquals(EnergyVerdict.WITHIN_UNCERTAINTY, report.energy.verdict)
    }

    // --- нутриенты: вердикт только когда весь интервал по одну сторону ---

    @Test
    fun `protein below target when whole interval below`() {
        // 20 г белка в одном приёме → интервал 14..26, цель 128 → BELOW
        val report = ReportCalc.computeDay(List(1) { meal(500.0, 12) }, 0, targets, zone)
        assertEquals(NutrientVerdict.BELOW, report.protein.verdict)
    }

    @Test
    fun `nutrient undetermined when interval crosses target`() {
        // белок: 3 приёма по 20 г = 60 → интервал 42..78, цель 55 → не определить
        val t = targets.copy(proteinG = 55)
        val report = ReportCalc.computeDay(List(3) { meal(500.0, 12) }, 0, t, zone)
        assertEquals(NutrientVerdict.UNDETERMINED, report.protein.verdict)
    }

    @Test
    fun `nutrient above when whole interval above target`() {
        // жир: 3 приёма по 10 г = 30 → интервал 21..39, цель 15 → ABOVE
        val t = targets.copy(fatG = 15)
        val report = ReportCalc.computeDay(List(3) { meal(500.0, 12) }, 0, t, zone)
        assertEquals(NutrientVerdict.ABOVE, report.fat.verdict)
    }

    // --- статус «мало данных» (§8.3) ---

    @Test
    fun `little data when fewer than three done meals`() {
        val two = ReportCalc.computeDay(listOf(meal(500.0, 8), meal(600.0, 13)), 0, targets, zone)
        assertTrue(two.littleData)

        val three = ReportCalc.computeDay(
            listOf(meal(500.0, 8), meal(600.0, 13), meal(700.0, 19)),
            0,
            targets,
            zone,
        )
        assertFalse(three.littleData)
    }

    @Test
    fun `little data when any failed meal present`() {
        val report = ReportCalc.computeDay(
            listOf(meal(500.0, 8), meal(600.0, 13), meal(700.0, 19)),
            failedCount = 1,
            targets,
            zone,
        )
        assertTrue(report.littleData)
    }

    @Test
    fun `empty day has zero interval and little data`() {
        val report = ReportCalc.computeDay(emptyList(), 0, targets, zone)
        assertTrue(report.littleData)
        assertEquals(0.0, report.energy.interval.low, 1e-9)
        assertEquals(0.0, report.energy.interval.high, 1e-9)
        assertEquals(0, report.regimen.mealCount)
        assertNull(report.regimen.lastIntake)
        assertNull(report.regimen.longestGapMinutes)
    }

    // --- блок «Режим» (§8.3) ---

    @Test
    fun `regimen counts meals, last time, late meals and longest gap`() {
        val meals = listOf(
            meal(400.0, 8, 0),
            meal(600.0, 13, 30),
            meal(800.0, 22, 15),
        )
        val report = ReportCalc.computeDay(meals, 0, targets, zone)
        assertEquals(3, report.regimen.mealCount)
        assertEquals(22, report.regimen.lastIntake?.hour)
        assertEquals(15, report.regimen.lastIntake?.minute)
        assertEquals(1, report.regimen.lateMeals) // 22:15 ≥ 21:00
        // самый длинный промежуток: 13:30 → 22:15 = 525 мин
        assertEquals(525L, report.regimen.longestGapMinutes)
    }

    @Test
    fun `meals are sorted by time regardless of input order`() {
        val meals = listOf(meal(800.0, 22, 0), meal(400.0, 8, 0))
        val report = ReportCalc.computeDay(meals, 0, targets, zone)
        assertEquals(22, report.regimen.lastIntake?.hour)
    }

    // --- интервал ±30% ---

    @Test
    fun `interval is plus minus thirty percent of sum`() {
        val interval = ReportCalc.interval(1000.0)
        assertEquals(700.0, interval.low, 1e-9)
        assertEquals(1300.0, interval.high, 1e-9)
    }
}
