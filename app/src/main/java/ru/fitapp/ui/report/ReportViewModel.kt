package ru.fitapp.ui.report

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.fitapp.FitAppApplication
import ru.fitapp.ai.ReportTextSanitizer
import ru.fitapp.core.LogTags
import ru.fitapp.data.db.MealStatus
import ru.fitapp.domain.DayReport
import ru.fitapp.domain.ReportCalc
import ru.fitapp.work.DayReportData

/**
 * Отчёт дня (§8, §9): цифры — только из domain/ReportCalc, текст — от LLM,
 * но показывается лишь прошедший ReportTextSanitizer (иначе fallback).
 *
 * Логи (tag `FitAppReport`): входные данные расчёта, вердикты и источник текста
 * (модель или fallback). Сами числа отчёта повторяют то, что видит пользователь.
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
                        // Без анкеты отчёт не строится — экран выглядит «пустым» без причины.
                        Log.w(TAG, "report date=$date: целей нет (анкета не заполнена), расчёт пропущен")
                        _state.update { it.copy(report = null, targetsMissing = true, hasDoneMeals = false) }
                    } else {
                        val doneCount = meals.count { it.meal.status == MealStatus.DONE }
                        val failedCount = DayReportData.failedCount(meals)
                        val report = ReportCalc.computeDay(
                            doneMeals = DayReportData.toReportMeals(meals),
                            failedCount = failedCount,
                            targets = targets,
                        )
                        Log.i(
                            TAG,
                            "report date=$date: карточек=${meals.size} done=$doneCount failed=$failedCount " +
                                "little_data=${report.littleData} " +
                                "kcal=[${report.energy.interval.low.roundToInt()}..${report.energy.interval.high.roundToInt()}] " +
                                "target=${report.energy.targetKcal} energy=${report.energy.verdict} " +
                                "protein=${report.protein.verdict} fat=${report.fat.verdict} " +
                                "carbs=${report.carbs.verdict} ultra=${report.composition.ultraProcessedItems}/" +
                                "${report.composition.totalItems}",
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
        val report = _state.value.report
        if (report == null) {
            Log.w(TAG, "report date=$date: текст не запрошен — отчёт не рассчитан")
            return
        }
        if (_state.value.textLoading) {
            Log.i(TAG, "report date=$date: запрос текста уже выполняется")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(textLoading = true) }
            val generator = fitApp.reportTextGenerator
            val inputJson = generator.buildInputJson(report)
            val fallback = generator.buildFallbackText(report)
            val startedAt = SystemClock.elapsedRealtime()
            Log.i(TAG, "report date=$date: запрос текста у модели input_chars=${inputJson.length}")
            val sanitized = try {
                val raw = generator.generate(report)
                ReportTextSanitizer.sanitize(raw, inputJson, fallback)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Не роняем экран отчёта: показываем fallback, но инцидент остаётся в логе.
                Log.e(TAG, "report date=$date: формулировка упала, показан fallback", e)
                fallback
            }
            val fromLlm = sanitized != fallback
            if (!fromLlm) {
                Log.w(TAG, "report date=$date: показан fallback-текст (модель не дала годного ответа)")
            }
            _state.update { it.copy(text = sanitized, textLoading = false) }
            Log.i(
                TAG,
                "report date=$date: текст готов source=${if (fromLlm) "llm" else "fallback"} " +
                    "chars=${sanitized.length} elapsed_ms=${SystemClock.elapsedRealtime() - startedAt}",
            )
        }
    }

    private companion object {
        const val TAG = LogTags.REPORT
    }
}