package ru.fitapp.ui.common

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import ru.fitapp.R

private val ruLocale: Locale = Locale.forLanguageTag("ru")
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFullFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", ruLocale)
private val monthTitleFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", ruLocale)

fun formatTimeOfDay(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter)

fun formatDateFull(date: LocalDate): String = date.format(dateFullFormatter)

fun formatMonthTitle(month: YearMonth): String =
    month.format(monthTitleFormatter).replaceFirstChar { it.uppercase(ruLocale) }

/** Ккал — целым числом. */
fun formatKcal(value: Double): String = value.roundToInt().toString()

/** Граммы: целое, если значение близко к целому, иначе один знак после запятой. */
fun formatGrams(value: Double): String {
    val rounded = value.roundToInt()
    return if (abs(value - rounded) < 0.05) rounded.toString() else String.format(ruLocale, "%.1f", value)
}

/** Промежуток в минутах как «6 ч 20 мин» / «45 мин». */
fun formatGap(context: Context, minutes: Long): String =
    if (minutes >= 60) {
        context.getString(R.string.report_gap_hours, minutes / 60, minutes % 60)
    } else {
        context.getString(R.string.report_gap_minutes, minutes)
    }
