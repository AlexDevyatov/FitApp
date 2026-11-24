package ru.fitapp.ui.report

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import kotlin.math.roundToInt
import ru.fitapp.R
import ru.fitapp.domain.DayReport
import ru.fitapp.domain.NutrientBlock
import ru.fitapp.domain.RegimenBlock
import ru.fitapp.ui.common.SectionCard
import ru.fitapp.ui.common.formatDateFull
import ru.fitapp.ui.common.formatGap
import ru.fitapp.ui.factoryOf
import ru.fitapp.ui.fitApp

/**
 * Отчёт дня (§8.3, §9): три блока (Энергия / Состав / Режим) + вердикт + «мало данных».
 * Все цифры — из ReportCalc; текст LLM показывается только после ReportTextSanitizer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(date: LocalDate, onBack: () -> Unit) {
    val app = fitApp()
    val vm: ReportViewModel = viewModel(key = "report-$date", factory = factoryOf { ReportViewModel(app, date) })
    val state by vm.state.collectAsState()

    // Первичная генерация текста, когда отчёт впервые посчитан.
    LaunchedEffect(state.report, state.text, state.textLoading) {
        if (state.report != null && state.text == null && !state.textLoading) {
            vm.refreshText()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.report_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            formatDateFull(date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val report = state.report
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.targetsMissing -> item(key = "no-targets") {
                    InfoBanner(text = stringResource(R.string.report_no_targets))
                }

                report == null -> item(key = "loading") {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                else -> {
                    if (report.littleData) {
                        item(key = "little-data") {
                            LittleDataBanner()
                        }
                    }
                    if (!state.hasDoneMeals) {
                        item(key = "no-meals") {
                            InfoBanner(text = stringResource(R.string.report_no_meals))
                        }
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
                    item(key = "spacer") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun EnergyCard(report: DayReport) {
    SectionCard(title = stringResource(R.string.report_block_energy)) {
        Text(
            stringResource(
                R.string.report_kcal_range,
                fmt(report.energy.interval.low),
                fmt(report.energy.interval.high),
            ),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.report_target_kcal, report.energy.targetKcal),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VerdictSurface(report.energy.verdict.label)
    }
}

@Composable
private fun CompositionCard(report: DayReport) {
    SectionCard(title = stringResource(R.string.report_block_composition)) {
        NutrientRow(stringResource(R.string.report_protein), report.protein)
        NutrientRow(stringResource(R.string.report_fat), report.fat)
        NutrientRow(stringResource(R.string.report_carbs), report.carbs)
        HorizontalDivider()
        Text(
            stringResource(
                R.string.report_fiber_value,
                fmt(report.composition.fiberInterval.low),
                fmt(report.composition.fiberInterval.high),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(
                R.string.report_sugar_value,
                fmt(report.composition.sugarInterval.low),
                fmt(report.composition.sugarInterval.high),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(
                R.string.report_ultra_value,
                report.composition.ultraProcessedItems,
                report.composition.totalItems,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun NutrientRow(name: String, block: NutrientBlock) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(
                R.string.report_range_vs_target,
                fmt(block.interval.low),
                fmt(block.interval.high),
                block.targetG,
                block.verdict.label,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Границы интервалов показываем целыми — точность оценки всё равно ±30% (§8.2). */
private fun fmt(value: Double): String = value.roundToInt().toString()

@Composable
private fun RegimenCard(regimen: RegimenBlock) {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.report_block_regimen)) {
        Text(
            stringResource(R.string.report_meal_count, regimen.mealCount),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            if (regimen.lastIntake != null) {
                stringResource(
                    R.string.report_last_intake,
                    "%02d:%02d".format(regimen.lastIntake.hour, regimen.lastIntake.minute),
                )
            } else {
                stringResource(R.string.report_last_intake_none)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.report_late_meals, regimen.lateMeals),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(
                R.string.report_longest_gap,
                regimen.longestGapMinutes?.let { formatGap(context, it) } ?: "—",
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ReportTextCard(text: String?, loading: Boolean, onRefresh: () -> Unit) {
    SectionCard(title = stringResource(R.string.report_text_title)) {
        if (loading && text == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.report_text_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (text != null) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.report_little_data_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun InfoBanner(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun VerdictSurface(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            stringResource(R.string.report_verdict, label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
