// EncryptedSharedPreferences и MasterKey помечены deprecated в security-crypto 1.1.0-alpha07
// (Jetpack Security свёрнут целиком). Замена — собственный AES-GCM поверх Android Keystore —
// меняет формат хранения ключа, поэтому вынесена за границы MVP (§11 PLAN). До миграции
// API остаётся рабочим: подавляем предупреждение осознанно, а не по недосмотру.
@file:Suppress("DEPRECATION")

package ru.fitapp.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Хранилище API-ключа провайдера (§11 PLAN): EncryptedSharedPreferences,
 * ключ не появляется в БД, логах и APK. Класс намеренно не логирует значения.
 */
class ApiKeyStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(apiKey: String) {
        prefs.edit().putString(KEY_API, apiKey).apply()
    }

    fun read(): String? =
        prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() }

    fun clear() {
        prefs.edit().remove(KEY_API).apply()
    }

    private companion object {
        const val PREFS_FILE = "fitapp_secure"
        const val KEY_API = "api_key"
    }
}
