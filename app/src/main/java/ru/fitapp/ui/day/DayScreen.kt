package ru.fitapp.ui.day

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import kotlin.math.roundToInt
import ru.fitapp.R
import ru.fitapp.data.db.MealStatus
import ru.fitapp.data.db.MealWithItems
import ru.fitapp.ui.FitAppBottomBar
import ru.fitapp.ui.common.CalorieRing
import ru.fitapp.ui.common.EmptyState
import ru.fitapp.ui.common.MacroChip
import ru.fitapp.ui.common.MealStatusChip
import ru.fitapp.ui.common.MealThumbnail
import ru.fitapp.ui.common.ScreenTopBar
import ru.fitapp.ui.common.SectionCard
import ru.fitapp.ui.common.StatusDot
import ru.fitapp.ui.common.formatDateFull
import ru.fitapp.ui.common.formatGrams
import ru.fitapp.ui.common.formatKcal
import ru.fitapp.ui.common.formatTimeOfDay
import ru.fitapp.ui.common.kindLabelRes
import ru.fitapp.ui.common.statusLabelRes
import ru.fitapp.ui.factoryOf
import ru.fitapp.ui.fitApp

/**
 * Экран дня: hero-кольцо энергии, макросы, лента карточек с фото и статусами.
 * Отвечает на три вопроса моментально: сколько съедено, из чего, что было.
 */
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
    val targets by vm.targets.collectAsState()
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

    val doneItems = meals.filter { it.meal.status == MealStatus.DONE }.flatMap { it.items }
    val kcal = doneItems.sumOf { it.kcal }.roundToInt()
    val protein = doneItems.sumOf { it.proteinG }
    val fat = doneItems.sumOf { it.fatG }
    val carbs = doneItems.sumOf { it.carbsG }
    val target = targets

    fun launchGallery() {
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ScreenTopBar(
                title = if (isToday) stringResource(R.string.today_title) else stringResource(R.string.day_title),
                subtitle = formatDateFull(date),
                onBack = onBack,
                actions = {
                    if (isToday) {
                        IconButton(onClick = ::launchGallery, enabled = !importing) {
                            Icon(Icons.Filled.PhotoLibrary, stringResource(R.string.day_gallery))
                        }
                    }
                    IconButton(onClick = { onOpenReport(date) }) {
                        Icon(Icons.Filled.Assessment, stringResource(R.string.day_report_action))
                    }
                },
            )
        },
        bottomBar = {
            if (navController != null) FitAppBottomBar(navController)
        },
        floatingActionButton = {
            if (isToday && meals.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onCapture,
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                    text = {
                        Text(
                            stringResource(R.string.day_capture),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (importing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 128.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "hero") {
                    HeroCard(
                        kcal = kcal,
                        targetKcal = target?.kcal,
                        protein = protein,
                        fat = fat,
                        carbs = carbs,
                        targetProtein = target?.proteinG,
                        targetFat = target?.fatG,
                        targetCarbs = target?.carbsG,
                    )
                }

                if (meals.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Outlined.Restaurant,
                            title = stringResource(R.string.day_empty_title),
                            hint = stringResource(
                                if (isToday) R.string.day_empty_hint else R.string.day_empty_hint_past,
                            ),
                            modifier = Modifier.height(360.dp),
                            actions = if (isToday) {
                                {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        androidx.compose.material3.Button(
                                            onClick = onCapture,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                        ) {
                                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.day_capture))
                                        }
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = ::launchGallery,
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                        ) {
                                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.day_gallery))
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }
                } else {
                    item(key = "feed-title") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.day_meals_title),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                stringResource(R.string.day_meals_count, meals.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item(key = "feed") {
                        MealFeed(meals = meals, onOpenMeal = onOpenMeal)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    kcal: Int,
    targetKcal: Int?,
    protein: Double,
    fat: Double,
    carbs: Double,
    targetProtein: Int?,
    targetFat: Int?,
    targetCarbs: Int?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            CalorieRing(consumedKcal = kcal, targetKcal = targetKcal)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MacroChip(
                    label = stringResource(R.string.day_macro_protein),
                    valueText = macroValue(protein, targetProtein),
                    progress = progress(protein, targetProtein),
                    icon = Icons.Filled.Bolt,
                    modifier = Modifier.weight(1f),
                    over = targetProtein != null && protein > targetProtein,
                )
                MacroChip(
                    label = stringResource(R.string.day_macro_fat),
                    valueText = macroValue(fat, targetFat),
                    progress = progress(fat, targetFat),
                    icon = Icons.Filled.LocalFireDepartment,
                    modifier = Modifier.weight(1f),
                    over = targetFat != null && fat > targetFat,
                )
                MacroChip(
                    label = stringResource(R.string.day_macro_carbs),
                    valueText = macroValue(carbs, targetCarbs),
                    progress = progress(carbs, targetCarbs),
                    icon = Icons.Filled.Grain,
                    modifier = Modifier.weight(1f),
                    over = targetCarbs != null && carbs > targetCarbs,
                )
            }
        }
    }
}

private fun macroValue(value: Double, target: Int?): String =
    if (target != null) "${formatGrams(value)} / $target г" else "${formatGrams(value)} г"

private fun progress(value: Double, target: Int?): Float =
    if (target != null && target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f

@Composable
private fun MealFeed(meals: List<MealWithItems>, onOpenMeal: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            meals.forEachIndexed { index, meal ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 96.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                MealRow(mealWithItems = meal, onClick = { onOpenMeal(meal.meal.id) })
            }
        }
    }
}

/** Строка ленты 88 dp: фото 64, название/мета, справа ккал и статус-точка. */
@Composable
private fun MealRow(mealWithItems: MealWithItems, onClick: () -> Unit) {
    val meal = mealWithItems.meal
    val kcal = mealWithItems.items.sumOf { it.kcal }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box {
            MealThumbnail(photoPath = meal.photoPath, size = 64.dp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = mealRowTitle(mealWithItems),
                style = MaterialTheme.typography.titleMedium,
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
            }
            if (meal.status == MealStatus.FAILED && !meal.errorMessage.isNullOrBlank()) {
                Text(
                    meal.errorMessage ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (meal.status == MealStatus.DONE) {
                Text(
                    formatKcal(kcal),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.unit_kcal),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MealStatusChip(meal.status)
            }
            StatusDot(meal.status)
        }
    }
}

@Composable
private fun mealRowTitle(mealWithItems: MealWithItems): String {
    val meal = mealWithItems.meal
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
