package ru.fitapp.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Enqueue обработки снимка (§10 PLAN):
 * UniqueWork("meal-<id>"), ExistingWorkPolicy.KEEP, NetworkType.CONNECTED, backoff 30s exponential.
 */
object MealProcessor {

    fun uniqueName(mealId: String) = "meal-$mealId"

    /** Поставить карточку в очередь. Повторный вызов с тем же mealId не создаёт вторую работу. */
    fun enqueue(context: Context, mealId: String) {
        val request = OneTimeWorkRequestBuilder<AnalyzeWorker>()
            .setInputData(workDataOf(AnalyzeWorker.KEY_MEAL_ID to mealId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueName(mealId), ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Ручной ретрай из карточки (§10): сбрасывает счётчик попыток
     * и перезапускает работу даже если предыдущая ещё висит в KEEP-очереди.
     */
    suspend fun retryNow(context: Context, mealId: String, resetAttempts: suspend (String) -> Unit) {
        resetAttempts(mealId)
        val request = OneTimeWorkRequestBuilder<AnalyzeWorker>()
            .setInputData(workDataOf(AnalyzeWorker.KEY_MEAL_ID to mealId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(uniqueName(mealId), ExistingWorkPolicy.REPLACE, request)
    }
}
