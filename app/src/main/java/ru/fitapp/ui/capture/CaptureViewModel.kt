package ru.fitapp.ui.capture

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.fitapp.FitAppApplication
import ru.fitapp.core.LogTags
import ru.fitapp.ui.MealCreator

/**
 * Съёмка (§9): снимок уходит в обработку без диалогов —
 * сохраняем фото, создаём meal(PENDING), ставим в очередь и возвращаемся в ленту.
 *
 * Логи (tag `FitAppUI`): размер кадра, переходы состояния и причина, если кадр
 * не сохранился. Ключ здесь не участвует и не логируется.
 */
class CaptureViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface CaptureState {
        data object Idle : CaptureState
        data object Saving : CaptureState
        data object Saved : CaptureState
        data object Error : CaptureState
    }

    private val creator = MealCreator(app as FitAppApplication)

    private val _state = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    fun onJpegCaptured(jpeg: ByteArray) {
        val current = _state.value
        if (current is CaptureState.Saving || current is CaptureState.Saved) {
            Log.w(TAG, "capture: повторный снимок проигнорирован state=${current.stateName()}")
            return
        }
        viewModelScope.launch {
            _state.value = CaptureState.Saving
            Log.i(TAG, "capture: кадр получен bytes=${jpeg.size}, сохранение")
            val result = runCatching { creator.importJpeg(jpeg) }
            result.exceptionOrNull()?.let { Log.e(TAG, "capture: кадр не сохранён bytes=${jpeg.size}", it) }
            _state.value = if (result.isSuccess) CaptureState.Saved else CaptureState.Error
            Log.i(TAG, "capture: state=${_state.value.stateName()} meal=${result.getOrNull()}")
        }
    }

    fun onCaptureError(cause: Throwable? = null) {
        if (_state.value !is CaptureState.Saved) _state.value = CaptureState.Error
        if (cause == null) {
            Log.e(TAG, "capture: CameraX не отдал кадр (исключение не передано)")
        } else {
            Log.e(TAG, "capture: CameraX не отдал кадр", cause)
        }
    }

    private fun CaptureState.stateName(): String = this::class.simpleName ?: "Unknown"

    private companion object {
        const val TAG = LogTags.UI
    }
}