package ru.fitapp.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import java.time.LocalDate
import java.time.YearMonth
import ru.fitapp.R
import ru.fitapp.data.db.DayStats
import ru.fitapp.domain.ReportCalc
import ru.fitapp.ui.FitAppBottomBar
import ru.fitapp.ui.common.SectionCard
import ru.fitapp.ui.common.formatMonthTitle
import ru.fitapp.ui.factoryOf
import ru.fitapp.ui.fitApp

/** Цвет «мало данных» — янтарный; «пусто» — нейральный серый. */
private val LittleDataAmber = Color(0xFFEF6C00)
private val EmptyDayGray = Color(0xFFBDBDBD)

private enum class DayFullness { FULL, LITTLE, EMPTY }

private fun fullnessOf(stats: DayStats?): DayFullness = when {
    stats == null || stats.mealCount == 0 -> DayFullness.EMPTY
    stats.doneCount >= ReportCalc.MIN_DONE_MEALS && stats.failedCount == 0 -> DayFullness.FULL
    else -> DayFullness.LITTLE
}

/**
 * Календарь (§9): собственная Compose-сетка месяца без внешних библиотек,
 * отметка полноты дня (полный / мало данных / пусто), тренд по ккал днями месяца.
 * Тап по дню — экран «День» с той же лентой и доступом к отчёту.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(navController: NavController, onOpenDay: (LocalDate) -> Unit) {
    val app = fitApp()
    val vm: CalendarViewModel = viewModel(factory = factoryOf { CalendarViewModel(app) })
    val month by vm.month.collectAsState()
    val stats by vm.stats.collectAsState()
    val targetKcal by vm.targetKcal.collectAsState()
    val weekdays = stringArrayResource(R.array.weekday_short)
    val today = remember { LocalDate.now() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.calendar_title)) })
        },
        bottomBar = { FitAppBottomBar(navController) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- Переключатель месяца ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = { vm.shiftMonth(-1) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        stringResource(R.string.cd_prev_month),
                    )
                }
                Text(
                    formatMonthTitle(month),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                IconButton(onClick = { vm.shiftMonth(1) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        stringResource(R.string.cd_next_month),
                    )
                }
            }

            // --- Сетка месяца (понедельник — первый день недели) ---
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdays.forEach { name ->
                    Text(
                        name,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
            val daysInMonth = month.lengthOfMonth()
            val weeks = (leadingBlanks + daysInMonth + 6) / 7
            repeat(weeks) { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val dayNumber = week * 7 + column - leadingBlanks + 1
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                            if (dayNumber in 1..daysInMonth) {
                                val date = month.atDay(dayNumber)
                                DayCell(
                                    dayNumber = dayNumber,
                                    fullness = fullnessOf(stats[date]),
                                    isToday = date == today,
                                    onClick = { onOpenDay(date) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }

            Legend()

            // --- Тренд по ккал днями выбранного месяца ---
            TrendCard(
                month = month,
                stats = stats,
                targetKcal = targetKcal,
                onOpenDay = onOpenDay,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DayCell(
    dayNumber: Int,
    fullness: DayFullness,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dotColor = when (fullness) {
        DayFullness.FULL -> scheme.primary
        DayFullness.LITTLE -> LittleDataAmber
        DayFullness.EMPTY -> EmptyDayGray.copy(alpha = 0.4f)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isToday) scheme.primary else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    dayNumber.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isToday) scheme.onPrimary else scheme.onSurface,
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
    }
}

@Composable
private fun Legend() {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(scheme.primary, stringResource(R.string.calendar_legend_full))
        Spacer(Modifier.width(16.dp))
        LegendDot(LittleDataAmber, stringResource(R.string.calendar_legend_little))
        Spacer(Modifier.width(16.dp))
        LegendDot(EmptyDayGray, stringResource(R.string.calendar_legend_empty))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TrendCard(
    month: YearMonth,
    stats: Map<LocalDate, DayStats>,
    targetKcal: Int?,
    onOpenDay: (LocalDate) -> Unit,
) {
    SectionCard(title = stringResource(R.string.calendar_trend_title)) {
        targetKcal?.let { target ->
            Text(
                stringResource(R.string.calendar_trend_target, target),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (stats.isEmpty()) {
            Text(
                stringResource(R.string.calendar_trend_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        val daysInMonth = month.lengthOfMonth()
        val maxKcal = stats.values.maxOf { it.totalKcal }.coerceAtLeast(1.0)
        val scheme = MaterialTheme.colorScheme
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                for (dayNumber in 1..daysInMonth) {
                    val date = month.atDay(dayNumber)
                    val dayStats = stats[date]
                    val kcal = dayStats?.totalKcal ?: 0.0
                    val fraction = if (kcal > 0.0) {
                        (kcal / maxKcal).toFloat().coerceIn(0.04f, 1f)
                    } else {
                        0f
                    }
                    val barColor = when (fullnessOf(dayStats)) {
                        DayFullness.FULL -> scheme.primary
                        DayFullness.LITTLE -> LittleDataAmber
                        DayFullness.EMPTY -> scheme.onSurface.copy(alpha = 0.10f)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onOpenDay(date) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(barColor),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                for (dayNumber in 1..daysInMonth) {
                    Text(
                        if (dayNumber % 2 == 1) dayNumber.toString() else "",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 7.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
