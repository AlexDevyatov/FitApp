package ru.fitapp.ui.meal

import android.app.Application
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
import ru.fitapp.data.db.MealStatus
import ru.fitapp.data.db.MealWithItems
import ru.fitapp.domain.FrameKind
import ru.fitapp.work.MealProcessor

/**
 * Карточка приёма пищи (§9): компоненты, правки (тип кадра, название, удаление),
 * повтор обработки при FAILED/AUTH_ERROR. Каждая правка пишет edit и помечает
 * meal.edited / day.hand_edited — это делает MealRepository.
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

    fun renameItem(itemId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { fitApp.mealRepository.renameItem(itemId, trimmed) }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch { fitApp.mealRepository.deleteItem(itemId) }
    }

    fun deleteMeal() {
        viewModelScope.launch {
            fitApp.mealRepository.deleteMeal(mealId)
            withContext(Dispatchers.IO) { fitApp.photoStore.delete(mealId) }
            _deleted.value = true
        }
    }

    /**
     * Ручной ретрай (§10): сбрасывает счётчик попыток и перезапускает работу.
     * Статус возвращается в PENDING — иначе воркер пропустит карточку
     * (конечные статусы не перезапускаются, в том числе AUTH_ERROR).
     */
    fun retry() {
        viewModelScope.launch {
            fitApp.mealRepository.setStatus(mealId, MealStatus.PENDING)
            MealProcessor.retryNow(fitApp, mealId, fitApp.mealRepository::resetAttempts)
        }
    }
}
