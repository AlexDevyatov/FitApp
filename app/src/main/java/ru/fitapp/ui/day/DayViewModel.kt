package ru.fitapp.ui.day

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.fitapp.FitAppApplication
import ru.fitapp.core.LogTags
import ru.fitapp.data.db.MealWithItems
import ru.fitapp.ui.MealCreator

/**
 * Лента карточек выбранного календарного дня (§9: «Сегодня» и «День» из календаря).
 * Импорт из галереи: снимок сразу появляется в ленте со статусом PENDING.
 *
 * Логи (tag `FitAppUI`): начало импорта из галереи и причина, если он не удался.
 * Дальше снимок идёт общим путём MealCreator (tag `FitAppPhoto`).
 */
class DayViewModel(app: Application, val date: LocalDate) : AndroidViewModel(app) {

    private val fitApp: FitAppApplication = app as FitAppApplication
    private val creator = MealCreator(fitApp)

    val meals: StateFlow<List<MealWithItems>> = fitApp.mealRepository.observeDay(date)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _importFailed = MutableStateFlow(false)
    val importFailed: StateFlow<Boolean> = _importFailed.asStateFlow()

    fun importFromGallery(uri: Uri) {
        if (_importing.value) {
            Log.w(TAG, "day: импорт уже идёт, повторный выбор проигнорирован uri=$uri")
            return
        }
        viewModelScope.launch {
            _importing.value = true
            Log.i(TAG, "day: импорт из галереи date=$date uri=$uri")
            runCatching { creator.importFromUri(uri) }.fold(
                onSuccess = { mealId ->
                    Log.i(TAG, "day: импорт из галереи завершён date=$date meal=$mealId")
                },
                onFailure = { error ->
                    Log.e(TAG, "day: импорт из галереи не удался date=$date uri=$uri", error)
                    _importFailed.value = true
                },
            )
            _importing.value = false
        }
    }

    fun consumeImportError() {
        _importFailed.value = false
    }

    private companion object {
        const val TAG = LogTags.UI
    }
}