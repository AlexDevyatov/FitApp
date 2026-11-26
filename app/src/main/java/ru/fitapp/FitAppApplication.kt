package ru.fitapp

import android.app.Application
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.fitapp.ai.DishAnalyzer
import ru.fitapp.ai.QwenAnalyzer
import ru.fitapp.ai.ReportTextGenerator
import ru.fitapp.core.LogTags
import ru.fitapp.data.MealRepository
import ru.fitapp.data.ProfileRepository
import ru.fitapp.data.db.AppDatabase
import ru.fitapp.data.files.PhotoStore
import ru.fitapp.data.prefs.ApiKeyStore
import ru.fitapp.data.prefs.SettingsStore
import ru.fitapp.work.EveningReportScheduler
import ru.fitapp.work.ReportNotifier

/**
 * Ручная DI-композиция (§5 PLAN): все сервисы — ленивые singleton-ы.
 * MockAnalyzer подставляется только в тестах (инструментальный тест может
 * переопределить [analyzerOverride]).
 *
 * Логи (tag `FitAppApp`): запуск процесса, выбранная реализация анализатора
 * и необработанные исключения — то, что иначе видно только как «приложение закрылось».
 */
class FitAppApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.create(this) }

    val keyStore: ApiKeyStore by lazy { ApiKeyStore(this) }

    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    val photoStore: PhotoStore by lazy { PhotoStore(filesDir) }

    val profileRepository: ProfileRepository by lazy { ProfileRepository(database.profileDao()) }

    val mealRepository: MealRepository by lazy {
        MealRepository(database.mealDao(), database.dayDao(), database.editDao())
    }

    /** Ключ читается из KeyStore при каждом запросе: смена ключа не требует пересоздания клиента. */
    private val apiKeyProvider: () -> String? = { keyStore.read() }

    /** Тестовая подмена анализатора; в проде всегда null. */
    @Volatile
    var analyzerOverride: DishAnalyzer? = null

    /** Фоновые задачи процесса: планирование пуша при каждом старте. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val analyzer: DishAnalyzer by lazy {
        val implementation = analyzerOverride ?: QwenAnalyzer(apiKeyProvider)
        // Видно, если в сборку попал MockAnalyzer: модель тогда не вызывается вовсе.
        Log.i(
            LogTags.AI,
            "analyzer: используется ${implementation::class.java.simpleName}, model=${QwenAnalyzer.MODEL}",
        )
        implementation
    }

    val reportTextGenerator: ReportTextGenerator by lazy { ReportTextGenerator(apiKeyProvider) }

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        Log.i(LogTags.APP, "app: процесс запущен")
        ReportNotifier.ensureChannel(this)
        // Раньше пуш ставился только при изменении настроек: с настройками по умолчанию
        // (21:00, включён) работа не создавалась ни разу — уведомление не приходило.
        appScope.launch {
            runCatching { EveningReportScheduler.ensureScheduled(this@FitAppApplication) }
                .onFailure { Log.e(LogTags.PUSH, "push: автопланирование при старте упало", it) }
        }
    }

    /**
     * Пишет необработанное исключение в logcat под тегом FitAppApp до того,
     * как процесс будет убит. Поведение системы не меняется: обработчик
     * по умолчанию вызывается следом.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(LogTags.APP, "app: НЕОБРАБОТАННОЕ ИСКЛЮЧЕНИЕ thread=${thread.name}", throwable)
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
            }
        }
    }
}
