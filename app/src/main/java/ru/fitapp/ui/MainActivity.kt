package ru.fitapp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ru.fitapp.FitAppApplication
import ru.fitapp.ui.theme.FitAppTheme

/**
 * Точка входа UI (§9 PLAN): старт — онбординг, если SettingsStore.onboardingCompleted
 * ещё не выставлен, иначе основной скелет «Сегодня / Календарь / Настройки».
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FitAppTheme {
                FitAppRoot()
            }
        }
    }
}

@Composable
private fun FitAppRoot() {
    val app = LocalContext.current.applicationContext as FitAppApplication
    val onboardingCompleted by app.settingsStore.onboardingCompleted.collectAsState(initial = null)
    when (val completed = onboardingCompleted) {
        null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> FitAppNavHost(startOnboarding = !completed)
    }
}
