package ru.fitapp.domain

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Советы по питанию: проверяем, что они не пересказывают вердикты, а переводят
 * разрыв с целью в конкретные продукты и порции.
 */
class NutritionAdviceTest {

    private val targets = DailyTargets(kcal = 2000, proteinG = 128, fatG = 59, carbsG = 229)

    private fun epochMs(hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2025, 3, 10, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Suppress("LongParameterList")
    private fun report(
        perMealKcal: Double = 500.0,
        perMealProtein: Double = 32.0,
        perMealFat: Double = 15.0,
        perMealCarbs: Double = 55.0,
        perMealFiber: Double = 7.0,
        perMealSugar: Double = 3.0,
        ultra: Boolean = false,
        hours: List<Int> = listOf(8, 12, 16, 20),
        history: DayHistory = DayHistory(),
        failed: Int = 0,
    ): DayReport {
        val meals = hours.map { hour ->
            ReportMeal(
                takenAtEpochMs = epochMs(hour),
                items = listOf(
                    ReportItem(
                        kcal = perMealKcal,
                        proteinG = perMealProtein,
                        fatG = perMealFat,
                        carbsG = perMealCarbs,
                        fiberG = perMealFiber,
                        sugarG = perMealSugar,
                        ultraProcessed = ultra,
                    ),
                ),
            )
        }
        return ReportCalc.computeDay(
            doneMeals = meals,
            failedCount = failed,
            targets = targets,
            zone = ZoneOffset.UTC,
            history = history,
        )
    }

    @Test
    fun `empty day has no advice`() {
        assertTrue(NutritionAdvice.build(report(hours = emptyList())).isEmpty())
    }

    @Test
    fun `protein gap is translated into portions of food`() {
        // 3 приёма по 20 г белка = 60 г при цели 128 → разрыв 68 г
        val advice = NutritionAdvice.build(report(perMealProtein = 20.0, hours = listOf(8, 13, 19)))
        val protein = advice.first { it.id == "protein_low" }
        assertEquals(AdviceTone.WARN, protein.tone)
        assertTrue(protein.detail.contains("≈70 г белка"))
        assertTrue(protein.detail.contains("куриной грудки"))
        assertTrue(protein.detail.contains("творога"))
    }

    @Test
    fun `energy deficit suggests a snack with calories`() {
        // 3 приёма по 400 ккал = 1200, интервал 840..1560 < 2000 → дефицит
        val advice = NutritionAdvice.build(report(perMealKcal = 400.0, hours = listOf(8, 13, 19)))
        val energy = advice.first { it.id == "energy_low" }
        assertTrue(energy.detail.contains("≈800 ккал"))
        assertTrue(energy.detail.contains("≈450 ккал"))
    }

    @Test
    fun `energy excess tells what to remove`() {
        // 3 приёма по 1000 ккал = 3000, интервал 2100..3900 > 2000 → перебор
        val advice = NutritionAdvice.build(
            report(perMealKcal = 1000.0, perMealProtein = 40.0, hours = listOf(8, 13, 19)),
        )
        val energy = advice.first { it.id == "energy_high" }
        assertTrue(energy.detail.contains("≈1000 ккал"))
        assertTrue(energy.detail.contains("десерт"))
    }

    @Test
    fun `fiber gap is translated into vegetables and legumes`() {
        val advice = NutritionAdvice.build(report(perMealFiber = 5.0, hours = listOf(8, 13, 19)))
        val fiber = advice.first { it.id == "fiber_low" }
        assertTrue(fiber.detail.contains("клетчатки"))
        assertTrue(fiber.detail.contains("овощей"))
        assertTrue(fiber.detail.contains("чечевицы"))
    }

    @Test
    fun `sugar above limit counts teaspoons`() {
        // 3 приёма по 40 г сахара = 120 г, интервал 84..156 при лимите 50 → выше
        val advice = NutritionAdvice.build(
            report(perMealSugar = 40.0, hours = listOf(8, 13, 19)),
        )
        val sugar = advice.first { it.id == "sugar_high" }
        assertTrue(sugar.detail.contains("ч. л. сахара"))
    }

    @Test
    fun `late meal advice names the last intake and plural form`() {
        val advice = NutritionAdvice.build(report(hours = listOf(8, 13, 22)))
        val late = advice.first { it.id == "late_meal" }
        assertTrue(late.detail.contains("1 приём после 21:00"))
        assertTrue(late.detail.contains("22:00"))
    }

    @Test
    fun `ultra processed advice counts items of total`() {
        val advice = NutritionAdvice.build(
            report(ultra = true, hours = listOf(8, 13, 19)),
        )
        val ultra = advice.first { it.id == "ultra_processed" }
        assertTrue(ultra.detail.contains("3 из 3 компонентов"))
    }

    @Test
    fun `week average is compared with the target`() {
        val advice = NutritionAdvice.build(
            report(history = DayHistory(avgKcal = 1800, daysWithData = 5, prevDayKcal = 1700)),
        )
        val week = advice.first { it.id == "week" }
        assertTrue(week.detail.contains("≈1800 ккал"))
        assertTrue(week.detail.contains("ниже на ≈200 ккал в день"))
    }

    @Test
    fun `balanced day gets one positive advice instead of warnings`() {
        val advice = NutritionAdvice.build(report())
        assertEquals(1, advice.size)
        assertEquals("day_ok", advice.single().id)
        assertEquals(AdviceTone.GOOD, advice.single().tone)
        assertTrue(advice.single().detail.contains("2000"))
    }

    @Test
    fun `advice list is capped and ordered by importance`() {
        // Всё сломано сразу: мало белка, перебор калорий, мало клетчатки, сахар, ультрапереработка.
        val advice = NutritionAdvice.build(
            report(
                perMealKcal = 1000.0,
                perMealProtein = 20.0,
                perMealFiber = 1.0,
                perMealSugar = 40.0,
                ultra = true,
                hours = listOf(8, 13, 22),
            ),
        )
        assertEquals(NutritionAdvice.MAX_ITEMS, advice.size)
        assertEquals(
            listOf("protein_low", "energy_high", "fiber_low", "sugar_high", "ultra_processed"),
            advice.map { it.id },
        )
    }

    @Test
    fun `protein is not praised when the verdict is undetermined`() {
        // 100 г при цели 128 → интервал 70..130 накрывает цель: хвалить не за что
        val advice = NutritionAdvice.build(report(perMealProtein = 25.0))
        assertFalse(advice.any { it.id == "protein_ok" })
    }

    @Test
    fun `protein is praised only when the whole interval is above target`() {
        // 4 × 80 г = 320 г белка, интервал 224..416 > 128
        val advice = NutritionAdvice.build(
            report(perMealProtein = 80.0, perMealFiber = 1.0),
        )
        val ok = advice.first { it.id == "protein_ok" }
        assertEquals(AdviceTone.GOOD, ok.tone)
        assertTrue(ok.title.contains("Белка достаточно"))
    }

    @Test
    fun `little data day still gives actionable advice but no data instead of silence`() {
        val advice = NutritionAdvice.build(
            report(perMealProtein = 10.0, hours = listOf(13), failed = 1),
        )
        assertTrue(advice.any { it.id == "protein_low" })
        // отдельного «мало данных» в советах нет: за это отвечает баннер на экране
        assertFalse(advice.any { it.id == "little_data" })
    }
}
