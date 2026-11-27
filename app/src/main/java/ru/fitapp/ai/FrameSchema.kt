package ru.fitapp.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.fitapp.domain.AnalyzeOutcome
import ru.fitapp.domain.AnalysisResult
import ru.fitapp.domain.AnalyzedItem
import ru.fitapp.domain.FrameKind

/** JSON-парсер ответа провайдера: неизвестные поля (reasoning и пр.) игнорируются. */
val frameJson = Json {
    ignoreUnknownKeys = true
    isLenient = false
    coerceInputValues = true
}

/** Компонент разбора по схеме §4.3 PLAN. */
@Serializable
data class FrameItemDto(
    @SerialName("name") val name: String,
    @SerialName("grams") val grams: Double? = null,
    @SerialName("kcal") val kcal: Double,
    @SerialName("protein_g") val proteinG: Double,
    @SerialName("fat_g") val fatG: Double,
    @SerialName("carbs_g") val carbsG: Double,
    @SerialName("fiber_g") val fiberG: Double,
    @SerialName("sugar_g") val sugarG: Double,
    @SerialName("ultra_processed") val ultraProcessed: Boolean,
)

/** Ответ модели по схеме §4.3 PLAN. kind включает "not_food". */
@Serializable
data class FrameResponseDto(
    @SerialName("kind") val kind: String,
    @SerialName("confidence") val confidence: Double,
    @SerialName("notes") val notes: String,
    @SerialName("items") val items: List<FrameItemDto>,
)

/** Схема response_format json_schema (strict) — §4.3 PLAN, один в один + `drink`. */
const val FRAME_JSON_SCHEMA_STRICT: String = """
{
  "type": "object",
  "additionalProperties": false,
  "required": ["kind", "items", "confidence", "notes"],
  "properties": {
    "kind": { "type": "string", "enum": ["dish", "plate", "label", "drink", "not_food"] },
    "confidence": { "type": "number" },
    "notes": { "type": "string" },
    "items": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["name", "grams", "kcal", "protein_g", "fat_g", "carbs_g", "fiber_g", "sugar_g", "ultra_processed"],
        "properties": {
          "name": { "type": "string" },
          "grams": { "type": ["number", "null"] },
          "kcal": { "type": "number" },
          "protein_g": { "type": "number" },
          "fat_g": { "type": "number" },
          "carbs_g": { "type": "number" },
          "fiber_g": { "type": "number" },
          "sugar_g": { "type": "number" },
          "ultra_processed": { "type": "boolean" }
        }
      }
    }
  }
}
"""

/** Порог уверенности классификации (§3 PLAN): ниже — тип «блюдо». */
const val KIND_CONFIDENCE_THRESHOLD = 0.5

fun FrameItemDto.toDomain(): AnalyzedItem = AnalyzedItem(
    name = name,
    grams = grams,
    kcal = kcal,
    proteinG = proteinG,
    fatG = fatG,
    carbsG = carbsG,
    fiberG = fiberG,
    sugarG = sugarG,
    ultraProcessed = ultraProcessed,
)

/**
 * Маппинг DTO в доменный исход:
 * - kind == "not_food" → AnalyzeOutcome.NotFood;
 * - неизвестный kind или confidence < 0.5 → FrameKind.DISH (§2, решение 13);
 * - иначе Ok(AnalysisResult).
 */
fun FrameResponseDto.toOutcome(): AnalyzeOutcome {
    if (kind == KIND_NOT_FOOD) return AnalyzeOutcome.NotFood(notes)
    val parsedKind = when (kind) {
        KIND_DISH -> FrameKind.DISH
        KIND_PLATE -> FrameKind.PLATE
        KIND_LABEL -> FrameKind.LABEL
        KIND_DRINK -> FrameKind.DRINK
        else -> FrameKind.DISH
    }
    val effectiveKind = if (confidence < KIND_CONFIDENCE_THRESHOLD) FrameKind.DISH else parsedKind
    return AnalyzeOutcome.Ok(
        result = AnalysisResult(
            kind = effectiveKind,
            items = items.map { it.toDomain() },
            confidence = confidence,
            notes = notes,
        ),
        rawJson = frameJson.encodeToString(FrameResponseDto.serializer(), this),
    )
}

const val KIND_DISH = "dish"
const val KIND_PLATE = "plate"
const val KIND_LABEL = "label"
const val KIND_DRINK = "drink"
const val KIND_NOT_FOOD = "not_food"
