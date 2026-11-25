package ru.fitapp.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ru.fitapp.FitAppApplication
import ru.fitapp.core.LogTags
import ru.fitapp.data.prefs.SettingsStore
import ru.fitapp.ui.theme.FitAppTheme

/**
 * Точка входа UI (§9 PLAN): старт — онбординг, если SettingsStore.onboardingCompleted
 * ещё не выставлен, иначе основной скелет «Сегодня / Календарь / Настройки».
 *
 * Логи (tag `FitAppUI`): какой экран открывается на старте. Отвечает на «меня снова
 * кинуло в онбординг» без догадок.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(LogTags.UI, "ui: MainActivity.onCreate")
        enableEdgeToEdge()
        setContent {
            val app = LocalContext.current.applicationContext as FitAppApplication
            val themeMode by app.settingsStore.darkThemeMode.collectAsState(initial = SettingsStore.THEME_MODE_SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                SettingsStore.THEME_MODE_LIGHT -> false
                SettingsStore.THEME_MODE_DARK -> true
                else -> systemDark
            }
            FitAppTheme(darkTheme = isDark) {
                FitAppRoot()
            }
        }
    }
}

@Composable
private fun FitAppRoot() {
    val app = LocalContext.current.applicationContext as FitAppApplication
    val onboardingCompleted by app.settingsStore.onboardingCompleted.collectAsState(initial = null)
    LaunchedEffect(onboardingCompleted) {
        val completed = onboardingCompleted ?: return@LaunchedEffect
        Log.i(LogTags.UI, "ui: стартовый экран=${if (completed) "today" else "onboarding/key"}")
    }
    when (val completed = onboardingCompleted) {
        null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> FitAppNavHost(startOnboarding = !completed)
    }
}