package ru.fitapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Статусы карточки приёма пищи (§6 PLAN). */
object MealStatus {
    const val PENDING = "PENDING"
    const val RUNNING = "RUNNING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
    const val AUTH_ERROR = "AUTH_ERROR"
    const val NOT_FOOD = "NOT_FOOD"
}

/**
 * Состояние пересчёта КБЖУ по новому названию компонента.
 * null в базе — пересчёт не нужен или уже прошёл успешно.
 */
object ItemRecalcState {
    const val RUNNING = "RUNNING"
    const val FAILED = "FAILED"
}

/** Анкета и цели. Всегда одна строка с id = 1 (§6 PLAN). */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "sex") val sex: String,
    @ColumnInfo(name = "age") val age: Int,
    @ColumnInfo(name = "height_cm") val heightCm: Int,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "activity") val activity: Double,
    @ColumnInfo(name = "deficit_pct") val deficitPct: Int,
    @ColumnInfo(name = "target_kcal") val targetKcal: Int,
    @ColumnInfo(name = "target_protein_g") val targetProteinG: Int,
    @ColumnInfo(name = "target_fat_g") val targetFatG: Int,
    @ColumnInfo(name = "target_carbs_g") val targetCarbsG: Int,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

/** День дневника. hand_edited — день, где юзер правил карточки (§6 PLAN). */
@Entity(tableName = "day")
data class DayEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String, // yyyy-MM-dd
    @ColumnInfo(name = "note") val note: String = "",
    @ColumnInfo(name = "hand_edited") val handEdited: Int = 0,
)

/** Карточка приёма пищи (§6 PLAN). */
@Entity(
    tableName = "meal",
    indices = [Index(value = ["date", "taken_at"])],
)
data class MealEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "date") val date: String, // yyyy-MM-dd
    @ColumnInfo(name = "taken_at") val takenAt: Long, // epoch millis
    @ColumnInfo(name = "kind") val kind: String? = null, // dish/plate/label
    @ColumnInfo(name = "status") val status: String = MealStatus.PENDING,
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
    @ColumnInfo(name = "confidence") val confidence: Double? = null,
    @ColumnInfo(name = "raw_json") val rawJson: String? = null,
    @ColumnInfo(name = "error_code") val errorCode: String? = null,
    @ColumnInfo(name = "error_message") val errorMessage: String? = null,
    @ColumnInfo(name = "model") val model: String? = null,
    @ColumnInfo(name = "attempts") val attempts: Int = 0,
    @ColumnInfo(name = "edited") val edited: Int = 0,
    /** Отметка «С сахаром» для напитка: 1 — к КБЖУ добавлена порция сахара. */
    @ColumnInfo(name = "sugar_added", defaultValue = "0") val sugarAdded: Int = 0,
)

/**
 * Компонент разбора карточки (§6 PLAN).
 *
 * `base_*` — неизменяемый результат модели: от него считаются все правки (масса,
 * отметка сахара, новое название), поэтому серия правок не накапливает погрешность.
 * `kcal`/`protein_g`/… — текущие числа компонента, по ним считаются суммы дня и отчёт.
 */
@Entity(
    tableName = "item",
    indices = [Index(value = ["meal_id"])],
)
data class ItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "meal_id") val mealId: String,
    @ColumnInfo(name = "ord") val ord: Int,
    @ColumnInfo(name = "name") val name: String,
    /** Текущая масса порции; правится пользователем. */
    @ColumnInfo(name = "grams") val grams: Double? = null,
    @ColumnInfo(name = "kcal") val kcal: Double,
    @ColumnInfo(name = "protein_g") val proteinG: Double,
    @ColumnInfo(name = "fat_g") val fatG: Double,
    @ColumnInfo(name = "carbs_g") val carbsG: Double,
    @ColumnInfo(name = "fiber_g") val fiberG: Double,
    @ColumnInfo(name = "sugar_g") val sugarG: Double,
    @ColumnInfo(name = "ultra_processed") val ultraProcessed: Int, // 0/1
    /** Масса из разбора модели; null — модель вес не оценила, массу править нельзя. */
    @ColumnInfo(name = "base_grams") val baseGrams: Double? = null,
    @ColumnInfo(name = "base_kcal", defaultValue = "0") val baseKcal: Double = 0.0,
    @ColumnInfo(name = "base_protein_g", defaultValue = "0") val baseProteinG: Double = 0.0,
    @ColumnInfo(name = "base_fat_g", defaultValue = "0") val baseFatG: Double = 0.0,
    @ColumnInfo(name = "base_carbs_g", defaultValue = "0") val baseCarbsG: Double = 0.0,
    @ColumnInfo(name = "base_fiber_g", defaultValue = "0") val baseFiberG: Double = 0.0,
    @ColumnInfo(name = "base_sugar_g", defaultValue = "0") val baseSugarG: Double = 0.0,
    /** ItemRecalcState.RUNNING/FAILED, null — пересчёт по названию не нужен. */
    @ColumnInfo(name = "recalc_state") val recalcState: String? = null,
    @ColumnInfo(name = "recalc_error") val recalcError: String? = null,
)

/** Правка карточки — источник локальной метрики (§6 PLAN). */
@Entity(
    tableName = "edit",
    indices = [Index(value = ["meal_id"])],
)
data class EditEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "meal_id") val mealId: String,
    @ColumnInfo(name = "field") val field: String,
    @ColumnInfo(name = "before") val before: String?,
    @ColumnInfo(name = "after") val after: String?,
    @ColumnInfo(name = "at") val at: Long,
)
