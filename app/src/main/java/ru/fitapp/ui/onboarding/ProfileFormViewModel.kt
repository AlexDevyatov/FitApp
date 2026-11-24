package ru.fitapp.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.fitapp.FitAppApplication
import ru.fitapp.data.ProfileRepository.Companion.activityEnum
import ru.fitapp.domain.Activity
import ru.fitapp.domain.Sex
import ru.fitapp.domain.Tdee
import ru.fitapp.domain.TdeeGoals

/**
 * Анкета и расчёт цели (§8.1, §9): используется и в онбординге, и в настройках
 * («просмотр/правка целей»). Цель пересчитывается на каждое изменение полей.
 */
class ProfileFormViewModel(app: Application) : AndroidViewModel(app) {

    private val fitApp: FitAppApplication = app as FitAppApplication

    data class FormState(
        val sex: Sex = Sex.FEMALE,
        val age: String = "",
        val height: String = "",
        val weight: String = "",
        val activity: Activity = Activity.LIGHT,
        val deficitPct: Int = Tdee.MIN_DEFICIT_PCT,
        val goals: TdeeGoals? = null,
        val loaded: Boolean = false,
        val saved: Boolean = false,
    )

    private val _state = MutableStateFlow(FormState())
    val state: StateFlow<FormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = fitApp.profileRepository.getProfile()
            _state.update { current ->
                if (profile == null) {
                    current.copy(loaded = true)
                } else {
                    val filled = current.copy(
                        sex = runCatching { Sex.valueOf(profile.sex) }.getOrDefault(Sex.FEMALE),
                        age = profile.age.toString(),
                        height = profile.heightCm.toString(),
                        weight = trimNumber(profile.weightKg),
                        activity = profile.activityEnum() ?: Activity.LIGHT,
                        deficitPct = profile.deficitPct.coerceIn(Tdee.MIN_DEFICIT_PCT, Tdee.MAX_DEFICIT_PCT),
                        loaded = true,
                    )
                    filled.copy(goals = computeGoals(filled))
                }
            }
        }
    }

    fun onSexChange(sex: Sex) = updateState { it.copy(sex = sex) }

    fun onAgeChange(value: String) = updateState { it.copy(age = digitsOnly(value)) }

    fun onHeightChange(value: String) = updateState { it.copy(height = digitsOnly(value)) }

    fun onWeightChange(value: String) = updateState { it.copy(weight = decimalOnly(value)) }

    fun onActivityChange(activity: Activity) = updateState { it.copy(activity = activity) }

    fun onDeficitChange(pct: Int) =
        updateState { it.copy(deficitPct = pct.coerceIn(Tdee.MIN_DEFICIT_PCT, Tdee.MAX_DEFICIT_PCT)) }

    fun save() {
        val form = _state.value
        val goals = form.goals ?: return
        val age = form.age.toIntOrNull() ?: return
        val heightCm = form.height.replace(',', '.').toDoubleOrNull()?.roundToInt() ?: return
        val weightKg = form.weight.replace(',', '.').toDoubleOrNull() ?: return
        viewModelScope.launch {
            fitApp.profileRepository.saveGoals(
                goals = goals,
                sex = form.sex,
                age = age,
                heightCm = heightCm,
                weightKg = weightKg,
                activity = form.activity.factor,
                deficitPct = form.deficitPct,
            )
            // Онбординг завершается сохранением анкеты; в режиме правки флаг уже true.
            fitApp.settingsStore.setOnboardingCompleted(true)
            _state.update { it.copy(saved = true) }
        }
    }

    private fun updateState(transform: (FormState) -> FormState) {
        _state.update { current ->
            val next = transform(current)
            next.copy(goals = computeGoals(next))
        }
    }

    /** Цель считается, только когда все три числа заполнены и в правдоподобных границах. */
    private fun computeGoals(form: FormState): TdeeGoals? {
        val age = form.age.toIntOrNull() ?: return null
        val heightCm = form.height.replace(',', '.').toDoubleOrNull() ?: return null
        val weightKg = form.weight.replace(',', '.').toDoubleOrNull() ?: return null
        if (age !in AGE_RANGE || heightCm !in HEIGHT_RANGE || weightKg !in WEIGHT_RANGE) return null
        return Tdee.goals(
            sex = form.sex,
            weightKg = weightKg,
            heightCm = heightCm,
            ageYears = age,
            activityFactor = form.activity.factor,
            deficitPct = form.deficitPct,
        )
    }

    private fun digitsOnly(value: String): String = value.filter { it.isDigit() }

    private fun decimalOnly(value: String): String =
        value.filter { it.isDigit() || it == ',' || it == '.' }

    private fun trimNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private companion object {
        val AGE_RANGE = 14..100
        val HEIGHT_RANGE = 100.0..250.0
        val WEIGHT_RANGE = 30.0..400.0
    }
}
