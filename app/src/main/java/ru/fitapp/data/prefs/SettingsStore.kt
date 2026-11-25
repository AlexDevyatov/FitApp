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
     * Тема оформления: 0 = как в системе, 1 = светлая, 2 = тёмная.
     * Значение по умолчанию — 0 (следовать системе).
     */
    val darkThemeMode: Flow<Int> =
        dataStore.data.map { (it[KEY_DARK_THEME_MODE] ?: THEME_MODE_SYSTEM).coerceIn(THEME_MODE_SYSTEM, THEME_MODE_DARK) }

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

    suspend fun setDarkThemeMode(mode: Int) {
        val clamped = mode.coerceIn(THEME_MODE_SYSTEM, THEME_MODE_DARK)
        dataStore.edit { it[KEY_DARK_THEME_MODE] = clamped }
        Log.i(TAG, "settings: dark_theme_mode=$clamped")
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

        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_PUSH_ENABLED = booleanPreferencesKey("push_enabled")
        private val KEY_PUSH_HOUR = intPreferencesKey("push_hour")
        private val KEY_DARK_THEME_MODE = intPreferencesKey("dark_theme_mode")
    }
}
