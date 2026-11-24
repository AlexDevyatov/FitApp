package ru.fitapp.core

/**
 * Теги logcat для критических путей приложения.
 *
 * Идея: инцидент разбирается фильтром по области, а не чтением всего logcat.
 * `adb logcat -s FitAppAI FitAppWorker`        — разбор фото и очередь;
 * `adb logcat -s FitAppPhoto FitAppData`       — снимок и запись в базу;
 * `adb logcat -s FitAppKey FitAppUI`           — ключ и действия пользователя;
 * `adb logcat -s FitAppReport FitAppPush`      — отчёт и вечерний пуш.
 *
 * Уровни: I — нормальный ход событий; W — подозрительное, но не смертельное
 * (пропуск, повтор, fallback); E — операция не выполнена.
 */
object LogTags {
    /** Жизненный цикл процесса и необработанные исключения. */
    const val APP = "FitAppApp"

    /** Действия пользователя и состояния экранов (ViewModel-и, съёмка). */
    const val UI = "FitAppUI"

    /** Room и репозитории: что именно записано в базу. */
    const val DATA = "FitAppData"

    /** Файлы снимков: сжатие, запись, чтение, удаление. */
    const val PHOTO = "FitAppPhoto"

    /** API-ключ: наличие в хранилище и результат проверки провайдером. */
    const val KEY = "FitAppKey"

    /** Запросы к модели: разбор кадра и текст отчёта. */
    const val AI = "FitAppAI"

    /** Очередь WorkManager: постановка, попытки, исход. */
    const val WORKER = "FitAppWorker"

    /** Расчёт и формулировка дневного отчёта. */
    const val REPORT = "FitAppReport"

    /** Вечерний пуш: планирование, срабатывание, показ уведомления. */
    const val PUSH = "FitAppPush"
}

/** Длина выдержки по умолчанию: хватает, чтобы увидеть место сбоя. */
private const val DEFAULT_EXCERPT_LIMIT = 240

/**
 * Одна строка без переносов: сообщения провайдера и stack trace иначе
 * растягиваются на экраны logcat и теряют связь с соседними записями.
 */
fun logExcerpt(text: String, limit: Int = DEFAULT_EXCERPT_LIMIT): String {
    val oneLine = text.replace('\n', ' ').replace('\r', ' ').trim()
    return if (oneLine.length <= limit) oneLine else oneLine.take(limit) + "…"
}

/** Короткое описание исключения для строки лога: тип и сообщение. */
fun Throwable.describe(): String = "${javaClass.simpleName}: ${logExcerpt(message.orEmpty(), 200)}"