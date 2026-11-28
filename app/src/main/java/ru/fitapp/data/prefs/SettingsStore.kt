package ru.fitapp.data.prefs

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.fitapp.core.LogTags

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Настройки сессии (§3 PLAN): DataStore preferences.
 * Цели здесь не дублируются — они живут в profile (Room).
 */
class SettingsStore(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val onboardingCompleted: Flow<Boolean> =
        dataStore.data.map { it[KEY_ONBOARDING_COMPLETED] ?: false }

    val pushEnabled: Flow<Boolean> =
        dataStore.data.map { it[KEY_PUSH_ENABLED] ?: true }

    /** Час вечернего пуша, по умолчанию 21:00 (§3 PLAN). */
    val pushHour: Flow<Int> =
        dataStore.data.map { (it[KEY_PUSH_HOUR] ?: DEFAULT_PUSH_HOUR).coerceIn(0, 23) }

    /**
     * Спрашивали ли разрешение на уведомления. Нужно, чтобы не показывать
     * системный диалог при каждом запуске после отказа.
     */
    val notificationAsked: Flow<Boolean> =
        dataStore.data.map { it[KEY_NOTIFICATION_ASKED] ?: false }

    /**
     * Тема оформления: 0 = как в системе, 1 = светлая, 2 = тёмная.
     * Значение по умолчанию — 0 (следовать системе).
     */
    val darkThemeMode: Flow<Int> =
        dataStore.data.map { (it[KEY_DARK_THEME_MODE] ?: THEME_MODE_SYSTEM).coerceIn(THEME_MODE_SYSTEM, THEME_MODE_DARK) }

    /**
     * Выбранная иконка ярлыка: 0..5 — id из `ui.icon.AppIcon`, по умолчанию 0
     * (мята на изумруде, как на сплэше). Сама иконка включается через PackageManager,
     * здесь хранится только выбор — чтобы восстановить его после обновления.
     */
    val appIcon: Flow<Int> =
        dataStore.data.map { (it[KEY_APP_ICON] ?: 0).coerceIn(0, APP_ICON_MAX) }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
        Log.i(TAG, "settings: onboarding_completed=$completed")
    }

    suspend fun setPushEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_PUSH_ENABLED] = enabled }
        Log.i(TAG, "settings: push_enabled=$enabled")
    }

    suspend fun setPushHour(hour: Int) {
        val clamped = hour.coerceIn(0, 23)
        dataStore.edit { it[KEY_PUSH_HOUR] = clamped }
        Log.i(TAG, "settings: push_hour=$clamped")
    }

    suspend fun setNotificationAsked(asked: Boolean) {
        dataStore.edit { it[KEY_NOTIFICATION_ASKED] = asked }
        Log.i(TAG, "settings: notification_asked=$asked")
    }

    suspend fun setDarkThemeMode(mode: Int) {
        val clamped = mode.coerceIn(THEME_MODE_SYSTEM, THEME_MODE_DARK)
        dataStore.edit { it[KEY_DARK_THEME_MODE] = clamped }
        Log.i(TAG, "settings: dark_theme_mode=$clamped")
    }

    suspend fun setAppIcon(id: Int) {
        val clamped = id.coerceIn(0, APP_ICON_MAX)
        dataStore.edit { it[KEY_APP_ICON] = clamped }
        Log.i(TAG, "settings: app_icon=$clamped")
    }

    companion object {
        private const val TAG = LogTags.DATA

        const val DEFAULT_PUSH_HOUR = 21

        /** Как в системе. */
        const val THEME_MODE_SYSTEM = 0

        /** Всегда светлая. */
        const val THEME_MODE_LIGHT = 1

        /** Всегда тёмная. */
        const val THEME_MODE_DARK = 2

        /** Последний id иконки в `ui.icon.AppIcon`: за ним выбор не уходит. */
        const val APP_ICON_MAX = 5

        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_PUSH_ENABLED = booleanPreferencesKey("push_enabled")
        private val KEY_PUSH_HOUR = intPreferencesKey("push_hour")
        private val KEY_NOTIFICATION_ASKED = booleanPreferencesKey("notification_asked")
        private val KEY_DARK_THEME_MODE = intPreferencesKey("dark_theme_mode")
        private val KEY_APP_ICON = intPreferencesKey("app_icon")
    }
}
