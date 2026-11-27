package ru.fitapp.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.fitapp.core.LogTags

@Database(
    entities = [
        ProfileEntity::class,
        DayEntity::class,
        MealEntity::class,
        ItemEntity::class,
        EditEntity::class,
    ],
    version = AppDatabase.DB_VERSION,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun dayDao(): DayDao
    abstract fun mealDao(): MealDao
    abstract fun editDao(): EditDao

    companion object {
        private const val TAG = LogTags.DATA
        private const val DB_NAME = "fitapp.db"

        /**
         * Версия схемы. 2 — правка массы компонента, пересчёт КБЖУ по названию,
         * отметка «С сахаром» (см. [Migrations.MIGRATION_1_2]).
         */
        const val DB_VERSION = 2

        fun create(context: Context): AppDatabase {
            val dbFile = context.applicationContext.getDatabasePath(DB_NAME)
            Log.i(TAG, "db: открытие ${dbFile.name} version=$DB_VERSION path=${dbFile.absolutePath}")
            return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .addMigrations(*Migrations.ALL)
                .addCallback(
                    object : RoomDatabase.Callback() {
                        // Свежая база вместо восстановленной — главная причина «пропала история».
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            Log.w(TAG, "db: база создана с нуля — истории прошлых дней нет")
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            Log.i(TAG, "db: база открыта version=${db.version}")
                        }
                    },
                )
                .build()
        }
    }
}