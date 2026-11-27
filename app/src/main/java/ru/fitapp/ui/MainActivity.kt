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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
 * Splash: системный экран (Theme.FitApp.Splash) не держим вовсе — он умеет показать только
 * ровный цвет, поэтому уходит вместе с первым кадром. Дальше секунду живёт настоящий сплэш
 * из макета ([SPLASH_MIN_VISIBLE_MS]), его рисует Compose из двух векторов.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen() обязан вызываться до super.onCreate: тема активна только сейчас.
        installSplashScreen()
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

/** Сколько показываем сплэш с макета, мс. */
private const val SPLASH_MIN_VISIBLE_MS = 1000L

@Composable
private fun FitAppRoot() {
    val app = LocalContext.current.applicationContext as FitAppApplication
    val onboardingCompleted by app.settingsStore.onboardingCompleted.collectAsState(initial = null)

    // Отсчёт начинается не от композиции, а от первого кадра: холодный старт занимает ~750 мс,
// и без этого от секунды оставалось бы 250 мс — сплэш успевал мигнуть.
    var splashMinElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        delay(SPLASH_MIN_VISIBLE_MS)
        splashMinElapsed = true
    }

    LaunchedEffect(onboardingCompleted) {
        val completed = onboardingCompleted ?: return@LaunchedEffect
        Log.i(LogTags.UI, "ui: стартовый экран=${if (completed) "today" else "onboarding/key"}")
    }

    val completed = onboardingCompleted
    val splashVisible = completed == null || !splashMinElapsed
    val splashAlpha by animateFloatAsState(
        targetValue = if (splashVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "splashAlpha",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (completed != null) {
            // Диалог разрешения не должен всплывать поверх сплэша, поэтому гейт — под ним.
            if (!splashVisible) {
                NotificationPermissionGate(app)
            }
            FitAppNavHost(startOnboarding = !completed)
        }
        if (splashAlpha > 0f) {
            Box(modifier = Modifier.fillMaxSize().alpha(splashAlpha)) {
                FitAppSplashLayer()
            }
        }
    }
}

/**
 * Сплэш SnapMeal — два вектора, ни одного растра.
 *
 * Фон (`ic_splash_bg`) тянется на весь экран без сохранения пропорций: это градиент с мягкими
 * пятнами, любая растяжка в нём невидима. Логотип (`ic_splash_logo` — диафрагма и надписи
 * в кривых Manrope) масштабируется с сохранением пропорций и стоит по центру, поэтому круглая
 * диафрагма остаётся круглой и на 9:16, и на 9:20.
 */
@Composable
private fun FitAppSplashLayer() {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.ic_splash_bg),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(R.drawable.ic_splash_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
        )
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