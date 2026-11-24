package ru.fitapp.domain

/** Тип кадра, определённый моделью (§5 PLAN). */
enum class FrameKind { DISH, PLATE, LABEL }

/** Один компонент разбора кадра (§5 PLAN). */
data class AnalyzedItem(
    val name: String,
    val grams: Double?,
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val fiberG: Double,
    val sugarG: Double,
    val ultraProcessed: Boolean,
)

/** Успешный разбор кадра (§5 PLAN). */
data class AnalysisResult(
    val kind: FrameKind,
    val items: List<AnalyzedItem>,
    val confidence: Double,
    val notes: String,
)

/**
 * Исход анализа одного кадра (§5 PLAN + статус NOT_FOOD из §6:
 * kind == "not_food" — не ошибка разбора и не успех, а отдельный исход).
 */
sealed interface AnalyzeOutcome {
    /** rawJson — исходный JSON модели (для meal.raw_json и отладки); у Mock может быть null. */
    data class Ok(val result: AnalysisResult, val rawJson: String? = null) : AnalyzeOutcome
    data class NotFood(val notes: String) : AnalyzeOutcome
    data class AuthError(val message: String) : AnalyzeOutcome
    data class Retryable(val reason: String, val retryAfterMs: Long? = null) : AnalyzeOutcome
    data class Permanent(val reason: String) : AnalyzeOutcome
}
