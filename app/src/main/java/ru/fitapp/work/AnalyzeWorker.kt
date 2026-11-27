package ru.fitapp.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import ru.fitapp.FitAppApplication
import ru.fitapp.core.LogTags
import ru.fitapp.data.MealRepository
import ru.fitapp.data.db.MealStatus
import ru.fitapp.domain.AnalyzeOutcome

/**
 * Обработка одного снимка (§10 PLAN): meal(PENDING) → QwenAnalyzer → DONE/NOT_FOOD/FAILED/AUTH_ERROR
 * либо Result.retry() для Retryable. Backoff exponential 30s задаётся при enqueue (MealProcessor).
 *
 * Логи (tag `FitAppWorker`): старт и остановка работы, попытки, ожидание слота семафора,
 * исход разбора и любое исключение. Связываются с логами снимка по mealId.
 */
class AnalyzeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(
            TAG,
            "work id=$id: старт run_attempt=$runAttemptCount meal=${inputData.getString(KEY_MEAL_ID)}",
        )
        return AiThrottle.withPermit {
            try {
                run()
            } catch (e: CancellationException) {
                // Система остановила работу (отмена, лимит времени): карточка может остаться в RUNNING.
                Log.w(
                    TAG,
                    "work id=$id: работа отменена meal=${inputData.getString(KEY_MEAL_ID)}",
                )
                throw e
            } catch (e: Exception) {
                // Поведение прежнее (исключение уходит в WorkManager), но инцидент виден в logcat.
                Log.e(
                    TAG,
                    "work id=$id: разбор упал с исключением meal=${inputData.getString(KEY_MEAL_ID)}",
                    e,
                )
                throw e
            }
        }
    }

    private suspend fun run(): Result {
        val mealId = inputData.getString(KEY_MEAL_ID)
        if (mealId == null) {
            Log.e(TAG, "work id=$id: во входных данных нет $KEY_MEAL_ID")
            return Result.failure()
        }
        val app = applicationContext as? FitAppApplication
        if (app == null) {
            Log.e(TAG, "work id=$id: applicationContext не FitAppApplication")
            return Result.failure()
        }
        val repo: MealRepository = app.mealRepository

        val meal = repo.getMeal(mealId)
        if (meal == null) {
            Log.w(TAG, "meal=$mealId: карточки нет в базе, работа завершена")
            return Result.failure()
        }
        // Конечные статусы не перезапускаем (защита от повторного enqueue).
        if (meal.status == MealStatus.DONE ||
            meal.status == MealStatus.AUTH_ERROR ||
            meal.status == MealStatus.NOT_FOOD
        ) {
            Log.i(TAG, "meal=$mealId: статус ${meal.status} конечный, разбор не нужен")
            return Result.success()
        }

        repo.setStatus(mealId, MealStatus.RUNNING)
        repo.incrementAttempts(mealId)
        val attempts = meal.attempts + 1
        Log.i(TAG, "meal=$mealId: старт разбора, попытка $attempts из $MAX_ATTEMPTS")

        val jpeg = app.photoStore.read(mealId)
        if (jpeg == null) {
            Log.e(TAG, "meal=$mealId: файл снимка не найден, разбор невозможен")
            repo.markFailed(mealId, "photo_missing", "Файл снимка не найден.")
            return Result.failure()
        }
        Log.i(TAG, "meal=$mealId: снимок ${jpeg.size} байт прочитан")

        return when (val outcome = app.analyzer.analyze(jpeg)) {
            is AnalyzeOutcome.Ok -> {
                Log.i(
                    TAG,
                    "meal=$mealId: готово, компонентов ${outcome.result.items.size}, " +
                        "уверенность ${outcome.result.confidence}",
                )
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
                Log.i(TAG, "meal=$mealId: на фото не найдена еда")
                repo.markNotFood(mealId, outcome.notes)
                Result.success()
            }

            is AnalyzeOutcome.AuthError -> {
                // §4.4: без ретраев, сразу видимый статус
                Log.e(TAG, "meal=$mealId: проблема с ключом — ${outcome.message}")
                repo.markAuthError(mealId, outcome.message)
                Result.failure()
            }

            is AnalyzeOutcome.Permanent -> {
                // 400 / битая схема / TLS — без ретраев (§4.4)
                Log.e(TAG, "meal=$mealId: неисправимая ошибка — ${outcome.reason}")
                repo.markFailed(mealId, "permanent", outcome.reason)
                Result.failure()
            }

            is AnalyzeOutcome.Retryable -> {
                if (attempts >= MAX_ATTEMPTS) {
                    Log.w(TAG, "meal=$mealId: попытки исчерпаны ($attempts), последняя причина — ${outcome.reason}")
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
                        Log.i(TAG, "meal=$mealId: ждём ${retryAfter}мс по Retry-After перед повтором")
                        delay(retryAfter)
                    }
                    Log.i(TAG, "meal=$mealId: повтор (${outcome.reason})")
                    Result.retry()
                }
            }
        }
    }

    companion object {
        private const val TAG = LogTags.WORKER

        const val KEY_MEAL_ID = "mealId"
        const val MAX_ATTEMPTS = 5
        const val MODEL_NAME = "qwen4-max"

        /** Потолок ожидания Retry-After внутри воркера — 2 минуты. */
        const val MAX_RETRY_AFTER_WAIT_MS = 120_000L
    }
}