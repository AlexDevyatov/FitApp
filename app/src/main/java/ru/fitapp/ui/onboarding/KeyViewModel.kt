package ru.fitapp.ui.onboarding

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.fitapp.FitAppApplication
import ru.fitapp.R
import ru.fitapp.ai.KeyProbeResult
import ru.fitapp.core.LogTags

/** Исход проверки ключа в терминах UI (сообщения собираются из ресурсов). */
sealed interface KeyCheckUiResult {
    data object EmptyField : KeyCheckUiResult
    data object Valid : KeyCheckUiResult
    data class InvalidKey(val message: String) : KeyCheckUiResult
    data object ModelMissing : KeyCheckUiResult
    data class TlsError(val detail: String) : KeyCheckUiResult
    data class NetworkError(val detail: String) : KeyCheckUiResult
}

/**
 * Экран ввода и проверки ключа (§9: probeKey — GET /models и наличие qwen4-max).
 * Ключ сохраняется в KeyStore до проверки: анализатор читает его из хранилища;
 * при неуспехе проверки ключ сразу удаляется.
 */
class KeyViewModel(app: Application) : AndroidViewModel(app) {

    private val fitApp: FitAppApplication = app as FitAppApplication

    private val _keyText = MutableStateFlow("")
    val keyText: StateFlow<String> = _keyText.asStateFlow()

    private val _keyVisible = MutableStateFlow(false)
    val keyVisible: StateFlow<Boolean> = _keyVisible.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _result = MutableStateFlow<KeyCheckUiResult?>(null)
    val result: StateFlow<KeyCheckUiResult?> = _result.asStateFlow()

    /** Короткое информационное сообщение (буфер пуст, файл прочитан/не прочитан). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun onKeyChange(text: String) {
        _keyText.value = text
        _result.value = null
    }

    fun toggleKeyVisible() {
        _keyVisible.value = !_keyVisible.value
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun pasteFromClipboard(clipboardText: String?) {
        if (clipboardText.isNullOrBlank()) {
            Log.w(TAG, "key: буфер обмена пуст, вставка отменена")
            _message.value = fitApp.getString(R.string.key_clipboard_empty)
            return
        }
        onKeyChange(clipboardText.trim())
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    fitApp.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString().trim() }
                }.onFailure {
                    Log.e(TAG, "key: файл с ключом не прочитан uri=$uri", it)
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                Log.w(TAG, "key: файл пуст или не прочитан uri=$uri")
                _message.value = fitApp.getString(R.string.key_import_failed)
            } else {
                Log.i(TAG, "key: ключ импортирован из файла uri=$uri")
                _keyText.value = text
                _result.value = null
                _message.value = fitApp.getString(R.string.key_imported_from_file)
            }
        }
    }

    fun check() {
        val key = _keyText.value.trim()
        if (key.isEmpty()) {
            Log.w(TAG, "check: поле ключа пустое, проверка не запущена")
            _result.value = KeyCheckUiResult.EmptyField
            return
        }
        viewModelScope.launch {
            _checking.value = true
            _result.value = null
            Log.i(TAG, "check: старт проверки ключа (значение не логируется)")
            withContext(Dispatchers.IO) { fitApp.keyStore.save(key) }
            val probe = fitApp.analyzer.probeKeyDetailed()
            if (probe !is KeyProbeResult.Valid) {
                Log.w(TAG, "check: проверка не прошла ($probe), ключ удалён из хранилища")
                withContext(Dispatchers.IO) { fitApp.keyStore.clear() }
            } else {
                Log.i(TAG, "check: ключ принят провайдером")
            }
            _result.value = when (probe) {
                is KeyProbeResult.Valid -> KeyCheckUiResult.Valid
                is KeyProbeResult.InvalidKey -> KeyCheckUiResult.InvalidKey(probe.message)
                is KeyProbeResult.ModelMissing -> KeyCheckUiResult.ModelMissing
                is KeyProbeResult.TlsError -> KeyCheckUiResult.TlsError(probe.message)
                is KeyProbeResult.NetworkError -> KeyCheckUiResult.NetworkError(probe.message)
            }
            _checking.value = false
        }
    }

    private companion object {
        const val TAG = LogTags.KEY
    }
}
