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
import ru.fitapp.data.prefs.SettingsStore
import ru.fitapp.domain.DailyTargets
import ru.fitapp.ui.icon.AppIcon
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsStore.DEFAULT_PUSH_HOUR)

    /** Тема: 0 = как в системе, 1 = светлая, 2 = тёмная. */
    val darkThemeMode: StateFlow<Int> = fitApp.settingsStore.darkThemeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsStore.THEME_MODE_SYSTEM)

    /** Выбранная иконка ярлыка (см. [ru.fitapp.ui.icon.AppIcon]). */
    val appIcon: StateFlow<Int> = fitApp.settingsStore.appIcon
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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
            // Экран настроек открывается всегда: сбой хранилища ключа — это не повод падать.
            _hasKey.value = runCatching { fitApp.keyStore.read() }
                .onFailure { Log.e(TAG, "settings: состояние ключа не прочитано", it) }
                .getOrNull() != null
        }
    }

    fun clearKey() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "settings: пользователь удаляет API-ключ")
            runCatching { fitApp.keyStore.clear() }
                .onFailure { Log.e(TAG, "settings: ключ не удалён", it) }
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

    fun setDarkThemeMode(mode: Int) {
        viewModelScope.launch {
            Log.i(TAG, "settings: пользователь меняет тему mode=$mode")
            fitApp.settingsStore.setDarkThemeMode(mode)
        }
    }

    /**
     * Иконка ярлыка: выбор пишем в DataStore, а сам алиас в манифесте переключаем
     * через PackageManager — система разрешает менять иконку только так.
     */
    fun setAppIcon(icon: AppIcon) {
        viewModelScope.launch {
            Log.i(TAG, "settings: пользователь меняет иконку ${icon.alias}")
            fitApp.settingsStore.setAppIcon(icon.id)
            AppIcon.apply(getApplication(), icon)
        }
    }

    private companion object {
        const val TAG = LogTags.UI
    }
}
