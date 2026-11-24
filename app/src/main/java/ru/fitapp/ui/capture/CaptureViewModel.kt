package ru.fitapp.ui.capture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.fitapp.FitAppApplication
import ru.fitapp.ui.MealCreator

/**
 * Съёмка (§9): снимок уходит в обработку без диалогов —
 * сохраняем фото, создаём meal(PENDING), ставим в очередь и возвращаемся в ленту.
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
        if (current is CaptureState.Saving || current is CaptureState.Saved) return
        viewModelScope.launch {
            _state.value = CaptureState.Saving
            val ok = runCatching { creator.importJpeg(jpeg) }.isSuccess
            _state.value = if (ok) CaptureState.Saved else CaptureState.Error
        }
    }

    fun onCaptureError() {
        if (_state.value !is CaptureState.Saved) _state.value = CaptureState.Error
    }
}
