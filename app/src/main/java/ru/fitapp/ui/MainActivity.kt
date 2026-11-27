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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.fitapp.FitAppApplication
import ru.fitapp.R
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
 *
 * Splash: системный экран (Theme.FitApp.Splash) держится, пока не прочитаны стартовые
 * настройки, но не дольше [SPLASH_TIMEOUT_MS]. Под ним сразу брендированный слой
 * Compose с тем же изумрудом и той же диафрагмой, поэтому перехода не видно.
 */
class MainActivity : ComponentActivity() {

    /** Стартовые данные прочитаны либо сработал таймаут — системный splash можно убирать. */
    private val startupReady = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen() обязан вызываться до super.onCreate: тема активна только сейчас.
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !startupReady.value }
        super.onCreate(savedInstanceState)
        Log.i(LogTags.UI, "ui: MainActivity.onCreate")
        enableEdgeToEdge()

        // Страховка: если DataStore почему-то молчит, splash не должен висеть вечно.
        lifecycleScope.launch {
            delay(SPLASH_TIMEOUT_MS)
            startupReady.value = true
        }

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
                FitAppRoot(onStartupDataReady = { startupReady.value = true })
            }
        }
    }

    private companion object {
        /** Сколько максимум держим системный splash, мс. */
        const val SPLASH_TIMEOUT_MS = 900L
    }
}

@Composable
private fun FitAppRoot(onStartupDataReady: () -> Unit) {
    val app = LocalContext.current.applicationContext as FitAppApplication
    val onboardingCompleted by app.settingsStore.onboardingCompleted.collectAsState(initial = null)
    LaunchedEffect(onboardingCompleted) {
        val completed = onboardingCompleted ?: return@LaunchedEffect
        // Данные пришли — системный splash больше не нужен, дальше рисует Compose.
        onStartupDataReady()
        Log.i(LogTags.UI, "ui: стартовый экран=${if (completed) "today" else "onboarding/key"}")
    }
    when (val completed = onboardingCompleted) {
        // Пока настройки не прочитаны, показываем брендированный слой, а не пустой спиннер.
        null -> FitAppSplashLayer()
        else -> {
            if (completed) {
                NotificationPermissionGate(app)
            }
            FitAppNavHost(startOnboarding = !completed)
        }
    }
}

/**
 * Брендированный слой splash: градиент изумруд→хвоя, те же световые пятна, что в макете
 * `splash/v8-snapmeal.png`, диафрагма и логотип. Полностью совпадает с системным splash
 * по фону и глифу, поэтому переход «системный экран → Compose» не читается как мигание.
 */
@Composable
private fun FitAppSplashLayer() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0E6B4F), Color(0xFF0A4A37)),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val limeCenter = Offset(size.width * 0.16f, size.height * 0.12f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFC8F26A).copy(alpha = 0.55f), Color.Transparent),
                    center = limeCenter,
                    radius = size.width * 0.85f,
                ),
                radius = size.width * 0.85f,
                center = limeCenter,
            )
            val peachCenter = Offset(size.width * 0.92f, size.height * 0.88f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFB77D).copy(alpha = 0.45f), Color.Transparent),
                    center = peachCenter,
                    radius = size.width * 0.70f,
                ),
                radius = size.width * 0.70f,
                center = peachCenter,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_splash_shutter),
                contentDescription = null,
                modifier = Modifier.size(144.dp),
            )
            Spacer(modifier = Modifier.height(36.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
            )
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