package ru.fitapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    /** Единственная строка анкеты: id всегда 1 (CHECK из §6PLAN обеспечивается кодом). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface DayDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(day: DayEntity)

    @Query("SELECT * FROM day WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeRange(from: String, to: String): Flow<List<DayEntity>>

    @Query("SELECT * FROM day WHERE date = :date")
    suspend fun get(date: String): DayEntity?

    @Query("UPDATE day SET hand_edited = 1 WHERE date = :date")
    suspend fun markHandEdited(date: String)
}

@Dao
interface MealDao {
    // --- лента дня ---
    @Transaction
    @Query("SELECT * FROM meal WHERE date = :date ORDER BY taken_at ASC")
    fun observeDayWithItems(date: String): Flow<List<MealWithItems>>

    @Transaction
    @Query("SELECT * FROM meal WHERE id = :mealId")
    fun observeMealWithItems(mealId: String): Flow<MealWithItems?>

    // --- диапазон дат: календарь и тренд ---
    @Query("SELECT * FROM meal WHERE date BETWEEN :from AND :to ORDER BY date ASC, taken_at ASC")
    fun observeRange(from: String, to: String): Flow<List<MealEntity>>

    @Query(
        """
        SELECT m.date AS date,
               COUNT(*) AS mealCount,
               SUM(CASE WHEN m.status = 'DONE' THEN 1 ELSE 0 END) AS doneCount,
               SUM(CASE WHEN m.status = 'FAILED' THEN 1 ELSE 0 END) AS failedCount,
               COALESCE((SELECT SUM(i.kcal) FROM item i
                          JOIN meal m2 ON i.meal_id = m2.id
                         WHERE m2.date = m.date AND m2.status = 'DONE'), 0) AS totalKcal
        FROM meal m
        WHERE m.date BETWEEN :from AND :to
        GROUP BY m.date
        ORDER BY m.date ASC
        """
    )
    fun observeDayStats(from: String, to: String): Flow<List<DayStats>>

    @Query("SELECT COUNT(*) FROM meal WHERE date = :date AND status = 'FAILED'")
    fun observeFailedCount(date: String): Flow<Int>

    // --- локальная статистика теста (§9, экран «Настройки») ---
    @Query("SELECT COUNT(*) FROM meal")
    fun observeMealCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM meal WHERE edited = 1")
    fun observeEditedMealCount(): Flow<Int>

    // --- карточка ---
    @Query("SELECT * FROM meal WHERE id = :mealId")
    suspend fun getById(mealId: String): MealEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meal: MealEntity)

    @Query("DELETE FROM meal WHERE id = :mealId")
    suspend fun delete(mealId: String)

    @Query("UPDATE meal SET status = :status WHERE id = :mealId")
    suspend fun setStatus(mealId: String, status: String)

    @Query("UPDATE meal SET attempts = attempts + 1 WHERE id = :mealId")
    suspend fun incrementAttempts(mealId: String)

    @Query("UPDATE meal SET attempts = 0 WHERE id = :mealId")
    suspend fun resetAttempts(mealId: String)

    @Query(
        """
        UPDATE meal
           SET kind = :kind, status = :status, confidence = :confidence,
               raw_json = :rawJson, model = :model,
               error_code = NULL, error_message = NULL
         WHERE id = :mealId
        """
    )
    suspend fun setAnalysisResult(
        mealId: String,
        status: String,
        kind: String?,
        confidence: Double?,
        rawJson: String?,
        model: String?,
    )

    @Query(
        """
        UPDATE meal
           SET status = :status, error_code = :errorCode, error_message = :errorMessage
         WHERE id = :mealId
        """
    )
    suspend fun setError(mealId: String, status: String, errorCode: String?, errorMessage: String?)

    /** Правка названия/типа: помечает edited у карточки (§10 плана правок). */
    @Query("UPDATE meal SET edited = 1 WHERE id = :mealId")
    suspend fun markEdited(mealId: String)

    @Query("UPDATE meal SET kind = :kind WHERE id = :mealId")
    suspend fun setKind(mealId: String, kind: String)

    /** Отметка «С сахаром»: 1 — в текущие числа компонента добавлена порция сахара. */
    @Query("UPDATE meal SET sugar_added = :added WHERE id = :mealId")
    suspend fun setSugarAdded(mealId: String, added: Int)

    // --- компоненты ---
    @Query("SELECT * FROM item WHERE meal_id = :mealId ORDER BY ord ASC")
    fun observeItems(mealId: String): Flow<List<ItemEntity>>

    /** Состояние пересчёта КБЖУ по новому названию: null — успех или пересчёт не нужен. */
    @Query("UPDATE item SET recalc_state = :state, recalc_error = :error WHERE id = :itemId")
    suspend fun setItemRecalcState(itemId: String, state: String?, error: String?)

    @Query("SELECT * FROM item WHERE id = :itemId")
    suspend fun getItemById(itemId: String): ItemEntity?

    @Query("SELECT * FROM item WHERE meal_id = :mealId ORDER BY ord ASC")
    suspend fun getItems(mealId: String): List<ItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: ItemEntity)

    @Query("DELETE FROM item WHERE meal_id = :mealId")
    suspend fun deleteItems(mealId: String)

    @Query("DELETE FROM item WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    /** Сохранить разбор: заменить компоненты и перевести карточку в итоговый статус. */
    @Transaction
    suspend fun saveAnalysis(
        mealId: String,
        status: String,
        kind: String?,
        confidence: Double?,
        rawJson: String?,
        model: String,
        items: List<ItemEntity>,
    ) {
        deleteItems(mealId)
        setAnalysisResult(mealId, status, kind, confidence, rawJson, model)
        if (items.isNotEmpty()) {
            items.forEach { upsertItem(it) }
        }
    }

    /** Удалить карточку вместе с компонентами и правками. */
    @Transaction
    suspend fun deleteMealCascade(mealId: String) {
        deleteItems(mealId)
        delete(mealId)
    }
}

@Dao
interface EditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(edit: EditEntity)

    /** Локальный счётчик правок (§15: метрика читается только на устройстве). */
    @Query("SELECT COUNT(*) FROM edit")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM edit WHERE meal_id = :mealId")
    fun observeCountForMeal(mealId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM edit")
    suspend fun totalCount(): Int

    /** График правок по дням: последние семь дней с правками. */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', at / 1000, 'unixepoch', 'localtime') AS day,
               COUNT(*) AS count
        FROM edit
        GROUP BY day
        ORDER BY day DESC
        LIMIT 7
        """
    )
    fun observeEditDays(): Flow<List<EditDayCount>>

    /** Последние правки: что и на что исправляли. */
    @Query("SELECT field, before, after, at FROM edit ORDER BY at DESC LIMIT 5")
    fun observeRecentEdits(): Flow<List<RecentEdit>>
}
