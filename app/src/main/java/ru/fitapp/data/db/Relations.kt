package ru.fitapp.data.db

import androidx.room.Embedded
import androidx.room.Relation

/** Карточка вместе с компонентами — для ленты дня и экрана карточки. */
data class MealWithItems(
    @Embedded val meal: MealEntity,
    @Relation(parentColumn = "id", entityColumn = "meal_id")
    val items: List<ItemEntity>,
)

/** Агрегат дня для календаря и тренда (§6, экран «Календарь»). */
data class DayStats(
    val date: String,
    val mealCount: Int,
    val doneCount: Int,
    val failedCount: Int,
    val totalKcal: Double,
)
