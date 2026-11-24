package ru.fitapp.data

import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.fitapp.core.LogTags
import ru.fitapp.core.logExcerpt
import ru.fitapp.data.db.DayDao
import ru.fitapp.data.db.DayEntity
import ru.fitapp.data.db.EditDao
import ru.fitapp.data.db.EditEntity
import ru.fitapp.data.db.ItemEntity
import ru.fitapp.data.db.MealDao
import ru.fitapp.data.db.MealEntity
import ru.fitapp.data.db.MealStatus
import ru.fitapp.data.db.MealWithItems
import ru.fitapp.domain.AnalyzedItem
import ru.fitapp.domain.FrameKind

/**
 * Тонкий слой над DAO карточек, компонентов и правок (§6 PLAN).
 *
 * Логи (tag `FitAppData`): каждое изменение, из-за которого карточка выглядит
 * иначе, чем ожидал пользователь — создание, статус, сохранённый разбор,
 * ошибка, правка и удаление. Чтения и Flow-запросы не логируются.
 */
class MealRepository(
    private val mealDao: MealDao,
    private val dayDao: DayDao,
    private val editDao: EditDao,
) {

    fun observeDay(date: LocalDate): Flow<List<MealWithItems>> =
        mealDao.observeDayWithItems(date.toString())

    fun observeMeal(mealId: String): Flow<MealWithItems?> =
        mealDao.observeMealWithItems(mealId)

    fun observeDayStats(from: LocalDate, to: LocalDate) =
        mealDao.observeDayStats(from.toString(), to.toString())

    fun observeMealsInRange(from: LocalDate, to: LocalDate) =
        mealDao.observeRange(from.toString(), to.toString())

    suspend fun getMeal(mealId: String): MealEntity? = mealDao.getById(mealId)

    /**
     * Создать карточку PENDING и запись дня; вернуть id карточки.
     * mealId можно задать извне: UI сначала сохраняет фото под этим id (§6: filesDir/meals/<mealId>.jpg).
     */
    suspend fun createMeal(
        mealId: String = UUID.randomUUID().toString(),
        takenAt: LocalDateTime = LocalDateTime.now(),
        photoPath: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val date = takenAt.toLocalDate().toString()
        dayDao.insertIfMissing(DayEntity(date = date))
        mealDao.upsert(
            MealEntity(
                id = mealId,
                date = date,
                takenAt = takenAt.atZone(zone).toInstant().toEpochMilli(),
                status = MealStatus.PENDING,
                photoPath = photoPath,
            )
        )
        Log.i(TAG, "meal=$mealId: создана карточка PENDING date=$date photo=${if (photoPath != null) "есть" else "нет"}")
        return mealId
    }

    suspend fun setStatus(mealId: String, status: String) {
        mealDao.setStatus(mealId, status)
        Log.i(TAG, "meal=$mealId: статус -> $status")
    }

    suspend fun incrementAttempts(mealId: String) = mealDao.incrementAttempts(mealId)

    suspend fun resetAttempts(mealId: String) {
        mealDao.resetAttempts(mealId)
        Log.i(TAG, "meal=$mealId: счётчик попыток сброшен")
    }

    /** Итог успешного разбора: компоненты + DONE. */
    suspend fun saveAnalysis(
        mealId: String,
        kind: FrameKind,
        confidence: Double,
        rawJson: String,
        model: String,
        items: List<AnalyzedItem>,
    ) {
        val entities = items.mapIndexed { index, item ->
            ItemEntity(
                id = UUID.randomUUID().toString(),
                mealId = mealId,
                ord = index,
                name = item.name,
                grams = item.grams,
                kcal = item.kcal,
                proteinG = item.proteinG,
                fatG = item.fatG,
                carbsG = item.carbsG,
                fiberG = item.fiberG,
                sugarG = item.sugarG,
                ultraProcessed = if (item.ultraProcessed) 1 else 0,
            )
        }
        mealDao.saveAnalysis(
            mealId = mealId,
            status = MealStatus.DONE,
            kind = kind.name.lowercase(),
            confidence = confidence,
            rawJson = rawJson,
            model = model,
            items = entities,
        )
        Log.i(
            TAG,
            "meal=$mealId: разбор сохранён kind=${kind.name.lowercase()} items=${entities.size} " +
                "kcal=${items.sumOf { it.kcal }.roundToInt()} model=$model confidence=$confidence",
        )
        if (entities.isEmpty()) {
            // Модель вернула пустой список: карточка DONE, но в ленте и отчёте она пустая.
            Log.w(TAG, "meal=$mealId: компонентов нет — карточка останется пустой")
        }
    }

    /** Карточка без еды: NOT_FOOD, фото остаётся (§4.3 PLAN). */
    suspend fun markNotFood(mealId: String, notes: String) {
        mealDao.setAnalysisResult(
            mealId = mealId,
            status = MealStatus.NOT_FOOD,
            kind = "not_food",
            confidence = null,
            rawJson = null,
            model = null,
        )
        if (notes.isNotBlank()) {
            mealDao.setError(mealId, MealStatus.NOT_FOOD, null, notes)
        }
        Log.i(TAG, "meal=$mealId: NOT_FOOD notes=${logExcerpt(notes, 120)}")
    }

    suspend fun markFailed(mealId: String, errorCode: String?, message: String) {
        mealDao.setError(mealId, MealStatus.FAILED, errorCode, message)
        Log.w(TAG, "meal=$mealId: FAILED code=$errorCode message=${logExcerpt(message, 160)}")
    }

    suspend fun markAuthError(mealId: String, message: String) {
        mealDao.setError(mealId, MealStatus.AUTH_ERROR, "invalid_api_key", message)
        Log.e(TAG, "meal=$mealId: AUTH_ERROR message=${logExcerpt(message, 160)}")
    }

    suspend fun deleteMeal(mealId: String) {
        mealDao.deleteMealCascade(mealId)
        Log.i(TAG, "meal=$mealId: карточка удалена вместе с компонентами")
    }

    /** Правка названия компонента с записью в edit и пометкой edited (§10 PLAN). */
    suspend fun renameItem(itemId: String, newName: String) {
        val item = mealDao.getItemById(itemId)
        if (item == null) {
            Log.w(TAG, "item=$itemId: переименование невозможно — компонента нет в базе")
            return
        }
        if (item.name == newName) {
            Log.i(TAG, "item=$itemId: название не изменилось, правка не записана")
            return
        }
        mealDao.upsertItem(item.copy(name = newName))
        val meal = mealDao.getById(item.mealId) ?: return
        recordEdit(item.mealId, meal.date, field = "item:$itemId:name", before = item.name, after = newName)
        Log.i(
            TAG,
            "meal=${item.mealId}: компонент $itemId переименован " +
                "«${logExcerpt(item.name, 60)}» -> «${logExcerpt(newName, 60)}»",
        )
    }

    suspend fun setMealKind(mealId: String, kind: FrameKind) {
        val meal = mealDao.getById(mealId)
        if (meal == null) {
            Log.w(TAG, "meal=$mealId: смена типа невозможна — карточки нет в базе")
            return
        }
        val newKind = kind.name.lowercase()
        if (meal.kind == newKind) {
            Log.i(TAG, "meal=$mealId: тип кадра уже $newKind")
            return
        }
        recordEdit(mealId, meal.date, field = "kind", before = meal.kind, after = newKind)
        mealDao.setKind(mealId, newKind)
        Log.i(TAG, "meal=$mealId: тип кадра ${meal.kind} -> $newKind")
    }

    /** Удалить компонент карточки с записью правки. */
    suspend fun deleteItem(itemId: String) {
        val item = mealDao.getItemById(itemId)
        if (item == null) {
            Log.w(TAG, "item=$itemId: удаление невозможно — компонента нет в базе")
            return
        }
        mealDao.deleteItem(itemId)
        val meal = mealDao.getById(item.mealId) ?: return
        recordEdit(item.mealId, meal.date, field = "item:$itemId:deleted", before = item.name, after = null)
        Log.i(TAG, "meal=${item.mealId}: компонент $itemId «${logExcerpt(item.name, 60)}» удалён")
    }

    private suspend fun recordEdit(mealId: String, date: String, field: String, before: String?, after: String?) {
        editDao.insert(
            EditEntity(
                id = UUID.randomUUID().toString(),
                mealId = mealId,
                field = field,
                before = before,
                after = after,
                at = System.currentTimeMillis(),
            )
        )
        mealDao.markEdited(mealId)
        dayDao.markHandEdited(date)
        Log.i(TAG, "meal=$mealId: правка записана field=$field date=$date")
    }

    fun observeEditCount(): Flow<Int> = editDao.observeTotalCount()

    /** Локальная метрика правок для экрана «Статистика теста» (§9, §15 PLAN). */
    fun observeMealStats(): Flow<MealStats> = combine(
        mealDao.observeMealCount(),
        mealDao.observeEditedMealCount(),
        editDao.observeTotalCount(),
    ) { total, edited, edits -> MealStats(totalMeals = total, editedMeals = edited, totalEdits = edits) }

    private companion object {
        const val TAG = LogTags.DATA
    }
}

/** Счётчики для локальной статистики теста (без отправки куда-либо). */
data class MealStats(
    val totalMeals: Int,
    val editedMeals: Int,
    val totalEdits: Int,
)