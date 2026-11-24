package ru.fitapp.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ru.fitapp.FitAppApplication
import ru.fitapp.data.MealRepository
import ru.fitapp.data.db.MealStatus
import ru.fitapp.domain.AnalyzeOutcome

/**
 * Обработка одного снимка (§10 PLAN): meal(PENDING) → QwenAnalyzer → DONE/NOT_FOOD/FAILED/AUTH_ERROR
 * либо Result.retry() для Retryable. Backoff exponential 30s задаётся при enqueue (MealProcessor).
 */
class AnalyzeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = semaphore.withPermit { run() }

    private suspend fun run(): Result {
        val mealId = inputData.getString(KEY_MEAL_ID) ?: return Result.failure()
        val app = applicationContext as? FitAppApplication ?: return Result.failure()
        val repo: MealRepository = app.mealRepository

        val meal = repo.getMeal(mealId) ?: return Result.failure()
        // Конечные статусы не перезапускаем (защита от повторного enqueue).
        if (meal.status == MealStatus.DONE ||
            meal.status == MealStatus.AUTH_ERROR ||
            meal.status == MealStatus.NOT_FOOD
        ) {
            return Result.success()
        }

        repo.setStatus(mealId, MealStatus.RUNNING)
        repo.incrementAttempts(mealId)
        val attempts = meal.attempts + 1

        val jpeg = app.photoStore.read(mealId)
        if (jpeg == null) {
            repo.markFailed(mealId, "photo_missing", "Файл снимка не найден.")
            return Result.failure()
        }

        return when (val outcome = app.analyzer.analyze(jpeg)) {
            is AnalyzeOutcome.Ok -> {
                repo.saveAnalysis(
                    mealId = mealId,
                    kind = outcome.result.kind,
                    confidence = outcome.result.confidence,
                    rawJson = outcome.rawJson.orEmpty(),
                    model = MODEL_NAME,
                    items = outcome.result.items,
                )
                Result.success()
            }

            is AnalyzeOutcome.NotFood -> {
                repo.markNotFood(mealId, outcome.notes)
                Result.success()
            }

            is AnalyzeOutcome.AuthError -> {
                // §4.4: без ретраев, сразу видимый статус
                repo.markAuthError(mealId, outcome.message)
                Result.failure()
            }

            is AnalyzeOutcome.Permanent -> {
                // 400 / битая схема / length / TLS — без ретраев (§4.4)
                repo.markFailed(mealId, "permanent", outcome.reason)
                Result.failure()
            }

            is AnalyzeOutcome.Retryable -> {
                if (attempts >= MAX_ATTEMPTS) {
                    repo.markFailed(
                        mealId,
                        "attempts_exhausted",
                        "Не удалось обработать снимок после $MAX_ATTEMPTS попыток. Последняя причина: ${outcome.reason}",
                    )
                    Result.failure()
                } else {
                    // Учитываем Retry-After (429): ждём в воркере, но не дольше потолка,
                    // дальше — обычный экспоненциальный backoff WorkManager.
                    val retryAfter = outcome.retryAfterMs
                    if (retryAfter != null && retryAfter in 1..MAX_RETRY_AFTER_WAIT_MS) {
                        delay(retryAfter)
                    }
                    Result.retry()
                }
            }
        }
    }

    companion object {
        const val KEY_MEAL_ID = "mealId"
        const val MAX_ATTEMPTS = 5
        const val MODEL_NAME = "qwen4-max"

        /** Потолок ожидания Retry-After внутри воркера — 2 минуты. */
        const val MAX_RETRY_AFTER_WAIT_MS = 120_000L

        /** Не больше 2 параллельных воркеров, чтобы не ловить 429 на серии снимков (§10). */
        private val semaphore = Semaphore(2)
    }
}
