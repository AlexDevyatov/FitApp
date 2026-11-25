package ru.fitapp.ui.settings

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
import ru.fitapp.FitAppApplication
import ru.fitapp.core.LogTags
import ru.fitapp.data.MealStats
import ru.fitapp.domain.DailyTargets
import ru.fitapp.work.EveningReportScheduler

/**
 * Настройки (§9): ключ, вечерний пуш с временем, цели, «Статистика теста»
 * (локальные счётчики, без отправки куда-либо).
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val fitApp: FitAppApplication = app as FitAppApplication

    private val _hasKey = MutableStateFlow(false)
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    val pushEnabled: StateFlow<Boolean> = fitApp.settingsStore.pushEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val pushHour: StateFlow<Int> = fitApp.settingsStore.pushHour
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ru.fitapp.data.prefs.SettingsStore.DEFAULT_PUSH_HOUR)

    val targets: StateFlow<DailyTargets?> = fitApp.profileRepository.targets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val stats: StateFlow<MealStats?> = fitApp.mealRepository.observeMealStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val editDays: StateFlow<List<ru.fitapp.data.db.EditDayCount>> = fitApp.mealRepository.observeEditDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentEdits: StateFlow<List<ru.fitapp.data.db.RecentEdit>> = fitApp.mealRepository.observeRecentEdits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refreshKeyState()
    }

    /** Ключ лежит в EncryptedSharedPreferences — читаем не на главном потоке. */
    fun refreshKeyState() {
        viewModelScope.launch(Dispatchers.IO) {
            _hasKey.value = fitApp.keyStore.read() != null
        }
    }

    fun clearKey() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "settings: пользователь удаляет API-ключ")
            fitApp.keyStore.clear()
            _hasKey.value = false
        }
    }

    /** Включение/выключение пуша с перепланированием PeriodicWork (§3, §10). */
    fun setPushEnabled(enabled: Boolean) {
        viewModelScope.launch {
            Log.i(TAG, "settings: пользователь меняет пуш enabled=$enabled")
            fitApp.settingsStore.setPushEnabled(enabled)
            EveningReportScheduler.reschedule(getApplication())
        }
    }

    fun setPushHour(hour: Int) {
        viewModelScope.launch {
            Log.i(TAG, "settings: пользователь меняет час пуша hour=$hour")
            fitApp.settingsStore.setPushHour(hour)
            EveningReportScheduler.reschedule(getApplication())
        }
    }

    private companion object {
        const val TAG = LogTags.UI
    }
}
