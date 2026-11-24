package ru.fitapp.ai

import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.fitapp.core.LogTags
import ru.fitapp.core.logExcerpt
import ru.fitapp.domain.AnalyzeOutcome

/**
 * Метаданные одного ответа chat/completions — минимум, нужный для разбора инцидента:
 * чем закончился ответ и куда ушли токены.
 */
internal data class CompletionStats(
    val finishReason: String?,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val reasoningTokens: Int?,
    val imageTokens: Int?,
    val answerChars: Int,
    val reasoningChars: Int,
) {
    /** Ответ обрезан лимитом max_tokens: JSON заведомо неполный. */
    val truncated: Boolean get() = finishReason == "length"
}

/**
 * Логи AI-слоя (tag `FitAppAI`, уровень I/W/E — видно в logcat без фильтров отладки).
 *
 * Ключ API не логируется никогда: ни целиком, ни частично. Тело ответа не логируется
 * целиком — только короткие выдержки ([excerpt]) в местах, где разбор уже сломался.
 * Всё остальное (модель, размеры, токены, finish_reason, исход) пишется одной строкой,
 * чтобы инцидент разбирался одним `adb logcat -s FitAppAI`.
 */
internal object AiLog {

    const val TAG = LogTags.AI

    /** Длина выдержки из ответа модели: хватает, чтобы увидеть место обрыва. */
    private const val EXCERPT_LIMIT = 240

    fun request(model: String, imageBytes: Int?, promptChars: Int?, maxTokens: Int) {
        val payload = if (imageBytes != null) "image_bytes=$imageBytes" else "prompt_chars=$promptChars"
        Log.i(TAG, "request model=$model $payload max_tokens=$maxTokens")
    }

    fun http(code: Int, elapsedMs: Long, bodyBytes: Int) {
        Log.i(TAG, "response http=$code elapsed_ms=$elapsedMs body_bytes=$bodyBytes")
    }

    fun completion(stats: CompletionStats) {
        Log.i(
            TAG,
            "completion finish_reason=${stats.finishReason} prompt_tokens=${stats.promptTokens} " +
                "image_tokens=${stats.imageTokens} completion_tokens=${stats.completionTokens} " +
                "reasoning_tokens=${stats.reasoningTokens} answer_chars=${stats.answerChars} " +
                "reasoning_chars=${stats.reasoningChars}",
        )
    }

    fun outcome(outcome: AnalyzeOutcome) {
        val line = when (outcome) {
            is AnalyzeOutcome.Ok ->
                "ok items=${outcome.result.items.size} confidence=${outcome.result.confidence}"
            is AnalyzeOutcome.NotFood -> "not_food notes=${excerpt(outcome.notes)}"
            is AnalyzeOutcome.AuthError -> "auth_error message=${excerpt(outcome.message)}"
            is AnalyzeOutcome.Retryable ->
                "retryable reason=${excerpt(outcome.reason)} retry_after_ms=${outcome.retryAfterMs}"
            is AnalyzeOutcome.Permanent -> "permanent reason=${excerpt(outcome.reason)}"
        }
        Log.i(TAG, "outcome $line")
    }

    fun warn(message: String) = Log.w(TAG, message)

    fun error(message: String, error: Throwable? = null) {
        if (error == null) Log.e(TAG, message) else Log.e(TAG, message, error)
    }

    /** Одна строка без переносов: ответ модели иначе растягивается на экраны logcat. */
    fun excerpt(text: String): String = logExcerpt(text, EXCERPT_LIMIT)
}

/** Достаёт finish_reason и usage; отсутствующие/битые поля дают null, а не исключение. */
internal fun readCompletionStats(
    root: JsonObject,
    choice: JsonObject,
    message: JsonObject?,
): CompletionStats {
    val usage = root.objectOrNull("usage")
    val promptDetails = usage?.objectOrNull("prompt_tokens_details")
    val completionDetails = usage?.objectOrNull("completion_tokens_details")
    return CompletionStats(
        finishReason = choice.stringOrNull("finish_reason"),
        promptTokens = usage?.intOrNull("prompt_tokens"),
        completionTokens = usage?.intOrNull("completion_tokens"),
        reasoningTokens = completionDetails?.intOrNull("reasoning_tokens"),
        imageTokens = promptDetails?.intOrNull("image_tokens"),
        answerChars = message?.stringOrNull("content")?.length ?: 0,
        reasoningChars = message?.stringOrNull("reasoning_content")?.length ?: 0,
    )
}

private fun JsonObject.stringOrNull(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

private fun JsonObject.intOrNull(key: String): Int? =
    runCatching { this[key]?.jsonPrimitive?.intOrNull }.getOrNull()

private fun JsonObject.objectOrNull(key: String): JsonObject? =
    runCatching { this[key]?.jsonObject }.getOrNull()