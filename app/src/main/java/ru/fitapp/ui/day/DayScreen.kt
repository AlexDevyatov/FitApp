package ru.fitapp.ui.day

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import java.time.LocalDate
import ru.fitapp.R
import ru.fitapp.data.db.MealEntity
import ru.fitapp.data.db.MealStatus
import ru.fitapp.data.db.MealWithItems
import ru.fitapp.ui.FitAppBottomBar
import ru.fitapp.ui.common.EmptyState
import ru.fitapp.ui.common.MealStatusChip
import ru.fitapp.ui.common.MealThumbnail
import ru.fitapp.ui.common.formatDateFull
import ru.fitapp.ui.common.formatTimeOfDay
import ru.fitapp.ui.common.kindLabelRes
import ru.fitapp.ui.common.statusLabelRes
import ru.fitapp.ui.factoryOf
import ru.fitapp.ui.fitApp

/**
 * Экран дня (§9 «Сегодня» и «День» из календаря): лента карточек по времени,
 * статусы видны явно, «Снять» — FAB, «Из галереи» — Photo Picker.
 * Для прошлого дня (isToday == false) лента та же, но без добавления снимков.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    date: LocalDate,
    isToday: Boolean,
    navController: NavController?,
    onBack: (() -> Unit)? = null,
    onOpenMeal: (String) -> Unit,
    onOpenReport: (LocalDate) -> Unit,
    onCapture: () -> Unit,
) {
    val app = fitApp()
    val vm: DayViewModel = viewModel(key = "day-$date", factory = factoryOf { DayViewModel(app, date) })
    val meals by vm.meals.collectAsState()
    val importing by vm.importing.collectAsState()
    val importFailed by vm.importFailed.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) vm.importFromGallery(uri)
    }

    LaunchedEffect(importFailed) {
        if (importFailed) {
            snackbarHostState.showSnackbar(context.getString(R.string.day_import_failed))
            vm.consumeImportError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isToday) {
                        Column {
                            Text(stringResource(R.string.today_title), style = MaterialTheme.typography.titleLarge)
                            Text(
                                formatDateFull(date),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(formatDateFull(date), style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    if (isToday) {
                        IconButton(
                            onClick = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            enabled = !importing,
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, stringResource(R.string.day_gallery))
                        }
                    }
                    TextButton(onClick = { onOpenReport(date) }) {
                        Text(stringResource(R.string.day_report_action))
                    }
                },
            )
        },
        bottomBar = {
            if (navController != null) FitAppBottomBar(navController)
        },
        floatingActionButton = {
            if (isToday) {
                ExtendedFloatingActionButton(
                    onClick = onCapture,
                    icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                    text = { Text(stringResource(R.string.day_capture)) },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (importing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (meals.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Restaurant,
                    title = stringResource(R.string.day_empty_title),
                    hint = stringResource(
                        if (isToday) R.string.day_empty_hint else R.string.day_empty_hint_past,
                    ),
                    action = if (isToday) {
                        {
                            TextButton(onClick = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            }) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.day_gallery))
                            }
                        }
                    } else {
                        null
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(meals, key = { it.meal.id }) { mealWithItems ->
                        MealRowCard(
                            mealWithItems = mealWithItems,
                            onClick = { onOpenMeal(mealWithItems.meal.id) },
                        )
                    }
                }
            }
        }
    }
}

/** Строка ленты: миниатюра, название/статус, время, тип кадра, причина ошибки. */
@Composable
private fun MealRowCard(mealWithItems: MealWithItems, onClick: () -> Unit) {
    val meal = mealWithItems.meal
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MealThumbnail(photoPath = meal.photoPath, size = 64.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = mealCardTitle(mealWithItems),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        formatTimeOfDay(meal.takenAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    kindLabelRes(meal.kind)?.let { kindRes ->
                        Text(
                            "· ${stringResource(kindRes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MealStatusChip(meal.status)
                }
                if (meal.status == MealStatus.FAILED && !meal.errorMessage.isNullOrBlank()) {
                    Text(
                        meal.errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.cd_open_meal),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun mealCardTitle(mealWithItems: MealWithItems): String {
    val meal: MealEntity = mealWithItems.meal
    val items = mealWithItems.items
    if (meal.status == MealStatus.DONE && items.isNotEmpty()) {
        val first = items.first().name
        return if (items.size > 1) {
            "$first ${stringResource(R.string.day_items_more, items.size - 1)}"
        } else {
            first
        }
    }
    return stringResource(statusLabelRes(meal.status))
}
