package ru.fitapp.data.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt
import ru.fitapp.core.LogTags

/**
 * Хранение фото приёмов пищи (§3, §6 PLAN):
 * filesDir/meals/<mealId>.jpg, JPEG, длинная сторона ≤ 1024 px, качество 80.
 * В БД хранится только путь.
 *
 * Логи (tag `FitAppPhoto`): размеры и байты до/после сжатия, причина, если сжатие
 * пропущено, и отказ файловой операции. Сами байты снимка не логируются никогда.
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
        if (!mealsDir.exists() && !mealsDir.mkdirs()) {
            // Не падаем здесь: запись ниже бросит IOException с внятной причиной.
            Log.e(TAG, "photo=$mealId: каталог снимков недоступен path=${mealsDir.absolutePath}")
        }
        val target = fileFor(mealId)
        val compressed = compress(mealId, jpeg)
        try {
            target.outputStream().use { it.write(compressed) }
        } catch (e: IOException) {
            Log.e(TAG, "photo=$mealId: запись ${target.name} не удалась bytes=${compressed.size}", e)
            throw e
        }
        Log.i(
            TAG,
            "photo=$mealId: сохранено ${mealsDir.name}/${target.name} " +
                "in_bytes=${jpeg.size} out_bytes=${target.length()}",
        )
        return target
    }

    fun read(mealId: String): ByteArray? {
        val file = fileFor(mealId)
        if (!file.exists()) {
            Log.w(TAG, "photo=$mealId: файла нет (${mealsDir.name}/${file.name})")
            return null
        }
        return try {
            file.readBytes()
        } catch (e: IOException) {
            Log.e(TAG, "photo=$mealId: чтение ${file.name} не удалось", e)
            null
        }
    }

    fun delete(mealId: String): Boolean {
        val file = fileFor(mealId)
        val deleted = file.delete()
        if (deleted) {
            Log.i(TAG, "photo=$mealId: удалено ${file.name}")
        } else {
            Log.w(TAG, "photo=$mealId: удалять нечего (${file.name})")
        }
        return deleted
    }

    private fun compress(mealId: String, jpeg: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            // Не удалось прочитать размеры — сохраняем исходник без пережатия.
            Log.w(TAG, "photo=$mealId: размеры JPEG не читаются, сохранён исходник bytes=${jpeg.size}")
            return jpeg
        }

        val longSide = max(bounds.outWidth, bounds.outHeight)
        val scale = (MAX_LONG_SIDE.toDouble() / longSide).coerceAtMost(1.0)

        val sampleOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(scale)
        }
        var bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, sampleOptions)
        if (bitmap == null) {
            Log.w(TAG, "photo=$mealId: декодирование JPEG не удалось, сохранён исходник bytes=${jpeg.size}")
            return jpeg
        }

        val targetWidth = (bounds.outWidth * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bounds.outHeight * scale).roundToInt().coerceAtLeast(1)
        if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
            val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }

        val out = ByteArrayOutputStream()
        val encoded = bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        val outWidth = bitmap.width
        val outHeight = bitmap.height
        bitmap.recycle()
        if (!encoded) {
            Log.w(TAG, "photo=$mealId: JPEG-кодек вернул false, байты могут быть пустыми")
        }
        val result = out.toByteArray()
        Log.i(
            TAG,
            "photo=$mealId: сжатие ${bounds.outWidth}x${bounds.outHeight} -> ${outWidth}x${outHeight} " +
                "quality=$QUALITY in_bytes=${jpeg.size} out_bytes=${result.size}",
        )
        return result
    }

    /** Степень двойки для inSampleSize, чтобы декодировать ближе к целевому масштабу. */
    private fun sampleSizeFor(scale: Double): Int {
        var sample = 1
        while (sample * 2 <= 1.0 / scale) sample *= 2
        return sample
    }

    companion object {
        private const val TAG = LogTags.PHOTO

        const val MAX_LONG_SIDE = 1024
        const val QUALITY = 80
    }
}