package ru.fitapp.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.fitapp.domain.DailyTargets
import ru.fitapp.domain.DayReport
import ru.fitapp.domain.EnergyBlock
import ru.fitapp.domain.EnergyVerdict
import ru.fitapp.domain.Interval
import ru.fitapp.domain.NutrientBlock
import ru.fitapp.domain.NutrientVerdict
import ru.fitapp.domain.CompositionBlock
import ru.fitapp.domain.RegimenBlock

class ReportTextSanitizeTest {

    private val sourceJson = """
        {"little_data":false,
         "energy":{"low_kcal":1540,"high_kcal":2860,"target_kcal":2200,"verdict":"в пределах неопределённости"},
         "protein":{"protein_low_g":56,"protein_high_g":104,"protein_target_g":128,"protein_verdict":"ниже цели"},
         "regimen":{"meals":4,"last_intake":"20:30","late_meals_after_21":0,"longest_gap_min":310}}
    """.trimIndent()

    @Test
    fun `text without new numbers passes`() {
        val text = "Энергия: день в пределах неопределённости, интервал от 1540 до 2860 ккал при цели 2200. " +
            "Белок ниже цели. Режим: 4 приёма, последний в 20:30."
        assertFalse(ReportTextSanitizer.containsNewNumbers(text, sourceJson))
        assertEquals(text, ReportTextSanitizer.sanitize(text, sourceJson, fallback = "FALLBACK"))
    }

    @Test
    fun `text with a new number is rejected and replaced by fallback`() {
        val text = "За день вы съели около 2500 ккал — это дефицит."
        assertTrue(ReportTextSanitizer.containsNewNumbers(text, sourceJson))
        assertEquals("FALLBACK", ReportTextSanitizer.sanitize(text, sourceJson, fallback = "FALLBACK"))
    }

    @Test
    fun `decimal comma variant of allowed number passes`() {
        // 1540,0 — то же число, что 1540 в JSON
        val text = "Нижняя граница 1540,0 ккал."
        assertFalse(ReportTextSanitizer.containsNewNumbers(text, sourceJson))
    }

    @Test
    fun `slightly different number is rejected`() {
        val text = "Примерно 1541 ккал внизу диапазона."
        assertTrue(ReportTextSanitizer.containsNewNumbers(text, sourceJson))
    }

    @Test
    fun `time components count as numbers from json`() {
        // 20:30 → числа 20 и 30; 20 есть в JSON? нет → должен отбраковываться,
        // если бы JSON не содержал last_intake "20:30". Проверяем обе стороны.
        assertFalse(ReportTextSanitizer.containsNewNumbers("Последний приём в 20:30.", sourceJson))
        assertTrue(ReportTextSanitizer.containsNewNumbers("Последний приём в 21:45.", sourceJson))
    }

    @Test
    fun `blank text falls back`() {
        assertEquals("FALLBACK", ReportTextSanitizer.sanitize("   ", sourceJson, fallback = "FALLBACK"))
    }

    @Test
    fun `fallback text is built from verdicts without llm`() {
        val report = DayReport(
            energy = EnergyBlock(Interval(1540.0, 2860.0), 2200, EnergyVerdict.WITHIN_UNCERTAINTY),
            protein = NutrientBlock(Interval(56.0, 104.0), 128, NutrientVerdict.BELOW),
            fat = NutrientBlock(Interval(40.0, 74.0), 59, NutrientVerdict.UNDETERMINED),
            carbs = NutrientBlock(Interval(150.0, 279.0), 229, NutrientVerdict.UNDETERMINED),
            composition = CompositionBlock(Interval(8.0, 15.0), Interval(20.0, 37.0), ultraProcessedItems = 2, totalItems = 9),
            regimen = RegimenBlock(mealCount = 4, lastIntake = java.time.LocalTime.of(20, 30), lateMeals = 0, longestGapMinutes = 310),
            littleData = true,
        )
        val generator = ReportTextGenerator(apiKeyProvider = { null })
        val fallback = generator.buildFallbackText(report)
        assertTrue(fallback.contains("в пределах неопределённости"))
        assertTrue(fallback.contains("Мало данных"))
        assertTrue(fallback.contains("20:30"))
        // fallback не содержит новых чисел относительно собственного JSON-входа
        val json = generator.buildInputJson(report)
        assertFalse(ReportTextSanitizer.containsNewNumbers(fallback, json))
        // текст перестал быть пересказом вердиктов: в нём есть разрыв с целью и действие
        assertTrue(fallback.contains("добавьте"))
        assertTrue(fallback.contains("при цели"))
    }
}
