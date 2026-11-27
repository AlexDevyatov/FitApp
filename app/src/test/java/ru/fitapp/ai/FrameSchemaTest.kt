package ru.fitapp.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.fitapp.domain.AnalyzeOutcome
import ru.fitapp.domain.FrameKind

class FrameSchemaTest {

    private val validJson = """
        {
          "kind": "plate",
          "confidence": 0.87,
          "notes": "Вес гарнира оценён примерно",
          "items": [
            {
              "name": "Рис отварной",
              "grams": 180.0,
              "kcal": 210.0,
              "protein_g": 4.5,
              "fat_g": 0.6,
              "carbs_g": 45.0,
              "fiber_g": 1.2,
              "sugar_g": 0.1,
              "ultra_processed": false
            },
            {
              "name": "Соус packaged",
              "grams": null,
              "kcal": 90.0,
              "protein_g": 1.0,
              "fat_g": 7.0,
              "carbs_g": 5.0,
              "fiber_g": 0.0,
              "sugar_g": 4.0,
              "ultra_processed": true
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses valid response with all fields`() {
        val dto = frameJson.decodeFromString(FrameResponseDto.serializer(), validJson)
        assertEquals("plate", dto.kind)
        assertEquals(0.87, dto.confidence, 1e-9)
        assertEquals(2, dto.items.size)
        assertEquals("Рис отварной", dto.items[0].name)
        assertTrue(dto.items[1].ultraProcessed)
    }

    @Test
    fun `grams null is preserved`() {
        val dto = frameJson.decodeFromString(FrameResponseDto.serializer(), validJson)
        assertNull(dto.items[1].grams)
        val outcome = dto.toOutcome() as AnalyzeOutcome.Ok
        assertNull(outcome.result.items[1].grams)
    }

    @Test
    fun `maps plate kind to domain`() {
        val outcome = frameJson.decodeFromString(FrameResponseDto.serializer(), validJson).toOutcome()
        assertTrue(outcome is AnalyzeOutcome.Ok)
        assertEquals(FrameKind.PLATE, (outcome as AnalyzeOutcome.Ok).result.kind)
        assertEquals(0.87, outcome.result.confidence, 1e-9)
        assertEquals(FrameKind.PLATE, outcome.result.kind)
    }

    @Test
    fun `not_food maps to NotFood outcome`() {
        val json = """{"kind":"not_food","confidence":0.95,"notes":"На фото нет еды","items":[]}"""
        val outcome = frameJson.decodeFromString(FrameResponseDto.serializer(), json).toOutcome()
        assertTrue(outcome is AnalyzeOutcome.NotFood)
        assertEquals("На фото нет еды", (outcome as AnalyzeOutcome.NotFood).notes)
    }

    @Test
    fun `low confidence falls back to dish kind`() {
        // §3 PLAN: порог уверенности 0.5, ниже — тип «блюдо»
        val json = """{"kind":"label","confidence":0.4,"notes":"Не уверен","items":[]}"""
        val outcome = frameJson.decodeFromString(FrameResponseDto.serializer(), json).toOutcome() as AnalyzeOutcome.Ok
        assertEquals(FrameKind.DISH, outcome.result.kind)
    }

    @Test
    fun `confidence exactly at threshold keeps parsed kind`() {
        val json = """{"kind":"label","confidence":0.5,"notes":"Погранично","items":[]}"""
        val outcome = frameJson.decodeFromString(FrameResponseDto.serializer(), json).toOutcome() as AnalyzeOutcome.Ok
        assertEquals(FrameKind.LABEL, outcome.result.kind)
    }

    @Test
    fun `unknown fields like reasoning are ignored`() {
        val json = """
            {"kind":"dish","confidence":0.9,"notes":"ок","items":[],
             "reasoning":"много букв", "extra_field": 42}
        """.trimIndent()
        val dto = frameJson.decodeFromString(FrameResponseDto.serializer(), json)
        assertEquals("dish", dto.kind)
    }

    @Test
    fun `drink kind maps to drink`() {
        val json = """
            {"kind":"drink","confidence":0.88,"notes":"Сахар не учтён","items":[
              {"name":"Кофе чёрный","grams":200.0,"kcal":4.0,"protein_g":0.2,"fat_g":0.0,
               "carbs_g":0.6,"fiber_g":0.0,"sugar_g":0.0,"ultra_processed":false}]}
        """.trimIndent()
        val outcome = frameJson.decodeFromString(FrameResponseDto.serializer(), json).toOutcome()
            as AnalyzeOutcome.Ok
        assertEquals(FrameKind.DRINK, outcome.result.kind)
        assertEquals("Кофе чёрный", outcome.result.items.single().name)
    }

    @Test
    fun `low confidence drink falls back to dish`() {
        // §3 PLAN: при сомнении модель возвращает «блюдо», отметка сахара не появится.
        val json = """{"kind":"drink","confidence":0.3,"notes":"Не уверен","items":[]}"""
        val outcome = frameJson.decodeFromString(FrameResponseDto.serializer(), json).toOutcome()
            as AnalyzeOutcome.Ok
        assertEquals(FrameKind.DISH, outcome.result.kind)
    }

    @Test
    fun `strict schema advertises drink`() {
        assertTrue(FRAME_JSON_SCHEMA_STRICT.contains("\"drink\""))
        val parsed = frameJson.parseToJsonElement(FRAME_JSON_SCHEMA_STRICT)
        assertTrue(parsed is kotlinx.serialization.json.JsonObject)
    }

    @Test
    fun `strict schema constant matches plan required fields`() {
        // Схема из §4.3 — проверяем, что константа содержит ключевые элементы
        assertTrue(FRAME_JSON_SCHEMA_STRICT.contains("\"not_food\""))
        assertTrue(FRAME_JSON_SCHEMA_STRICT.contains("\"ultra_processed\""))
        assertTrue(FRAME_JSON_SCHEMA_STRICT.contains("\"additionalProperties\": false"))
        // схема — валидный JSON
        val parsed = frameJson.parseToJsonElement(FRAME_JSON_SCHEMA_STRICT)
        assertTrue(parsed is kotlinx.serialization.json.JsonObject)
    }
}
