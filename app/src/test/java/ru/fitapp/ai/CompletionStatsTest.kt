package ru.fitapp.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор usage/finish_reason — то, на чём строится диагностика инцидентов.
 * Тело ответа ниже повторяет реальный ответ qwen4-max на кадр с куском пиццы,
 * оборванный по max_tokens при старом лимите 600.
 */
class CompletionStatsTest {

    private fun statsOf(json: String): CompletionStats {
        val root = Json.parseToJsonElement(json).jsonObject
        val choice = root["choices"]!!.jsonArray.first().jsonObject
        val message = choice["message"]?.jsonObject
        return readCompletionStats(root, choice, message)
    }

    @Test
    fun `length response is reported as truncated with reasoning tokens`() {
        val reasoning = "The image shows a slice of pizza held in hand. Kind: dish."
        val content = "{\"kind\": \"dish\", \"confidence\": 0.6, \"items\": [{\"name\": \"Тесто"

        val stats = statsOf(
            """
            {
              "choices": [{
                "finish_reason": "length",
                "message": {
                  "role": "assistant",
                  "reasoning_content": ${JsonPrimitive(reasoning)},
                  "content": ${JsonPrimitive(content)}
                }
              }],
              "usage": {
                "prompt_tokens": 1229,
                "completion_tokens": 600,
                "prompt_tokens_details": { "image_tokens": 770 },
                "completion_tokens_details": { "reasoning_tokens": 426 }
              }
            }
            """.trimIndent(),
        )

        assertEquals("length", stats.finishReason)
        assertTrue(stats.truncated)
        assertEquals(1229, stats.promptTokens)
        assertEquals(600, stats.completionTokens)
        assertEquals(426, stats.reasoningTokens)
        assertEquals(770, stats.imageTokens)
        assertEquals(content.length, stats.answerChars)
        assertEquals(reasoning.length, stats.reasoningChars)
    }

    @Test
    fun `stop response without usage details is not truncated`() {
        val stats = statsOf(
            """
            {
              "choices": [{
                "finish_reason": "stop",
                "message": { "role": "assistant", "content": "{\"kind\":\"dish\"}" }
              }],
              "usage": { "prompt_tokens": 100, "completion_tokens": 20 }
            }
            """.trimIndent(),
        )

        assertEquals("stop", stats.finishReason)
        assertFalse(stats.truncated)
        assertEquals(20, stats.completionTokens)
        assertNull(stats.reasoningTokens)
        assertNull(stats.imageTokens)
        assertEquals(0, stats.reasoningChars)
    }
}