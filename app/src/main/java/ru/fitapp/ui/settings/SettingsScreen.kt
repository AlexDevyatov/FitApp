package ru.fitapp.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import ru.fitapp.R
import ru.fitapp.ui.FitAppBottomBar
import ru.fitapp.ui.Routes
import ru.fitapp.ui.common.SectionCard
import ru.fitapp.ui.factoryOf
import ru.fitapp.ui.fitApp

/**
 * Настройки (§9): смена/проверка/очистка ключа, вечерний пуш + время
 * (runtime POST_NOTIFICATIONS на API 33+), цели (анкета повторно),
 * «Статистика теста» — локальный экран без отправки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val app = fitApp()
    val vm: SettingsViewModel = viewModel(factory = factoryOf { SettingsViewModel(app) })
    val hasKey by vm.hasKey.collectAsState()
    val pushEnabled by vm.pushEnabled.collectAsState()
    val pushHour by vm.pushHour.collectAsState()
    val targets by vm.targets.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearKeyDialog by remember { mutableStateOf(false) }
    var showHourDialog by remember { mutableStateOf(false) }
    var notificationDenied by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationDenied = !granted
        vm.setPushEnabled(true)
    }

    // Ключ могли изменить/очистить на дочернем экране — освежаем при показе.
    LaunchedEffect(Unit) { vm.refreshKeyState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
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
            // --- API-ключ ---
            SectionCard(title = stringResource(R.string.settings_section_key)) {
                Text(
                    stringResource(if (hasKey) R.string.settings_key_saved else R.string.settings_key_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasKey) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                OutlinedButton(
                    onClick = { navController.navigate(Routes.SETTINGS_KEY) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_key_change))
                }
                if (hasKey) {
                    OutlinedButton(
                        onClick = { showClearKeyDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.settings_key_clear),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // --- Вечерний отчёт ---
            SectionCard(title = stringResource(R.string.settings_section_report)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_push_enabled),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = pushEnabled,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                notificationDenied = false
                                vm.setPushEnabled(false)
                            } else if (
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                vm.setPushEnabled(true)
                            }
                        },
                    )
                }
                if (pushEnabled) {
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.settings_push_time, pushHour),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showHourDialog = true }) {
                            Text(stringResource(R.string.settings_push_time_action))
                        }
                    }
                }
                if (notificationDenied) {
                    Text(
                        stringResource(R.string.settings_push_permission_denied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // --- Цели ---
            SectionCard(title = stringResource(R.string.settings_section_goals)) {
                val currentTargets = targets
                Text(
                    if (currentTargets != null) {
                        stringResource(
                            R.string.settings_goals_value,
                            currentTargets.kcal,
                            currentTargets.proteinG,
                            currentTargets.fatG,
                            currentTargets.carbsG,
                        )
                    } else {
                        stringResource(R.string.settings_goals_missing)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = { navController.navigate(Routes.SETTINGS_PROFILE) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_goals_edit))
                }
            }

            // --- Статистика теста ---
            SectionCard(title = stringResource(R.string.settings_section_stats)) {
                Text(
                    stringResource(R.string.stats_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { navController.navigate(Routes.STATS) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_stats_open))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClearKeyDialog) {
        AlertDialog(
            onDismissRequest = { showClearKeyDialog = false },
            title = { Text(stringResource(R.string.settings_key_clear_title)) },
            text = { Text(stringResource(R.string.settings_key_clear_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearKey()
                        showClearKeyDialog = false
                    },
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearKeyDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showHourDialog) {
        AlertDialog(
            onDismissRequest = { showHourDialog = false },
            title = { Text(stringResource(R.string.settings_dialog_hour_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .height(360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    for (hour in 0..23) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setPushHour(hour)
                                    showHourDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "%02d:00".format(hour),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (hour == pushHour) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.weight(1f),
                            )
                            if (hour == pushHour) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHourDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

/** Локальная «Статистика теста» (§9, §15): счётчики правок, никуда не отправляются. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val app = fitApp()
    val vm: SettingsViewModel = viewModel(key = "stats", factory = factoryOf { SettingsViewModel(app) })
    val stats by vm.stats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val current = stats
            if (current == null) {
                Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
            } else {
                SectionCard(title = stringResource(R.string.stats_title)) {
                    Text(
                        stringResource(R.string.stats_total_meals, current.totalMeals),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        if (current.totalMeals > 0) {
                            stringResource(
                                R.string.stats_edited_meals,
                                current.editedMeals,
                                current.totalMeals,
                                current.editedMeals * 100 / current.totalMeals,
                            )
                        } else {
                            stringResource(R.string.stats_edited_meals_none, current.totalMeals)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.stats_total_edits, current.totalEdits),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Row {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.stats_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
