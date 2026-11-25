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
import java.security.KeyStore

/**
 * Хранилище API-ключа провайдера (§11 PLAN): EncryptedSharedPreferences,
 * ключ не появляется в БД, логах и APK. Класс намеренно не логирует значения:
 * только наличие ключа и результат операции.
 */
class ApiKeyStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy { openPrefs() }

    /**
     * Открывает зашифрованное хранилище. Keyset лежит в prefs, а мастер-ключ — в Android
     * Keystore, поэтому перенос данных на другое устройство (или сброс Keystore) оставляет
     * нерасшифровываемый файл: `create` падает с AEADBadTagException. Раньше это означало
     * падение экрана настроек, потому что ключ читается при его открытии. Восстановить
     * такой keyset нельзя — сбрасываем хранилище и предлагаем ввести ключ заново.
     */
    private fun openPrefs(): SharedPreferences {
        return try {
            createPrefs()
        } catch (e: Exception) {
            Log.e(TAG, "key: зашифрованное хранилище не открылось — сбрасываем ключ", e)
            resetStorage()
            try {
                createPrefs()
            } catch (retry: Exception) {
                Log.e(TAG, "key: хранилище не открылось и после сброса", retry)
                throw retry
            }
        }
    }

    private fun createPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Удаляет и файл keyset, и мастер-ключ: без второго файл всё равно не расшифровать. */
    private fun resetStorage() {
        appContext.deleteSharedPreferences(PREFS_FILE)
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(MASTER_KEY_ALIAS)
        }.onFailure {
            Log.w(TAG, "key: мастер-ключ не удалён из Keystore", it)
        }
        Log.w(TAG, "key: хранилище сброшено, API-ключ нужно ввести заново")
    }

    /** @return true, если ключ действительно записан в хранилище. */
    fun save(apiKey: String): Boolean = try {
        prefs.edit().putString(KEY_API, apiKey).apply()
        Log.i(TAG, "key: сохранён (значение не логируется)")
        true
    } catch (e: Exception) {
        Log.e(TAG, "key: ключ не сохранён", e)
        false
    }

    fun read(): String? = try {
        val value = prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() }
        // Отсутствие ключа — частая причина «запрос не ушёл», поэтому фиксируем и его.
        if (value == null) {
            Log.w(TAG, "key: ключа в хранилище нет")
        } else {
            Log.i(TAG, "key: ключ найден")
        }
        value
    } catch (e: Exception) {
        // Чтение не должно ронять экран настроек даже при неудачном восстановлении хранилища.
        Log.e(TAG, "key: ключ не прочитан", e)
        null
    }

    fun clear() {
        try {
            prefs.edit().remove(KEY_API).apply()
            Log.i(TAG, "key: ключ удалён из хранилища")
        } catch (e: Exception) {
            Log.e(TAG, "key: ключ не удалён из хранилища", e)
        }
    }

    private companion object {
        const val TAG = LogTags.KEY

        const val ANDROID_KEYSTORE = "AndroidKeyStore"

        /** Alias по умолчанию у [MasterKey.Builder], если не задан свой. */
        val MASTER_KEY_ALIAS: String = MasterKey.DEFAULT_MASTER_KEY_ALIAS

        const val PREFS_FILE = "fitapp_secure"
        const val KEY_API = "api_key"
    }
}