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

    companion object {
        private const val TAG = LogTags.DATA

        const val DEFAULT_PUSH_HOUR = 21

        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_PUSH_ENABLED = booleanPreferencesKey("push_enabled")
        private val KEY_PUSH_HOUR = intPreferencesKey("push_hour")
    }
}
