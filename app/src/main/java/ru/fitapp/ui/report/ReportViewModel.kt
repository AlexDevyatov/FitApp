package ru.fitapp.ui.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.fitapp.FitAppApplication
import ru.fitapp.ai.ReportTextSanitizer
import ru.fitapp.data.db.MealStatus
import ru.fitapp.domain.DayReport
import ru.fitapp.domain.ReportCalc
import ru.fitapp.work.DayReportData

/**
 * Отчёт дня (§8, §9): цифры — только из domain/ReportCalc, текст — от LLM,
 * но показывается лишь прошедший ReportTextSanitizer (иначе fallback).
 */
class ReportViewModel(app: Application, private val date: LocalDate) : AndroidViewModel(app) {

    private val fitApp: FitAppApplication = app as FitAppApplication

    data class ReportUiState(
        val report: DayReport? = null,
        val targetsMissing: Boolean = false,
        val hasDoneMeals: Boolean = false,
        val text: String? = null,
        val textLoading: Boolean = false,
    )

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                fitApp.mealRepository.observeDay(date),
                fitApp.profileRepository.targets,
            ) { meals, targets -> meals to targets }
                .collect { (meals, targets) ->
                    if (targets == null) {
                        _state.update { it.copy(report = null, targetsMissing = true, hasDoneMeals = false) }
                    } else {
                        val report = ReportCalc.computeDay(
                            doneMeals = DayReportData.toReportMeals(meals),
                            failedCount = DayReportData.failedCount(meals),
                            targets = targets,
                        )
                        _state.update {
                            it.copy(
                                report = report,
                                targetsMissing = false,
                                hasDoneMeals = meals.any { meal -> meal.meal.status == MealStatus.DONE },
                            )
                        }
                    }
                }
        }
    }

    /** Запрос формулировок у LLM; текст дополнительно проходит санитайзер на стороне UI. */
    fun refreshText() {
        val report = _state.value.report ?: return
        if (_state.value.textLoading) return
        viewModelScope.launch {
            _state.update { it.copy(textLoading = true) }
            val generator = fitApp.reportTextGenerator
            val inputJson = generator.buildInputJson(report)
            val fallback = generator.buildFallbackText(report)
            val raw = generator.generate(report)
            val sanitized = ReportTextSanitizer.sanitize(raw, inputJson, fallback)
            _state.update { it.copy(text = sanitized, textLoading = false) }
        }
    }
}
