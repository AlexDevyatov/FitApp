package ru.fitapp.ui

import android.net.Uri
import java.io.IOException
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fitapp.FitAppApplication
import ru.fitapp.work.MealProcessor

/**
 * Общий путь «снимок → карточка в ленте» для камеры и галереи (§9, §10 PLAN):
 * сохранить фото через PhotoStore, создать meal(PENDING), поставить в очередь обработки.
 * Никаких диалогов после снимка — карточка сразу появляется в ленте со статусом.
 */
class MealCreator(private val app: FitAppApplication) {

    /** Сохраняет JPEG и создаёт карточку PENDING; возвращает mealId. */
    suspend fun importJpeg(jpeg: ByteArray, takenAt: LocalDateTime = LocalDateTime.now()): String =
        withContext(Dispatchers.IO) {
            val mealId = UUID.randomUUID().toString()
            val file = app.photoStore.save(mealId, jpeg)
            app.mealRepository.createMeal(
                mealId = mealId,
                takenAt = takenAt,
                photoPath = file.absolutePath,
            )
            MealProcessor.enqueue(app, mealId)
            mealId
        }

    /** Читает изображение из Uri (галерея / Photo Picker) и передаёт в общий пайплайн. */
    suspend fun importFromUri(uri: Uri, takenAt: LocalDateTime = LocalDateTime.now()): String {
        val bytes = withContext(Dispatchers.IO) {
            runCatching {
                app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
        } ?: throw IOException("Не удалось прочитать выбранное изображение")
        return importJpeg(bytes, takenAt)
    }
}
