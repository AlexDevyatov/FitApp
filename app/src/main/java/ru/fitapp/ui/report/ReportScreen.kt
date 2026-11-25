package ru.fitapp.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import kotlin.math.min
import kotlin.math.roundToInt
import ru.fitapp.R
import ru.fitapp.domain.DayReport
import ru.fitapp.domain.EnergyVerdict
import ru.fitapp.domain.NutrientBlock
import ru.fitapp.domain.NutrientVerdict
import ru.fitapp.domain.RegimenBlock
import ru.fitapp.ui.common.ScreenTopBar
import ru.fitapp.ui.common.SectionCard
import ru.fitapp.ui.common.ThinProgress
import ru.fitapp.ui.common.formatDateFull
import ru.fitapp.ui.common.formatGap
import ru.fitapp.ui.factoryOf
import ru.fitapp.ui.fitApp

/**
 * Отчёт дня: вердикт первой карточкой, затем три блока с прогресс-барами,
 * текст модели — последним и не спорит с цифрами.
 */
@Composable
fun ReportScreen(date: LocalDate, onBack: () -> Unit) {
    val app = fitApp()
    val vm: ReportViewModel = viewModel(key = "report-$date", factory = factoryOf { ReportViewModel(app, date) })
    val state by vm.state.collectAsState()

    LaunchedEffect(state.report, state.text, state.textLoading) {
        if (state.report != null && state.text == null && !state.textLoading) {
            vm.refreshText()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.report_title),
                subtitle = formatDateFull(date),
                onBack = onBack,
            )
        },
    ) { padding ->
        val report = state.report
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                state.targetsMissing -> item(key = "no-targets") {
                    InfoBanner(text = stringResource(R.string.report_no_targets))
                }

                report == null -> item(key = "loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }

                else -> {
                    if (!state.hasDoneMeals) {
                        item(key = "no-meals") {
                            InfoBanner(text = stringResource(R.string.report_no_meals))
                        }
                    }
                    item(key = "verdict") { VerdictCard(report) }
                    if (report.littleData) {
                        item(key = "little-data") { LittleDataBanner() }
                    }
                    item(key = "energy") { EnergyCard(report) }
                    item(key = "composition") { CompositionCard(report) }
                    item(key = "regimen") { RegimenCard(report.regimen) }
                    item(key = "text") {
                        ReportTextCard(
                            text = state.text,
                            loading = state.textLoading,
                            onRefresh = vm::refreshText,
                        )
                    }
                    item(key = "spacer") { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun VerdictCard(report: DayReport) {
    val scheme = MaterialTheme.colorScheme
    val verdict = report.energy.verdict
    val (container, content) = when (verdict) {
        EnergyVerdict.LIKELY_DEFICIT -> scheme.primaryContainer to scheme.onPrimaryContainer
        EnergyVerdict.LIKELY_EXCESS -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        EnergyVerdict.WITHIN_UNCERTAINTY -> scheme.surfaceContainerLow to scheme.onSurface
    }
    val icon = when (verdict) {
        EnergyVerdict.LIKELY_DEFICIT -> Icons.Filled.CheckCircle
        EnergyVerdict.LIKELY_EXCESS -> Icons.Filled.WarningAmber
        EnergyVerdict.WITHIN_UNCERTAINTY -> Icons.Filled.Shield
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    fmt(report.energy.interval.low) + "–" + fmt(report.energy.interval.high),
                    style = MaterialTheme.typography.headlineLarge,
                    color = content,
                    modifier = Modifier.weight(1f),
                )
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(30.dp))
            }
            Text(
                stringResource(R.string.unit_kcal) + " · " + verdict.label,
                style = MaterialTheme.typography.titleMedium,
                color = content.copy(alpha = 0.9f),
            )
            Text(
                stringResource(R.string.report_target_kcal, report.energy.targetKcal),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun EnergyCard(report: DayReport) {
    val scheme = MaterialTheme.colorScheme
    val mid = (report.energy.interval.low + report.energy.interval.high) / 2.0
    SectionCard(
        title = stringResource(R.string.report_block_energy),
        subtitle = stringResource(R.string.report_target_kcal, report.energy.targetKcal),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(
                    R.string.report_kcal_range,
                    fmt(report.energy.interval.low),
                    fmt(report.energy.interval.high),
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.report_percent_target, (mid / report.energy.targetKcal * 100).roundToInt()),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        ThinProgress((mid / report.energy.targetKcal).toFloat())
        Text(
            report.energy.verdict.label,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompositionCard(report: DayReport) {
    val scheme = MaterialTheme.colorScheme
    SectionCard(title = stringResource(R.string.report_block_composition)) {
        NutrientRow(stringResource(R.string.report_protein), report.protein)
        HorizontalDivider(color = scheme.outlineVariant)
        NutrientRow(stringResource(R.string.report_fat), report.fat)
        HorizontalDivider(color = scheme.outlineVariant)
        NutrientRow(stringResource(R.string.report_carbs), report.carbs)
        HorizontalDivider(color = scheme.outlineVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(
                    R.string.report_fiber_value,
                    fmt(report.composition.fiberInterval.low),
                    fmt(report.composition.fiberInterval.high),
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(
                    R.string.report_sugar_value,
                    fmt(report.composition.sugarInterval.low),
                    fmt(report.composition.sugarInterval.high),
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            stringResource(
                R.string.report_ultra_value,
                report.composition.ultraProcessedItems,
                report.composition.totalItems,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NutrientRow(name: String, block: NutrientBlock) {
    val scheme = MaterialTheme.colorScheme
    val mid = (block.interval.low + block.interval.high) / 2.0
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${fmt(block.interval.low)}–${fmt(block.interval.high)} г",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        ThinProgress(
            (mid / block.targetG).toFloat(),
            color = if (block.verdict == NutrientVerdict.ABOVE) scheme.tertiary else scheme.primary,
        )
        Row {
            Text(
                stringResource(R.string.report_target_short, block.targetG),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                block.verdict.label,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

private fun fmt(value: Double): String = value.roundToInt().toString()

@Composable
private fun RegimenCard(regimen: RegimenBlock) {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.report_block_regimen)) {
        RegimenRow(
            icon = Icons.Filled.Schedule,
            label = stringResource(R.string.report_meal_count, regimen.mealCount),
        )
        RegimenRow(
            icon = Icons.Filled.Schedule,
            label = if (regimen.lastIntake != null) {
                stringResource(
                    R.string.report_last_intake,
                    "%02d:%02d".format(regimen.lastIntake.hour, regimen.lastIntake.minute),
                )
            } else {
                stringResource(R.string.report_last_intake_none)
            },
        )
        RegimenRow(
            icon = Icons.Filled.Schedule,
            label = stringResource(R.string.report_late_meals, regimen.lateMeals),
        )
        RegimenRow(
            icon = Icons.Filled.Schedule,
            label = stringResource(
                R.string.report_longest_gap,
                regimen.longestGapMinutes?.let { formatGap(context, it) } ?: "—",
            ),
        )
    }
}

@Composable
private fun RegimenRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReportTextCard(text: String?, loading: Boolean, onRefresh: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    SectionCard(title = stringResource(R.string.report_text_title)) {
        if (loading && text == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.report_text_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (text != null) {
            Text(text.replace("**", ""), style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = onRefresh, enabled = !loading) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.report_text_refresh))
        }
    }
}

@Composable
private fun LittleDataBanner() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = scheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = scheme.onTertiaryContainer,
                modifier = Modifier.size(22.dp),
            )
            Text(
                stringResource(R.string.report_little_data_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun InfoBanner(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(18.dp),
        )
    }
}
