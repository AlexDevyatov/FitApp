package ru.fitapp.data.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Хранение фото приёмов пищи (§3, §6 PLAN):
 * filesDir/meals/<mealId>.jpg, JPEG, длинная сторона ≤ 1024 px, качество 80.
 * В БД хранится только путь.
 */
class PhotoStore(baseDir: File) {

    private val mealsDir: File = File(baseDir, "meals")

    fun fileFor(mealId: String): File = File(mealsDir, "$mealId.jpg")

    fun relativePath(mealId: String): String = "meals/$mealId.jpg"

    /**
     * Сжимает и сохраняет JPEG. Возвращает путь к файлу.
     * Если входящие данные уже подходят под ограничения — пересохраняет их как есть.
     */
    fun save(mealId: String, jpeg: ByteArray): File {
        mealsDir.mkdirs()
        val target = fileFor(mealId)
        val compressed = compress(jpeg)
        target.outputStream().use { it.write(compressed) }
        return target
    }

    fun read(mealId: String): ByteArray? {
        val file = fileFor(mealId)
        return if (file.exists()) file.readBytes() else null
    }

    fun delete(mealId: String): Boolean = fileFor(mealId).delete()

    private fun compress(jpeg: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            // Не удалось прочитать размеры — сохраняем исходник без пережатия.
            return jpeg
        }

        val longSide = max(bounds.outWidth, bounds.outHeight)
        val scale = (MAX_LONG_SIDE.toDouble() / longSide).coerceAtMost(1.0)

        val sampleOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(scale)
        }
        var bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, sampleOptions)
            ?: return jpeg

        val targetWidth = (bounds.outWidth * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bounds.outHeight * scale).roundToInt().coerceAtLeast(1)
        if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
            val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    /** Степень двойки для inSampleSize, чтобы декодировать ближе к целевому масштабу. */
    private fun sampleSizeFor(scale: Double): Int {
        var sample = 1
        while (sample * 2 <= 1.0 / scale) sample *= 2
        return sample
    }

    companion object {
        const val MAX_LONG_SIDE = 1024
        const val QUALITY = 80
    }
}
