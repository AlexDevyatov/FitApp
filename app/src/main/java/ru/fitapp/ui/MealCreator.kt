package ru.fitapp.ui

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fitapp.FitAppApplication
import ru.fitapp.core.LogTags
import ru.fitapp.work.MealProcessor

/**
 * Общий путь «снимок → карточка в ленте» для камеры и галереи (§9, §10 PLAN):
 * сохранить фото через PhotoStore, создать meal(PENDING), поставить в очередь обработки.
 * Никаких диалогов после снимка — карточка сразу появляется в ленте со статусом.
 *
 * Логи (tag `FitAppPhoto`): размер исходника, сохранение файла, создание карточки
 * и постановка в очередь. По mealId эти записи стыкуются с логами AnalyzeWorker.
 */
class MealCreator(private val app: FitAppApplication) {

    /** Сохраняет JPEG и создаёт карточку PENDING; возвращает mealId. */
    suspend fun importJpeg(jpeg: ByteArray, takenAt: LocalDateTime = LocalDateTime.now()): String =
        withContext(Dispatchers.IO) {
            val mealId = UUID.randomUUID().toString()
            val startedAt = SystemClock.elapsedRealtime()
            Log.i(TAG, "import: старт meal=$mealId in_bytes=${jpeg.size} taken_at=$takenAt")

            val file = try {
                app.photoStore.save(mealId, jpeg)
            } catch (e: Exception) {
                // Снимок не сохранён — карточка не создаётся, пользователь увидит ошибку.
                Log.e(TAG, "import: снимок не сохранён meal=$mealId in_bytes=${jpeg.size}", e)
                throw e
            }

            app.mealRepository.createMeal(
                mealId = mealId,
                takenAt = takenAt,
                photoPath = file.absolutePath,
            )
            MealProcessor.enqueue(app, mealId)
            Log.i(
                TAG,
                "import: карточка создана и отдана в очередь meal=$mealId " +
                    "elapsed_ms=${SystemClock.elapsedRealtime() - startedAt}",
            )
            mealId
        }

    /** Читает изображение из Uri (галерея / Photo Picker) и передаёт в общий пайплайн. */
    suspend fun importFromUri(uri: Uri, takenAt: LocalDateTime = LocalDateTime.now()): String {
        val bytes = withContext(Dispatchers.IO) {
            runCatching {
                app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.onFailure {
                Log.e(TAG, "import: не удалось прочитать файл из галереи uri=$uri", it)
            }.getOrNull()
        } ?: run {
            Log.w(TAG, "import: галерея не дала байтов uri=$uri")
            throw IOException("Не удалось прочитать выбранное изображение")
        }
        Log.i(TAG, "import: из галереи прочитано bytes=${bytes.size} uri=$uri")
        return importJpeg(bytes, takenAt)
    }

    private companion object {
        const val TAG = LogTags.PHOTO
    }
}