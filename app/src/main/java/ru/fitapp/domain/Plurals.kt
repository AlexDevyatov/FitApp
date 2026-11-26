package ru.fitapp.domain

/**
 * Русское склонение после числа: 1 приём, 2 приёма, 5 приёмов.
 * Вынесено отдельно, потому что и советы, и текст отчёта пишут «N приёмов».
 */
fun pluralRu(count: Int, one: String, few: String, many: String): String {
    val tail = count % 100
    val word = when {
        tail in 11..14 -> many
        count % 10 == 1 -> one
        count % 10 in 2..4 -> few
        else -> many
    }
    return "$count $word"
}
