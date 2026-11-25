package ru.fitapp.ui.settings

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import ru.fitapp.R
import ru.fitapp.core.LogTags
import ru.fitapp.data.prefs.SettingsStore
import ru.fitapp.ui.FitAppBottomBar
import ru.fitapp.ui.Routes
import ru.fitapp.ui.common.ScreenTopBar
import ru.fitapp.ui.common.SectionCard
import ru.fitapp.ui.common.SettingsGroup
import ru.fitapp.ui.common.SettingsRow
import ru.fitapp.ui.factoryOf
import ru.fitapp.ui.fitApp

/** Тег уведомлений: решение пользователя по POST_NOTIFICATIONS. */
private const val TAG = LogTags.PUSH

/**
 * Настройки: карточка профиля, группы «Подключение / Напоминания / Цели / Данные / О приложении».
 */
@Composable
fun SettingsScreen(navController: NavController) {
    val app = fitApp()
    val vm: SettingsViewModel = viewModel(factory = factoryOf { SettingsViewModel(app) })
    val hasKey by vm.hasKey.collectAsState()
    val pushEnabled by vm.pushEnabled.collectAsState()
    val pushHour by vm.pushHour.collectAsState()
    val darkThemeMode by vm.darkThemeMode.collectAsState()
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
        Log.i(TAG, "push: пользователь ответил на запрос POST_NOTIFICATIONS granted=$granted")
        if (!granted) {
            Log.w(TAG, "push: уведомления запрещены системой — вечерний отчёт не покажется")
        }
        vm.setPushEnabled(true)
    }

    LaunchedEffect(Unit) { vm.refreshKeyState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { ScreenTopBar(title = stringResource(R.string.settings_title)) },
        bottomBar = { FitAppBottomBar(navController) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ProfileCard(
                kcal = targets?.kcal,
                protein = targets?.proteinG,
                fat = targets?.fatG,
                carbs = targets?.carbsG,
                onEdit = { navController.navigate(Routes.SETTINGS_PROFILE) },
            )

            SettingsGroup(title = stringResource(R.string.settings_group_connection)) {
                SettingsRow(
                    icon = Icons.Filled.Key,
                    title = stringResource(R.string.settings_section_key),
                    subtitle = stringResource(
                        if (hasKey) R.string.settings_key_saved else R.string.settings_key_missing,
                    ),
                    onClick = { navController.navigate(Routes.SETTINGS_KEY) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        CircleShape,
                                    ),
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
                if (hasKey) {
                    HorizontalDivider(modifier = Modifier.padding(start = 74.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = Icons.Filled.DeleteOutline,
                        title = stringResource(R.string.settings_key_clear),
                        onClick = { showClearKeyDialog = true },
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.settings_group_appearance)) {
                SettingsRow(
                    icon = when (darkThemeMode) {
                        SettingsStore.THEME_MODE_LIGHT -> Icons.Filled.LightMode
                        SettingsStore.THEME_MODE_DARK -> Icons.Filled.DarkMode
                        else -> Icons.Filled.BrightnessAuto
                    },
                    title = stringResource(R.string.settings_theme_title),
                    subtitle = when (darkThemeMode) {
                        SettingsStore.THEME_MODE_LIGHT -> stringResource(R.string.settings_theme_light)
                        SettingsStore.THEME_MODE_DARK -> stringResource(R.string.settings_theme_dark)
                        else -> stringResource(R.string.settings_theme_system)
                    },
                    trailing = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ThemeChip(
                                selected = darkThemeMode == SettingsStore.THEME_MODE_SYSTEM,
                                label = stringResource(R.string.settings_theme_system),
                                onClick = { vm.setDarkThemeMode(SettingsStore.THEME_MODE_SYSTEM) },
                            )
                            ThemeChip(
                                selected = darkThemeMode == SettingsStore.THEME_MODE_LIGHT,
                                label = stringResource(R.string.settings_theme_light),
                                onClick = { vm.setDarkThemeMode(SettingsStore.THEME_MODE_LIGHT) },
                            )
                            ThemeChip(
                                selected = darkThemeMode == SettingsStore.THEME_MODE_DARK,
                                label = stringResource(R.string.settings_theme_dark),
                                onClick = { vm.setDarkThemeMode(SettingsStore.THEME_MODE_DARK) },
                            )
                        }
                    },
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_group_reminders)) {
                SettingsRow(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.settings_push_title),
                    subtitle = stringResource(R.string.settings_push_enabled_hint),
                    trailing = {
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
                    },
                )
                if (pushEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(start = 74.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = Icons.Filled.Info,
                        title = stringResource(R.string.settings_push_time_short, pushHour),
                        onClick = { showHourDialog = true },
                        trailing = {
                            Text(
                                stringResource(R.string.settings_push_time_action),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                }
                if (notificationDenied) {
                    Text(
                        stringResource(R.string.settings_push_permission_denied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.settings_group_goals)) {
                SettingsRow(
                    icon = Icons.Filled.PieChart,
                    title = stringResource(R.string.settings_section_goals),
                    subtitle = if (targets != null) {
                        stringResource(
                            R.string.settings_goals_value,
                            targets?.kcal ?: 0,
                            targets?.proteinG ?: 0,
                            targets?.fatG ?: 0,
                            targets?.carbsG ?: 0,
                        )
                    } else {
                        stringResource(R.string.settings_goals_missing)
                    },
                    onClick = { navController.navigate(Routes.SETTINGS_PROFILE) },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_group_data)) {
                SettingsRow(
                    icon = Icons.Filled.Insights,
                    title = stringResource(R.string.settings_section_stats),
                    subtitle = stringResource(R.string.stats_note),
                    onClick = { navController.navigate(Routes.STATS) },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_group_about)) {
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.settings_about_version),
                    subtitle = stringResource(R.string.settings_about_note),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClearKeyDialog) {
        AlertDialog(
            onDismissRequest = { showClearKeyDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.settings_key_clear_title)) },
            text = { Text(stringResource(R.string.settings_key_clear_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearKey()
                        showClearKeyDialog = false
                    },
                ) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
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
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.settings_dialog_hour_title)) },
            text = {
                Column(
                    modifier = Modifier.height(360.dp).verticalScroll(rememberScrollState()),
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

@Composable
private fun ProfileCard(
    kcal: Int?,
    protein: Int?,
    fat: Int?,
    carbs: Int?,
    onEdit: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(scheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.settings_profile_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (kcal != null) {
                            stringResource(R.string.settings_profile_goal, kcal)
                        } else {
                            stringResource(R.string.settings_goals_missing)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.settings_profile_edit))
                }
            }
            if (kcal != null) {
                HorizontalDivider(color = scheme.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    MacroSummary(stringResource(R.string.day_macro_protein), protein)
                    MacroSummary(stringResource(R.string.day_macro_fat), fat)
                    MacroSummary(stringResource(R.string.day_macro_carbs), carbs)
                }
            }
        }
    }
}

@Composable
private fun ThemeChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) scheme.primary else scheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MacroSummary(label: String, grams: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            grams?.let { "$it г" } ?: "—",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** Локальная «Статистика теста»: счётчики правок, никуда не отправляются. */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val app = fitApp()
    val vm: SettingsViewModel = viewModel(key = "stats", factory = factoryOf { SettingsViewModel(app) })
    val stats by vm.stats.collectAsState()
    val editDays by vm.editDays.collectAsState()
    val recentEdits by vm.recentEdits.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(title = stringResource(R.string.stats_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val current = stats
            if (current == null) {
                Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
            } else {
                val accuracy = if (current.totalMeals > 0) {
                    (100 - current.editedMeals * 100 / current.totalMeals).coerceIn(0, 100)
                } else {
                    100
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(
                        value = current.totalMeals.toString(),
                        label = stringResource(R.string.stats_metric_meals),
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        value = current.editedMeals.toString(),
                        label = stringResource(R.string.stats_metric_edited),
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        value = "$accuracy%",
                        label = stringResource(R.string.stats_metric_accuracy),
                        modifier = Modifier.weight(1f),
                    )
                }
                SectionCard(title = stringResource(R.string.stats_edits_title)) {
                    if (editDays.isEmpty()) {
                        Text(
                            stringResource(R.string.stats_edits_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        EditChart(editDays)
                    }
                }
                SectionCard(title = stringResource(R.string.stats_recent_title)) {
                    if (recentEdits.isEmpty()) {
                        Text(
                            stringResource(R.string.stats_recent_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        recentEdits.forEachIndexed { index, edit ->
                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    editText(edit),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    editDateTime(edit.at),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.stats_total_edits, current.totalEdits), style = MaterialTheme.typography.titleMedium)
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            stringResource(R.string.stats_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EditChart(days: List<ru.fitapp.data.db.EditDayCount>) {
    val max = days.maxOf { it.count }.coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth().height(132.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.sortedBy { it.day }.forEach { day ->
            Column(
                modifier = Modifier.weight(1f).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    day.count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((76 * day.count / max).dp.coerceAtLeast(4.dp))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    dayLabel(day.day),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun dayLabel(day: String): String {
    val parts = day.split("-")
    return if (parts.size == 3) "${parts[2]}.${parts[1]}" else day
}

private fun editDateTime(epochMs: Long): String {
    val time = java.time.Instant.ofEpochMilli(epochMs)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
    return java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm", java.util.Locale.forLanguageTag("ru")).format(time)
}

private fun editText(edit: ru.fitapp.data.db.RecentEdit): String {
    val before = kindEditLabel(edit.before)
    val after = kindEditLabel(edit.after)
    return when {
        before != null && after != null -> "$before → $after"
        after != null -> after
        before != null -> "Убрано: $before"
        else -> edit.field
    }
}

private fun kindEditLabel(value: String?): String? = when (value) {
    null -> null
    "dish" -> "Блюдо"
    "plate" -> "Тарелка"
    "label" -> "Этикетка"
    else -> value
}

@Composable
private fun MetricCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
