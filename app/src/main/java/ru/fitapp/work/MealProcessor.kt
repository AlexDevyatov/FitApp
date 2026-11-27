package ru.fitapp.work

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import ru.fitapp.core.LogTags

/**
 * Enqueue обработки снимка (§10 PLAN):
 * UniqueWork("meal-<id>"), ExistingWorkPolicy.KEEP, NetworkType.CONNECTED, backoff 30s exponential.
 *
 * Логи (tag `FitAppWorker`): постановка в очередь с work_id — по нему запись стыкуется
 * с отчётом WorkManager и стартом AnalyzeWorker.
 */
object MealProcessor {

    private const val TAG = LogTags.WORKER

    fun uniqueName(mealId: String) = "meal-$mealId"

    /** Уникальное имя работы пересчёта КБЖУ по названию компонента. */
    fun recalcUniqueName(itemId: String) = "recalc-$itemId"

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
        Log.i(TAG, "meal=$mealId: поставлен в очередь на разбор (KEEP) work_id=${request.id}")
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
        Log.i(TAG, "meal=$mealId: ручной повтор разбора поставлен в очередь (REPLACE) work_id=${request.id}")
    }

    /**
     * Пересчёт КБЖУ компонента по новому названию. REPLACE: правка названия
     * отменяет незавершённый пересчёт по прежнему названию и сбрасывает попытки.
     */
    fun enqueueItemRecalc(context: Context, itemId: String) {
        val request = OneTimeWorkRequestBuilder<ItemRecalcWorker>()
            .setInputData(workDataOf(ItemRecalcWorker.KEY_ITEM_ID to itemId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(recalcUniqueName(itemId), ExistingWorkPolicy.REPLACE, request)
        Log.i(TAG, "item=$itemId: пересчёт КБЖУ по названию поставлен в очередь work_id=${request.id}")
    }
}