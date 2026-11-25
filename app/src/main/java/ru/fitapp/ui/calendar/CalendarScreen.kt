package ru.fitapp.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
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
import ru.fitapp.ui.common.ScreenTopBar
import ru.fitapp.ui.common.SectionCard
import ru.fitapp.ui.common.formatMonthTitle
import ru.fitapp.ui.factoryOf
import ru.fitapp.ui.fitApp

/** Цвет «мало данных» — янтарный; «пусто» — нейтральный серый. */
private val LittleDataAmber = Color(0xFFEF8A00)
private val EmptyDayGray = Color(0xFFB9B7AE)

private enum class DayFullness { FULL, LITTLE, EMPTY }

private fun fullnessOf(stats: DayStats?): DayFullness = when {
    stats == null || stats.mealCount == 0 -> DayFullness.EMPTY
    stats.doneCount >= ReportCalc.MIN_DONE_MEALS && stats.failedCount == 0 -> DayFullness.FULL
    else -> DayFullness.LITTLE
}

/**
 * Календарь: месяц в белой карточке, полнота дня — точкой под числом,
 * тренд ккал столбиками с пунктирной линией цели.
 */
@Composable
fun CalendarScreen(navController: NavController, onOpenDay: (LocalDate) -> Unit) {
    val app = fitApp()
    val vm: CalendarViewModel = viewModel(factory = factoryOf { CalendarViewModel(app) })
    val month by vm.month.collectAsState()
    val stats by vm.stats.collectAsState()
    val targetKcal by vm.targetKcal.collectAsState()
    val weekdays = stringArrayResource(R.array.weekday_short).toList()
    val today = remember { LocalDate.now() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(title = stringResource(R.string.calendar_title)) },
        bottomBar = { FitAppBottomBar(navController) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MonthCard(
                month = month,
                weekdays = weekdays,
                stats = stats,
                today = today,
                onPrev = { vm.shiftMonth(-1) },
                onNext = { vm.shiftMonth(1) },
                onOpenDay = onOpenDay,
            )
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
private fun MonthCard(
    month: YearMonth,
    weekdays: List<String>,
    stats: Map<LocalDate, DayStats>,
    today: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatMonthTitle(month),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                IconButton(onClick = onPrev, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        stringResource(R.string.cd_prev_month),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        stringResource(R.string.cd_next_month),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
                        Box(modifier = Modifier.weight(1f).height(42.dp)) {
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
        DayFullness.EMPTY -> EmptyDayGray.copy(alpha = 0.55f)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (fullness == DayFullness.EMPTY) dotColor.copy(alpha = 0.45f) else dotColor),
            )
        }
    }
}

@Composable
private fun Legend() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(MaterialTheme.colorScheme.primary, stringResource(R.string.calendar_legend_full))
        Spacer(Modifier.width(14.dp))
        LegendDot(LittleDataAmber, stringResource(R.string.calendar_legend_little))
        Spacer(Modifier.width(14.dp))
        LegendDot(EmptyDayGray, stringResource(R.string.calendar_legend_empty))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrendCard(
    month: YearMonth,
    stats: Map<LocalDate, DayStats>,
    targetKcal: Int?,
    onOpenDay: (LocalDate) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    SectionCard(
        title = stringResource(R.string.calendar_trend_title),
        subtitle = targetKcal?.let { stringResource(R.string.calendar_trend_target, it) },
    ) {
        if (stats.isEmpty()) {
            Text(
                stringResource(R.string.calendar_trend_empty),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        val daysInMonth = month.lengthOfMonth()
        val maxKcal = maxOf(
            stats.values.maxOf { it.totalKcal },
            (targetKcal ?: 0).toDouble() * 1.15,
            1.0,
        )
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(150.dp),
            ) {
                if (targetKcal != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val y = size.height * (1f - (targetKcal / maxKcal).toFloat()).coerceIn(0f, 1f)
                        drawLine(
                            color = scheme.onSurfaceVariant.copy(alpha = 0.45f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (dayNumber in 1..daysInMonth) {
                        val date = month.atDay(dayNumber)
                        val dayStats = stats[date]
                        val kcal = dayStats?.totalKcal ?: 0.0
                        val fraction = if (kcal > 0.0) {
                            (kcal / maxKcal).toFloat().coerceIn(0.04f, 1f)
                        } else {
                            0.02f
                        }
                        val barColor = when (fullnessOf(dayStats)) {
                            DayFullness.FULL -> scheme.primary
                            DayFullness.LITTLE -> LittleDataAmber
                            DayFullness.EMPTY -> scheme.onSurface.copy(alpha = 0.08f)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onOpenDay(date) },
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(fraction)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(barColor),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dayNumber in 1..daysInMonth) {
                    Text(
                        if (dayNumber % 2 == 1) dayNumber.toString() else "",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 8.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.calendar_trend_hint),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
