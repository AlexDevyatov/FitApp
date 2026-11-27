package ru.fitapp.ui.meal

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.fitapp.FitAppApplication
import ru.fitapp.core.LogTags
import ru.fitapp.data.db.ItemRecalcState
import ru.fitapp.data.db.MealStatus
import ru.fitapp.data.db.MealWithItems
import ru.fitapp.domain.FrameKind
import ru.fitapp.work.MealProcessor

/**
 * Карточка приёма пищи (§9): компоненты, правки (тип кадра, название и масса,
 * отметка «С сахаром», удаление), повтор обработки при FAILED/AUTH_ERROR.
 *
 * Название и масса сохраняются сразу; после смены названия ставится пересчёт КБЖУ
 * по названию (ItemRecalcWorker), масса пересчитывается локально в MealRepository.
 * Каждая правка пишет edit и помечает meal.edited / day.hand_edited.
 *
 * Логи (tag `FitAppUI`): отменённые действия, удаление, ручной повтор и правки.
 * Сами изменения карточки пишет MealRepository (tag `FitAppData`).
 */
class MealViewModel(app: Application, private val mealId: String) : AndroidViewModel(app) {

    private val fitApp: FitAppApplication = app as FitAppApplication

    val meal: StateFlow<MealWithItems?> = fitApp.mealRepository.observeMeal(mealId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun setKind(kind: FrameKind) {
        viewModelScope.launch { fitApp.mealRepository.setMealKind(mealId, kind) }
    }

    /** Отметка «С сахаром»: пересчитывает КБЖУ карточки без запроса к модели. */
    fun setSugar(withSugar: Boolean) {
        viewModelScope.launch { fitApp.mealRepository.setSugarAdded(mealId, withSugar) }
    }

    /**
     * Правка компонента. Название изменилось — ставим пересчёт КБЖУ по названию:
     * числа модели для нового блюда иначе остались бы от прежнего.
     */
    fun updateItem(itemId: String, newName: String, grams: Double?) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            Log.w(TAG, "meal=$mealId item=$itemId: пустое название — правка отклонена")
            return
        }
        viewModelScope.launch {
            val result = fitApp.mealRepository.updateItem(itemId, trimmed, grams)
            if (result.requiresReestimate) {
                Log.i(TAG, "meal=$mealId item=$itemId: название изменилось — КБЖУ будут пересчитаны")
                enqueueRecalc(itemId)
            }
        }
    }

    /** Повтор пересчёта после ошибки: работа ставится заново, счётчик попыток сбрасывается. */
    fun retryRecalc(itemId: String) {
        viewModelScope.launch {
            Log.i(TAG, "meal=$mealId item=$itemId: ручной повтор пересчёта КБЖУ")
            enqueueRecalc(itemId)
        }
    }

    /**
     * Ставит пересчёт в очередь и сразу помечает компонент: пока работа ждёт сеть,
     * карточка показывает «Пересчитываем КБЖУ», а не молчит со старыми числами.
     */
    private suspend fun enqueueRecalc(itemId: String) {
        fitApp.mealRepository.setItemRecalcState(itemId, ItemRecalcState.RUNNING, null)
        runCatching { MealProcessor.enqueueItemRecalc(getApplication(), itemId) }
            .onFailure { error ->
                Log.e(TAG, "meal=$mealId item=$itemId: пересчёт не удалось поставить в очередь", error)
                fitApp.mealRepository.setItemRecalcState(
                    itemId,
                    ItemRecalcState.FAILED,
                    "Не удалось поставить пересчёт в очередь",
                )
            }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch { fitApp.mealRepository.deleteItem(itemId) }
    }

    fun deleteMeal() {
        viewModelScope.launch {
            Log.i(TAG, "meal=$mealId: пользователь удаляет карточку")
            fitApp.mealRepository.deleteMeal(mealId)
            withContext(Dispatchers.IO) { fitApp.photoStore.delete(mealId) }
            _deleted.value = true
            Log.i(TAG, "meal=$mealId: карточка удалена, экран закрывается")
        }
    }

    /**
     * Ручной ретрай (§10): сбрасывает счётчик попыток и перезапускает работу.
     * Статус возвращается в PENDING — иначе воркер пропустит карточку
     * (конечные статусы не перезапускаются, в том числе AUTH_ERROR).
     */
    fun retry() {
        viewModelScope.launch {
            Log.i(TAG, "meal=$mealId: ручной повтор разбора пользователем")
            fitApp.mealRepository.setStatus(mealId, MealStatus.PENDING)
            MealProcessor.retryNow(fitApp, mealId, fitApp.mealRepository::resetAttempts)
            Log.i(TAG, "meal=$mealId: повтор разбора поставлен в очередь")
        }
    }

    private companion object {
        const val TAG = LogTags.UI
    }
}