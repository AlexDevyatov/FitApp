package ru.fitapp.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import ru.fitapp.FitAppApplication
import ru.fitapp.data.db.DayStats

/**
 * Календарь (§9): сетка месяца с отметкой полноты дня и тренд по ккал.
 * Данные — DayStats из Room-запроса по диапазону дат выбранного месяца.
 */
class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val fitApp: FitAppApplication = app as FitAppApplication

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val stats: StateFlow<Map<LocalDate, DayStats>> = _month
        .flatMapLatest { month ->
            fitApp.mealRepository.observeDayStats(month.atDay(1), month.atEndOfMonth())
        }
        .map { list -> list.associateBy { LocalDate.parse(it.date) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val targetKcal: StateFlow<Int?> = fitApp.profileRepository.targets
        .map { it?.kcal }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shiftMonth(deltaMonths: Long) {
        _month.update { it.plusMonths(deltaMonths) }
    }
}
