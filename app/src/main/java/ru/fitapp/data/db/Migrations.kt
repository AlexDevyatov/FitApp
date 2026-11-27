package ru.fitapp.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграции схемы. Данные пользователя — единственная копия дневника, поэтому
 * `fallbackToDestructiveMigration` в релизе не включается: каждая версия получает
 * явный путь из предыдущей.
 */
object Migrations {

    /**
     * v1 → v2: правка массы и пересчёт КБЖУ.
     *
     * - `meal.sugar_added` — отметка «С сахаром» для напитка;
     * - `item.base_*` — неизменяемые числа разбора, от которых считается масса и сахар.
     *   Для уже сохранённых компонентов базой становится их текущее значение,
     *   поэтому старые карточки выглядят ровно как раньше;
     * - `item.recalc_state/recalc_error` — состояние пересчёта по новому названию.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE meal ADD COLUMN sugar_added INTEGER NOT NULL DEFAULT 0")

            db.execSQL("ALTER TABLE item ADD COLUMN base_grams REAL")
            db.execSQL("ALTER TABLE item ADD COLUMN base_kcal REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE item ADD COLUMN base_protein_g REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE item ADD COLUMN base_fat_g REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE item ADD COLUMN base_carbs_g REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE item ADD COLUMN base_fiber_g REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE item ADD COLUMN base_sugar_g REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE item ADD COLUMN recalc_state TEXT")
            db.execSQL("ALTER TABLE item ADD COLUMN recalc_error TEXT")

            db.execSQL(
                """
                UPDATE item
                   SET base_grams = grams,
                       base_kcal = kcal,
                       base_protein_g = protein_g,
                       base_fat_g = fat_g,
                       base_carbs_g = carbs_g,
                       base_fiber_g = fiber_g,
                       base_sugar_g = sugar_g
                """.trimIndent(),
            )
        }
    }

    /** Полная цепочка миграций — подключается в [AppDatabase]. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}