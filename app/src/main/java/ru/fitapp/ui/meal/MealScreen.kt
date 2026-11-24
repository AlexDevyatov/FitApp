package ru.fitapp.ui.meal

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.roundToInt
import ru.fitapp.R
import ru.fitapp.data.db.ItemEntity
import ru.fitapp.data.db.MealStatus
import ru.fitapp.data.db.MealWithItems
import ru.fitapp.domain.FrameKind
import ru.fitapp.ui.common.MealStatusChip
import ru.fitapp.ui.common.formatGrams
import ru.fitapp.ui.common.formatKcal
import ru.fitapp.ui.common.formatTimeOfDay
import ru.fitapp.ui.factoryOf
import ru.fitapp.ui.fitApp

/**
 * Карточка приёма пищи (§9): фото, компоненты с КБЖУ (только чтение), смена типа кадра,
 * правка названия, удаление компонента и всей карточки (с подтверждением),
 * «Повторить обработку» при FAILED/AUTH_ERROR, баннер про ключ при AUTH_ERROR,
 * пометка и предложение удалить при NOT_FOOD.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealScreen(
    mealId: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val app = fitApp()
    val vm: MealViewModel = viewModel(key = "meal-$mealId", factory = factoryOf { MealViewModel(app, mealId) })
    val mealWithItems by vm.meal.collectAsState()
    val deleted by vm.deleted.collectAsState()

    var showDeleteMealDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ItemEntity?>(null) }
    var deleteItemTarget by remember { mutableStateOf<ItemEntity?>(null) }

    LaunchedEffect(deleted) {
        if (deleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.meal_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (mealWithItems != null) {
                        IconButton(onClick = { showDeleteMealDialog = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                stringResource(R.string.meal_delete_card),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val data = mealWithItems
        if (data == null) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            MealContent(
                padding = padding,
                data = data,
                onSetKind = vm::setKind,
                onRenameItem = { renameTarget = it },
                onDeleteItem = { deleteItemTarget = it },
                onDeleteMeal = { showDeleteMealDialog = true },
                onRetry = vm::retry,
                onOpenSettings = onOpenSettings,
            )
        }
    }

    if (showDeleteMealDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteMealDialog = false },
            title = { Text(stringResource(R.string.meal_delete_confirm_title)) },
            text = { Text(stringResource(R.string.meal_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = vm::deleteMeal) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMealDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    renameTarget?.let { target ->
        var text by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.meal_rename_title)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.meal_rename_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        vm.renameItem(target.id, text)
                        renameTarget = null
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    deleteItemTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteItemTarget = null },
            title = { Text(stringResource(R.string.meal_delete_item_title)) },
            text = { Text(stringResource(R.string.meal_delete_item_text, target.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteItem(target.id)
                        deleteItemTarget = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteItemTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun MealContent(
    padding: androidx.compose.foundation.layout.PaddingValues,
    data: MealWithItems,
    onSetKind: (FrameKind) -> Unit,
    onRenameItem: (ItemEntity) -> Unit,
    onDeleteItem: (ItemEntity) -> Unit,
    onDeleteMeal: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val meal = data.meal
    val itemsList = data.items

    LazyColumn(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // --- Фото ---
        item(key = "photo") {
            if (meal.photoPath != null) {
                AsyncImage(
                    model = File(meal.photoPath),
                    contentDescription = stringResource(R.string.cd_meal_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
        }

        // --- Статусная строка ---
        item(key = "status") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MealStatusChip(meal.status)
                Text(
                    formatTimeOfDay(meal.takenAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                meal.confidence?.let { confidence ->
                    Text(
                        stringResource(R.string.meal_confidence, (confidence * 100).roundToInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // --- Баннеры по статусу ---
        when (meal.status) {
            MealStatus.PENDING, MealStatus.RUNNING -> item(key = "processing") {
                InfoCard(
                    text = stringResource(R.string.meal_processing),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    icon = Icons.Filled.Info,
                    showProgress = meal.status == MealStatus.RUNNING,
                )
            }

            MealStatus.AUTH_ERROR -> item(key = "auth") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.meal_auth_banner),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (!meal.errorMessage.isNullOrBlank()) {
                            Text(meal.errorMessage ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onOpenSettings) {
                                Text(stringResource(R.string.meal_auth_banner_action))
                            }
                            TextButton(onClick = onRetry) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.meal_retry))
                            }
                        }
                    }
                }
            }

            MealStatus.FAILED -> item(key = "failed") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            meal.errorMessage ?: stringResource(R.string.status_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = onRetry) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.meal_retry))
                        }
                    }
                }
            }

            MealStatus.NOT_FOOD -> item(key = "not-food") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.meal_not_food_banner),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        if (!meal.errorMessage.isNullOrBlank()) {
                            Text(
                                stringResource(R.string.meal_notes_title) + ": " + meal.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onRetry) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.meal_retry))
                            }
                            TextButton(onClick = onDeleteMeal) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.meal_delete_card))
                            }
                        }
                    }
                }
            }
        }

        // --- Тип кадра (правится вручную, §9/решение 10) ---
        if (meal.status == MealStatus.DONE) {
            item(key = "kind") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.meal_kind), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KindChip(
                            label = stringResource(R.string.meal_kind_dish),
                            selected = meal.kind == "dish",
                            onClick = { onSetKind(FrameKind.DISH) },
                        )
                        KindChip(
                            label = stringResource(R.string.meal_kind_plate),
                            selected = meal.kind == "plate",
                            onClick = { onSetKind(FrameKind.PLATE) },
                        )
                        KindChip(
                            label = stringResource(R.string.meal_kind_label),
                            selected = meal.kind == "label",
                            onClick = { onSetKind(FrameKind.LABEL) },
                        )
                    }
                }
            }
        }

        // --- Компоненты ---
        if (itemsList.isNotEmpty()) {
            item(key = "items-header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.meal_components), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.meal_readonly_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(itemsList, key = { it.id }) { item ->
                ItemCard(
                    item = item,
                    editable = meal.status == MealStatus.DONE,
                    onRename = { onRenameItem(item) },
                    onDelete = { onDeleteItem(item) },
                )
            }

            // --- Суммарные КБЖУ (только чтение) ---
            item(key = "totals") {
                val totalKcal = itemsList.sumOf { it.kcal }
                val totalProtein = itemsList.sumOf { it.proteinG }
                val totalFat = itemsList.sumOf { it.fatG }
                val totalCarbs = itemsList.sumOf { it.carbsG }
                val totalFiber = itemsList.sumOf { it.fiberG }
                val totalSugar = itemsList.sumOf { it.sugarG }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(stringResource(R.string.meal_totals), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.meal_kcal_value, formatKcal(totalKcal)),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            listOf(
                                stringResource(R.string.meal_macro_protein, formatGrams(totalProtein)),
                                stringResource(R.string.meal_macro_fat, formatGrams(totalFat)),
                                stringResource(R.string.meal_macro_carbs, formatGrams(totalCarbs)),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            listOf(
                                stringResource(R.string.meal_macro_fiber, formatGrams(totalFiber)),
                                stringResource(R.string.meal_macro_sugar, formatGrams(totalSugar)),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item(key = "spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun ItemCard(
    item: ItemEntity,
    editable: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall)
                    val kcalText = stringResource(R.string.meal_kcal_value, formatKcal(item.kcal))
                    val gramsText = item.grams?.let { stringResource(R.string.meal_grams, formatGrams(it)) }
                    Text(
                        if (gramsText != null) "$gramsText · $kcalText" else kcalText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (editable) {
                    IconButton(onClick = onRename) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.cd_rename),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.cd_delete_item),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            HorizontalDivider()
            Text(
                listOf(
                    stringResource(R.string.meal_macro_protein, formatGrams(item.proteinG)),
                    stringResource(R.string.meal_macro_fat, formatGrams(item.fatG)),
                    stringResource(R.string.meal_macro_carbs, formatGrams(item.carbsG)),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                listOf(
                    stringResource(R.string.meal_macro_fiber, formatGrams(item.fiberG)),
                    stringResource(R.string.meal_macro_sugar, formatGrams(item.sugarG)),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.ultraProcessed == 1) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        stringResource(R.string.meal_ultra_processed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    showProgress: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showProgress) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = contentColor)
            } else {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = contentColor)
        }
    }
}
