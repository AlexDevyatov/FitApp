package ru.fitapp.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.fitapp.FitAppApplication
import ru.fitapp.core.LogTags
import ru.fitapp.data.prefs.SettingsStore
import ru.fitapp.ui.theme.FitAppTheme
import ru.fitapp.work.EveningReportScheduler
import ru.fitapp.work.ReportNotifier

/**
 * Точка входа UI (§9 PLAN): старт — онбординг, если SettingsStore.onboardingCompleted
 * ещё не выставлен, иначе основной скелет «Сегодня / Календарь / Настройки».
 *
 * После онбординга здесь же спрашивается разрешение POST_NOTIFICATIONS: пуш включён
 * по умолчанию, а без разрешения (Android 13+) уведомление молча отбрасывается системой.
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
        else -> {
            if (completed) {
                NotificationPermissionGate(app)
            }
            FitAppNavHost(startOnboarding = !completed)
        }
    }
}

/**
 * Один запрос разрешения на уведомления после онбординга.
 * Повторно не спрашиваем: после отказа путь один — экран настроек и системные настройки.
 */
@Composable
private fun NotificationPermissionGate(app: FitAppApplication) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pushEnabled by app.settingsStore.pushEnabled.collectAsState(initial = true)
    var requestedNow by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        Log.i(LogTags.PUSH, "push: разрешение на уведомления при старте granted=$granted")
        if (granted) {
            // Разрешение выдали — работа точно есть и сможет показать отчёт.
            scope.launch { EveningReportScheduler.ensureScheduled(context) }
        } else {
            Log.w(LogTags.PUSH, "push: уведомления запрещены — вечерний отчёт не покажется")
        }
    }

    LaunchedEffect(pushEnabled) {
        if (!pushEnabled || requestedNow) return@LaunchedEffect
        if (ReportNotifier.canPostNotifications(context)) return@LaunchedEffect
        if (app.settingsStore.notificationAsked.first()) return@LaunchedEffect
        requestedNow = true
        app.settingsStore.setNotificationAsked(true)
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
