package ru.fitapp

import android.app.Application
import ru.fitapp.ai.DishAnalyzer
import ru.fitapp.ai.QwenAnalyzer
import ru.fitapp.ai.ReportTextGenerator
import ru.fitapp.data.MealRepository
import ru.fitapp.data.ProfileRepository
import ru.fitapp.data.db.AppDatabase
import ru.fitapp.data.files.PhotoStore
import ru.fitapp.data.prefs.ApiKeyStore
import ru.fitapp.data.prefs.SettingsStore
import ru.fitapp.work.ReportNotifier

/**
 * Ручная DI-композиция (§5 PLAN): все сервисы — ленивые singleton-ы.
 * MockAnalyzer подставляется только в тестах (инструментальный тест может
 * переопределить [analyzerOverride]).
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

    val analyzer: DishAnalyzer by lazy {
        analyzerOverride ?: QwenAnalyzer(apiKeyProvider)
    }

    val reportTextGenerator: ReportTextGenerator by lazy { ReportTextGenerator(apiKeyProvider) }

    override fun onCreate() {
        super.onCreate()
        ReportNotifier.ensureChannel(this)
    }
}
