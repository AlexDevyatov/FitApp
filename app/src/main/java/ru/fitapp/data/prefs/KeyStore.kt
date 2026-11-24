// EncryptedSharedPreferences и MasterKey помечены deprecated в security-crypto 1.1.0-alpha07
// (Jetpack Security свёрнут целиком). Замена — собственный AES-GCM поверх Android Keystore —
// меняет формат хранения ключа, поэтому вынесена за границы MVP (§11 PLAN). До миграции
// API остаётся рабочим: подавляем предупреждение осознанно, а не по недосмотру.
@file:Suppress("DEPRECATION")

package ru.fitapp.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ru.fitapp.core.LogTags

/**
 * Хранилище API-ключа провайдера (§11 PLAN): EncryptedSharedPreferences,
 * ключ не появляется в БД, логах и APK. Класс намеренно не логирует значения:
 * только наличие ключа и результат операции.
 */
class ApiKeyStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        // Сбой Keystore (например, после restore на другом устройстве) иначе выглядит
        // как падение экрана настроек без единой записи в logcat.
        try {
            createPrefs(context)
        } catch (e: Exception) {
            Log.e(TAG, "key: зашифрованное хранилище не открылось", e)
            throw e
        }
    }

    private fun createPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(apiKey: String) {
        prefs.edit().putString(KEY_API, apiKey).apply()
        Log.i(TAG, "key: сохранён (значение не логируется)")
    }

    fun read(): String? {
        val value = prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() }
        // Отсутствие ключа — частая причина «запрос не ушёл», поэтому фиксируем и его.
        if (value == null) {
            Log.w(TAG, "key: ключа в хранилище нет")
        } else {
            Log.i(TAG, "key: ключ найден")
        }
        return value
    }

    fun clear() {
        prefs.edit().remove(KEY_API).apply()
        Log.i(TAG, "key: ключ удалён из хранилища")
    }

    private companion object {
        const val TAG = LogTags.KEY

        const val PREFS_FILE = "fitapp_secure"
        const val KEY_API = "api_key"
    }
}