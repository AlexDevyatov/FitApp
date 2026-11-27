package ru.fitapp.data

import android.util.Log
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ru.fitapp.core.LogTags
import ru.fitapp.core.logExcerpt
import ru.fitapp.data.db.DayDao
import ru.fitapp.data.db.DayEntity
import ru.fitapp.data.db.EditDao
import ru.fitapp.data.db.EditDayCount
import ru.fitapp.data.db.EditEntity
import ru.fitapp.data.db.ItemEntity
import ru.fitapp.data.db.MealDao
import ru.fitapp.data.db.MealEntity
import ru.fitapp.data.db.MealStatus
import ru.fitapp.data.db.MealWithItems
import ru.fitapp.data.db.RecentEdit
import ru.fitapp.domain.AnalyzedItem
import ru.fitapp.domain.DayHistory
import ru.fitapp.domain.FrameKind
import ru.fitapp.domain.Nutrition
import ru.fitapp.domain.PortionRecalc
import ru.fitapp.domain.dbValue

/**
 * Тонкий слой над DAO карточек, компонентов и правок (§6 PLAN).
 *
 * Правило пересчёта: `item.base_*` — неизменяемый ответ модели, `item.kcal`/… — текущие
 * числа. Масса и отметка «С сахаром» меняют только текущие числа, поэтому любую правку
 * можно отменить и от перестановки правок результат не зависит. Все суммы дня и отчёт
 * читают текущие числа, так что пересчёт виден везде сразу.
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

    /**
     * Контекст недели для отчёта: среднее по дням с готовыми карточками и вчерашний день.
     * Пустые дни и дни без DONE-карточек в среднее не попадают — иначе «отпуск» выглядел бы дефицитом.
     */
    fun observeHistory(date: LocalDate, days: Long = 7): Flow<DayHistory> =
        mealDao.observeDayStats(date.minusDays(days).toString(), date.minusDays(1).toString())
            .map { stats ->
                val withData = stats.filter { it.doneCount > 0 }
                val avg = if (withData.isEmpty()) {
                    null
                } else {
                    (withData.sumOf { it.totalKcal } / withData.size).roundToInt()
                }
                val prev = stats
                    .firstOrNull { it.date == date.minusDays(1).toString() && it.doneCount > 0 }
                    ?.totalKcal
                    ?.roundToInt()
                DayHistory(avgKcal = avg, daysWithData = withData.size, prevDayKcal = prev)
            }

    suspend fun getMeal(mealId: String): MealEntity? = mealDao.getById(mealId)

    suspend fun getItem(itemId: String): ItemEntity? = mealDao.getItemById(itemId)

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

    /** Итог успешного разбора: компоненты + DONE. Числа разбора становятся базой для правок. */
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
                baseGrams = item.grams,
                baseKcal = item.kcal,
                baseProteinG = item.proteinG,
                baseFatG = item.fatG,
                baseCarbsG = item.carbsG,
                baseFiberG = item.fiberG,
                baseSugarG = item.sugarG,
            )
        }
        mealDao.saveAnalysis(
            mealId = mealId,
            status = MealStatus.DONE,
            kind = kind.dbValue,
            confidence = confidence,
            rawJson = rawJson,
            model = model,
            items = entities,
        )
        Log.i(
            TAG,
            "meal=$mealId: разбор сохранён kind=${kind.dbValue} items=${entities.size} " +
                "kcal=${items.sumOf { it.kcal }.roundToInt()} model=$model confidence=$confidence",
        )
        if (entities.isEmpty()) {
            // Модель вернула пустой список: карточка DONE, но в ленте и отчёте она пустая.
            Log.w(TAG, "meal=$mealId: компонентов нет — карточка останется пустой")
        } else {
            // Новый разбор записывает базу; отметка «С сахаром» (если была) применяется заново.
            recomputeItems(mealId)
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

    /**
     * Правка компонента: название и масса. Обе величины пишутся сразу, текущие КБЖУ
     * пересчитываются от базы разбора. Название изменилось — вызывающий код запускает
     * пересчёт по названию (запрос к модели), масса — считается локально.
     */
    suspend fun updateItem(itemId: String, newName: String, newGrams: Double?): ItemUpdateResult {
        val item = mealDao.getItemById(itemId)
        if (item == null) {
            Log.w(TAG, "item=$itemId: правка невозможна — компонента нет в базе")
            return ItemUpdateResult()
        }
        val meal = mealDao.getById(item.mealId)
        if (meal == null) {
            Log.w(TAG, "item=$itemId: правка невозможна — карточки нет в базе")
            return ItemUpdateResult()
        }

        val name = newName.trim().ifEmpty { item.name }
        // Массу правим только если модель оценила исходный вес: без базы пересчёт невозможен.
        val grams = if (PortionRecalc.canEditGrams(item.baseGrams)) {
            newGrams ?: item.grams
        } else {
            item.grams
        }
        val nameChanged = name != item.name
        val gramsChanged = grams != item.grams

        if (!nameChanged && !gramsChanged) {
            Log.i(TAG, "item=$itemId: правка ничего не изменила, запись пропущена")
            return ItemUpdateResult(applied = true)
        }

        val targetId = sugarTargetId(item.mealId)
        mealDao.upsertItem(
            item.copy(name = name, grams = grams)
                .withEffective(sugarTarget = item.id == targetId, sugarAdded = meal.sugarAdded == 1),
        )
        if (nameChanged) {
            recordEdit(item.mealId, meal.date, field = "item:$itemId:name", before = item.name, after = name)
            Log.i(
                TAG,
                "meal=${item.mealId}: компонент $itemId переименован " +
                    "«${logExcerpt(item.name, 60)}» -> «${logExcerpt(name, 60)}»",
            )
        }
        if (gramsChanged) {
            recordEdit(
                item.mealId,
                meal.date,
                field = "item:$itemId:grams",
                before = PortionRecalc.gramsToInput(item.grams),
                after = PortionRecalc.gramsToInput(grams),
            )
            Log.i(
                TAG,
                "meal=${item.mealId}: компонент $itemId масса " +
                    "${PortionRecalc.gramsToInput(item.grams)} -> ${PortionRecalc.gramsToInput(grams)} г",
            )
        }
        return ItemUpdateResult(applied = true, nameChanged = nameChanged, gramsChanged = gramsChanged)
    }

    /** Отметка «С сахаром» для напитка: порция сахара добавляется в текущие числа. */
    suspend fun setSugarAdded(mealId: String, withSugar: Boolean) {
        val meal = mealDao.getById(mealId)
        if (meal == null) {
            Log.w(TAG, "meal=$mealId: отметка сахара невозможна — карточки нет в базе")
            return
        }
        val added = if (withSugar) 1 else 0
        if (meal.sugarAdded == added) {
            Log.i(TAG, "meal=$mealId: отметка сахара уже ${if (withSugar) "включена" else "выключена"}")
            return
        }
        mealDao.setSugarAdded(mealId, added)
        recordEdit(mealId, meal.date, field = "sugar", before = meal.sugarAdded.toString(), after = added.toString())
        recomputeItems(mealId)
        Log.i(
            TAG,
            "meal=$mealId: «с сахаром» -> ${withSugar} (+${PortionRecalc.ADDED_SUGAR_G} г сахара, " +
                "+${(PortionRecalc.ADDED_SUGAR_G * PortionRecalc.KCAL_PER_G_SUGAR).roundToInt()} ккал)",
        )
    }

    /**
     * Записать пересчёт КБЖУ по новому названию: числа модели становятся новой базой,
     * текущая масса пользователя сохраняется — итог считается от неё.
     */
    suspend fun applyItemEstimate(itemId: String, estimate: AnalyzedItem, model: String) {
        val item = mealDao.getItemById(itemId)
        if (item == null) {
            Log.w(TAG, "item=$itemId: пересчёт не сохранён — компонента нет в базе")
            return
        }
        val meal = mealDao.getById(item.mealId)
        if (meal == null) {
            Log.w(TAG, "item=$itemId: пересчёт не сохранён — карточки нет в базе")
            return
        }
        val baseGrams = estimate.grams?.takeIf { it > 0.0 } ?: item.baseGrams ?: item.grams
        val grams = item.grams ?: baseGrams
        val updated = item.copy(
            grams = grams,
            baseGrams = baseGrams,
            baseKcal = estimate.kcal,
            baseProteinG = estimate.proteinG,
            baseFatG = estimate.fatG,
            baseCarbsG = estimate.carbsG,
            baseFiberG = estimate.fiberG,
            baseSugarG = estimate.sugarG,
            recalcState = null,
            recalcError = null,
        )
        val targetId = sugarTargetId(item.mealId)
        val effective = updated.withEffective(
            sugarTarget = item.id == targetId,
            sugarAdded = meal.sugarAdded == 1,
        )
        mealDao.upsertItem(effective)
        Log.i(
            TAG,
            "meal=${item.mealId}: КБЖУ пересчитаны по названию item=$itemId model=$model " +
                "«${logExcerpt(item.name, 60)}» ${item.kcal.roundToInt()} -> ${effective.kcal.roundToInt()} ккал " +
                "база=(${baseGrams?.roundToInt()} г, ${estimate.kcal.roundToInt()} ккал)",
        )
    }

    suspend fun setItemRecalcState(itemId: String, state: String?, error: String?) {
        mealDao.setItemRecalcState(itemId, state, error)
    }

    /** Компонент, к которому добавляется сахар карточки: первый по порядку. */
    private suspend fun sugarTargetId(mealId: String): String? =
        mealDao.getItems(mealId).minByOrNull { it.ord }?.id

    /** Пересчитать текущие числа всех компонентов карточки от их базы. */
    private suspend fun recomputeItems(mealId: String) {
        val meal = mealDao.getById(mealId) ?: return
        val items = mealDao.getItems(mealId)
        val targetId = items.minByOrNull { it.ord }?.id
        items.forEach { item ->
            val updated = item.withEffective(
                sugarTarget = item.id == targetId,
                sugarAdded = meal.sugarAdded == 1,
            )
            if (updated != item) mealDao.upsertItem(updated)
        }
    }

    /** Текущие КБЖУ компонента: база модели × масса + сахар карточки (первому компоненту). */
    private fun ItemEntity.withEffective(sugarTarget: Boolean, sugarAdded: Boolean): ItemEntity {
        val base = Nutrition(
            kcal = baseKcal,
            proteinG = baseProteinG,
            fatG = baseFatG,
            carbsG = baseCarbsG,
            fiberG = baseFiberG,
            sugarG = baseSugarG,
        )
        val addedSugar = if (sugarAdded && sugarTarget) PortionRecalc.ADDED_SUGAR_G else 0.0
        val effective = PortionRecalc.effective(base, baseGrams, grams, addedSugar)
        return copy(
            kcal = effective.kcal,
            proteinG = effective.proteinG,
            fatG = effective.fatG,
            carbsG = effective.carbsG,
            fiberG = effective.fiberG,
            sugarG = effective.sugarG,
        )
    }

    suspend fun setMealKind(mealId: String, kind: FrameKind) {
        val meal = mealDao.getById(mealId)
        if (meal == null) {
            Log.w(TAG, "meal=$mealId: смена типа невозможна — карточки нет в базе")
            return
        }
        val newKind = kind.dbValue
        if (meal.kind == newKind) {
            Log.i(TAG, "meal=$mealId: тип кадра уже $newKind")
            return
        }
        recordEdit(mealId, meal.date, field = "kind", before = meal.kind, after = newKind)
        mealDao.setKind(mealId, newKind)
        Log.i(TAG, "meal=$mealId: тип кадра ${meal.kind} -> $newKind")
        // Напиток перестал быть напитком — отметка сахара больше не показывается,
        // поэтому её добавка снимается вместе с типом, а не висит невидимой.
        if (newKind != FrameKind.DRINK.dbValue && meal.sugarAdded == 1) {
            mealDao.setSugarAdded(mealId, 0)
            recomputeItems(mealId)
            Log.i(TAG, "meal=$mealId: тип не напиток — отметка «с сахаром» снята")
        }
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
        // Удалённый компонент мог быть тем, к которому добавлялся сахар: добавка переезжает
        // на первый оставшийся, чтобы суммарные КБЖУ не менялись скрытно.
        if (meal.sugarAdded == 1) recomputeItems(item.mealId)
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

    fun observeEditDays(): Flow<List<EditDayCount>> = editDao.observeEditDays()

    fun observeRecentEdits(): Flow<List<RecentEdit>> = editDao.observeRecentEdits()

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

/** Итог правки компонента: что изменилось и нужен ли пересчёт по названию. */
data class ItemUpdateResult(
    val applied: Boolean = false,
    val nameChanged: Boolean = false,
    val gramsChanged: Boolean = false,
) {
    /** Пересчёт по названию нужен только когда название реально изменилось. */
    val requiresReestimate: Boolean get() = applied && nameChanged
}

/** Счётчики для локальной статистики теста (без отправки куда-либо). */
data class MealStats(
    val totalMeals: Int,
    val editedMeals: Int,
    val totalEdits: Int,
)