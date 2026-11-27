package ru.fitapp.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import ru.fitapp.FitAppApplication
import ru.fitapp.ai.QwenAnalyzer
import ru.fitapp.core.LogTags
import ru.fitapp.core.logExcerpt
import ru.fitapp.data.MealRepository
import ru.fitapp.data.db.ItemEntity
import ru.fitapp.data.db.ItemRecalcState
import ru.fitapp.domain.AnalyzeOutcome

/**
 * Пересчёт КБЖУ компонента после правки названия (без фотографии): модель получает
 * название и текущую массу и возвращает пищевую ценность этой порции.
 *
 * Состояние видно в карточке: `item.recalc_state` = RUNNING на время запроса и FAILED
 * с текстом ошибки, если пересчитать не удалось — старые числа при этом остаются.
 *
 * Логи (tag `FitAppWorker`): старт, исход и ошибки; стыкуются с карточкой по itemId.
 */
class ItemRecalcWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val itemId = inputData.getString(KEY_ITEM_ID)
        if (itemId == null) {
            Log.e(TAG, "work id=$id: во входных данных нет $KEY_ITEM_ID")
            return Result.failure()
        }
        val app = applicationContext as? FitAppApplication
        if (app == null) {
            Log.e(TAG, "work id=$id: applicationContext не FitAppApplication")
            return Result.failure()
        }
        val repo: MealRepository = app.mealRepository
        val item = repo.getItem(itemId)
        if (item == null) {
            // Компонент удалили, пока работа стояла в очереди: пересчитывать нечего.
            Log.i(TAG, "item=$itemId: компонента нет в базе, пересчёт не нужен")
            return Result.success()
        }
        Log.i(
            TAG,
            "work id=$id: старт пересчёта КБЖУ run_attempt=$runAttemptCount item=$itemId " +
                "name=«${logExcerpt(item.name, 60)}» grams=${item.grams}",
        )
        return try {
            recalculate(app, repo, itemId, item)
        } catch (e: CancellationException) {
            Log.w(TAG, "work id=$id: пересчёт отменён item=$itemId")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "work id=$id: пересчёт упал с исключением item=$itemId", e)
            throw e
        }
    }

    private suspend fun recalculate(
        app: FitAppApplication,
        repo: MealRepository,
        itemId: String,
        item: ItemEntity,
    ): Result {
        repo.setItemRecalcState(itemId, ItemRecalcState.RUNNING, null)
        val outcome = AiThrottle.withPermit { app.analyzer.estimate(item.name, item.grams) }
        return when (outcome) {
            is AnalyzeOutcome.Ok -> {
                val estimate = outcome.result.items.firstOrNull()
                if (estimate == null) {
                    fail(repo, itemId, "Модель не вернула оценку состава")
                } else {
                    repo.applyItemEstimate(itemId, estimate, QwenAnalyzer.MODEL)
                    Log.i(TAG, "item=$itemId: КБЖУ пересчитаны по названию")
                    Result.success()
                }
            }

            is AnalyzeOutcome.NotFood -> fail(repo, itemId, "Модель не смогла оценить это название")

            is AnalyzeOutcome.AuthError -> fail(repo, itemId, outcome.message)

            is AnalyzeOutcome.Permanent -> fail(repo, itemId, outcome.reason)

            is AnalyzeOutcome.Retryable -> {
                if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
                    fail(
                        repo,
                        itemId,
                        "Не удалось пересчитать после $MAX_ATTEMPTS попыток. Последняя причина: ${outcome.reason}",
                    )
                } else {
                    val retryAfter = outcome.retryAfterMs
                    if (retryAfter != null && retryAfter in 1..MAX_RETRY_AFTER_WAIT_MS) {
                        Log.i(TAG, "item=$itemId: ждём ${retryAfter}мс по Retry-After перед повтором")
                        delay(retryAfter)
                    }
                    Log.i(TAG, "item=$itemId: повтор пересчёта (${outcome.reason})")
                    Result.retry()
                }
            }
        }
    }

    private suspend fun fail(repo: MealRepository, itemId: String, message: String): Result {
        repo.setItemRecalcState(itemId, ItemRecalcState.FAILED, message)
        Log.w(TAG, "item=$itemId: пересчёт не удался — ${logExcerpt(message, 160)}")
        return Result.failure()
    }

    companion object {
        private const val TAG = LogTags.WORKER

        const val KEY_ITEM_ID = "itemId"

        /** Пересчёт дешевле разбора снимка, поэтому попыток меньше. */
        const val MAX_ATTEMPTS = 3

        const val MAX_RETRY_AFTER_WAIT_MS = 60_000L
    }
}