package ru.fitapp.ui.day

import android.app.Application
import android.net.Uri
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
import ru.fitapp.data.db.MealWithItems
import ru.fitapp.ui.MealCreator

/**
 * Лента карточек выбранного календарного дня (§9: «Сегодня» и «День» из календаря).
 * Импорт из галереи: снимок сразу появляется в ленте со статусом PENDING.
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
        if (_importing.value) return
        viewModelScope.launch {
            _importing.value = true
            val ok = runCatching { creator.importFromUri(uri) }.isSuccess
            if (!ok) _importFailed.value = true
            _importing.value = false
        }
    }

    fun consumeImportError() {
        _importFailed.value = false
    }
}
