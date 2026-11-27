package ru.fitapp.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Текстовый запрос пересчёта КБЖУ: промпт обязан нести новое название и массу,
 * иначе модель оценит не то блюдо или не ту порцию.
 */
class AnalyzePromptsTest {

    @Test
    fun `estimate prompt carries new name and mass`() {
        val prompt = AnalyzePrompts.estimate("Куриная грудка с рисом", 150.0)
        assertTrue(prompt.contains("Куриная грудка с рисом"))
        assertTrue(prompt.contains("150 г"))
        assertTrue(prompt.contains("без фотографии"))
    }

    @Test
    fun `estimate prompt without mass asks to estimate portion`() {
        val prompt = AnalyzePrompts.estimate("Творог", null)
        assertTrue(prompt.contains("Творог"))
        assertTrue(prompt.contains("Масса порции неизвестна"))
        assertFalse(prompt.contains("150 г"))
    }

    @Test
    fun `estimate prompt keeps fractional mass with comma`() {
        val prompt = AnalyzePrompts.estimate("Овсянка", 250.5)
        assertTrue(prompt.contains("250,5 г"))
    }

    @Test
    fun `name is flattened to one line and stripped of guillemets`() {
        val prompt = AnalyzePrompts.estimate("Кофе\nс молоком\r\n«латте»", 200.0)
        assertFalse(prompt.contains("\n"))
        assertFalse(prompt.contains("\r"))
        // Кавычки из названия заменены на обычные, чтобы не ломать обрамление в промпте.
        assertTrue(prompt.contains("\"латте\""))
        assertTrue(prompt.contains("Кофе с молоком"))
    }

    @Test
    fun `too long name is truncated`() {
        val longName = "Я".repeat(AnalyzePrompts.MAX_NAME_CHARS + 50)
        val prompt = AnalyzePrompts.estimate(longName, 100.0)
        assertTrue(prompt.contains("Я".repeat(AnalyzePrompts.MAX_NAME_CHARS)))
        assertFalse(prompt.contains("Я".repeat(AnalyzePrompts.MAX_NAME_CHARS + 1)))
    }

    @Test
    fun `system prompt knows drinks and added sugar`() {
        assertTrue(AnalyzePrompts.SYSTEM_PROMPT.contains("`drink`"))
        assertTrue(AnalyzePrompts.SYSTEM_PROMPT.contains("сахар"))
    }

    @Test
    fun `sanitized name equals trimmed input`() {
        assertEquals("Гречка", AnalyzePrompts.sanitizeName("  Гречка  "))
    }
}