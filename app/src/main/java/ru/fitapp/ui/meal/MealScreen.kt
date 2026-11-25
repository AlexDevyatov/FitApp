package ru.fitapp.ui.meal

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import ru.fitapp.ui.common.ScreenTopBar
import ru.fitapp.ui.common.SectionCard
import ru.fitapp.ui.common.SegmentedControl
import ru.fitapp.ui.common.formatGrams
import ru.fitapp.ui.common.formatKcal
import ru.fitapp.ui.common.formatTimeOfDay
import ru.fitapp.ui.factoryOf
import ru.fitapp.ui.fitApp

/**
 * Карточка приёма пищи: фото-hero, что на тарелке, правка названий и типа кадра,
 * итог изумрудной карточкой. Удаление карточки — в overflow-меню.
 */
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
    var showMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ItemEntity?>(null) }
    var deleteItemTarget by remember { mutableStateOf<ItemEntity?>(null) }

    LaunchedEffect(deleted) {
        if (deleted) onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.meal_title),
                onBack = onBack,
                actions = {
                    if (mealWithItems != null) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, stringResource(R.string.meal_more))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.meal_delete_card),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDeleteMealDialog = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val data = mealWithItems
        if (data == null) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
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
            shape = RoundedCornerShape(28.dp),
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
            shape = RoundedCornerShape(28.dp),
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
                ) { Text(stringResource(R.string.action_save)) }
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
            shape = RoundedCornerShape(28.dp),
            title = { Text(stringResource(R.string.meal_delete_item_title)) },
            text = { Text(stringResource(R.string.meal_delete_item_text, target.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteItem(target.id)
                        deleteItemTarget = null
                    },
                ) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
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
    padding: PaddingValues,
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
    val scheme = MaterialTheme.colorScheme
    val totalKcal = itemsList.sumOf { it.kcal }
    val totalProtein = itemsList.sumOf { it.proteinG }
    val totalFat = itemsList.sumOf { it.fatG }
    val totalCarbs = itemsList.sumOf { it.carbsG }
    val totalFiber = itemsList.sumOf { it.fiberG }
    val totalSugar = itemsList.sumOf { it.sugarG }

    LazyColumn(
        modifier = Modifier.padding(padding).fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (meal.photoPath != null) {
            item(key = "photo") {
                AsyncImage(
                    model = File(meal.photoPath),
                    contentDescription = stringResource(R.string.cd_meal_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(24.dp)),
                )
            }
        }

        item(key = "status") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MealStatusChip(meal.status)
                Text(
                    formatTimeOfDay(meal.takenAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                meal.confidence?.let { confidence ->
                    Text(
                        stringResource(R.string.meal_confidence, (confidence * 100).roundToInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        when (meal.status) {
            MealStatus.PENDING, MealStatus.RUNNING -> item(key = "processing") {
                InfoCard(
                    text = stringResource(R.string.meal_processing),
                    containerColor = scheme.secondaryContainer,
                    contentColor = scheme.onSecondaryContainer,
                    showProgress = meal.status == MealStatus.RUNNING,
                )
            }

            MealStatus.AUTH_ERROR -> item(key = "auth") {
                StatusCard(containerColor = scheme.errorContainer, contentColor = scheme.onErrorContainer) {
                    Text(stringResource(R.string.meal_auth_banner), style = MaterialTheme.typography.bodyMedium)
                    if (!meal.errorMessage.isNullOrBlank()) {
                        Text(meal.errorMessage ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.meal_auth_banner_action)) }
                        RetryTextButton(onRetry)
                    }
                }
            }

            MealStatus.FAILED -> item(key = "failed") {
                StatusCard(containerColor = scheme.errorContainer, contentColor = scheme.onErrorContainer) {
                    Text(
                        meal.errorMessage ?: stringResource(R.string.status_failed),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RetryTextButton(onRetry)
                    }
                }
            }

            MealStatus.NOT_FOOD -> item(key = "not-food") {
                StatusCard(containerColor = scheme.tertiaryContainer, contentColor = scheme.onTertiaryContainer) {
                    Text(stringResource(R.string.meal_not_food_banner), style = MaterialTheme.typography.bodyMedium)
                    if (!meal.errorMessage.isNullOrBlank()) {
                        Text(meal.errorMessage ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RetryTextButton(onRetry)
                        TextButton(onClick = onDeleteMeal) { Text(stringResource(R.string.meal_delete_card)) }
                    }
                }
            }
        }

        if (itemsList.isNotEmpty()) {
            item(key = "components") {
                SectionCard(
                    title = stringResource(R.string.meal_components),
                    subtitle = stringResource(R.string.meal_readonly_hint),
                ) {
                    Column {
                        itemsList.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(color = scheme.outlineVariant)
                            ItemRow(
                                item = item,
                                editable = meal.status == MealStatus.DONE,
                                onRename = { onRenameItem(item) },
                                onDelete = { onDeleteItem(item) },
                            )
                        }
                    }
                }
            }

            item(key = "totals") {
                Card(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary,
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.meal_totals),
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onPrimary.copy(alpha = 0.8f),
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                formatKcal(totalKcal),
                                style = MaterialTheme.typography.headlineLarge,
                                color = scheme.onPrimary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.unit_kcal),
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.onPrimary.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        Text(
                            listOf(
                                stringResource(R.string.meal_macro_protein, formatGrams(totalProtein)),
                                stringResource(R.string.meal_macro_fat, formatGrams(totalFat)),
                                stringResource(R.string.meal_macro_carbs, formatGrams(totalCarbs)),
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onPrimary,
                        )
                        Text(
                            listOf(
                                stringResource(R.string.meal_macro_fiber, formatGrams(totalFiber)),
                                stringResource(R.string.meal_macro_sugar, formatGrams(totalSugar)),
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onPrimary.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }

        if (meal.status == MealStatus.DONE) {
            item(key = "kind") {
                SectionCard(title = stringResource(R.string.meal_kind)) {
                    val selected = when (meal.kind) {
                        "dish" -> 0
                        "plate" -> 1
                        "label" -> 2
                        else -> -1
                    }
                    SegmentedControl(
                        options = listOf(
                            stringResource(R.string.meal_kind_dish),
                            stringResource(R.string.meal_kind_plate),
                            stringResource(R.string.meal_kind_label),
                        ),
                        selectedIndex = selected,
                        onSelect = { index ->
                            onSetKind(
                                when (index) {
                                    0 -> FrameKind.DISH
                                    1 -> FrameKind.PLATE
                                    else -> FrameKind.LABEL
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: ItemEntity,
    editable: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.grams?.let {
                        Text(
                            stringResource(R.string.meal_grams, formatGrams(it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                        Text("·", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    Text(
                        stringResource(R.string.meal_kcal_value, formatKcal(item.kcal)),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            if (editable) {
                IconButton(onClick = onRename, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.cd_rename),
                        modifier = Modifier.size(18.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete_item),
                        modifier = Modifier.size(18.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            listOf(
                stringResource(R.string.meal_macro_protein, formatGrams(item.proteinG)),
                stringResource(R.string.meal_macro_fat, formatGrams(item.fatG)),
                stringResource(R.string.meal_macro_carbs, formatGrams(item.carbsG)),
                stringResource(R.string.meal_macro_fiber, formatGrams(item.fiberG)),
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        if (item.ultraProcessed == 1) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = scheme.tertiaryContainer,
            ) {
                Text(
                    stringResource(R.string.meal_ultra_processed),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun Card(
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        content()
    }
}

@Composable
private fun RetryTextButton(onRetry: () -> Unit) {
    TextButton(onClick = onRetry) {
        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.meal_retry))
    }
}

@Composable
private fun StatusCard(
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun InfoCard(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    showProgress: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
            } else {
                Icon(Icons.Filled.Info, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = contentColor)
        }
    }
}
