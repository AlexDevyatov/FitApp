package ru.fitapp.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import ru.fitapp.R
import ru.fitapp.domain.Activity
import ru.fitapp.domain.Sex
import ru.fitapp.ui.common.PrimaryButton
import ru.fitapp.ui.common.ScreenTopBar
import ru.fitapp.ui.common.SegmentedControl
import ru.fitapp.ui.factoryOf
import ru.fitapp.ui.fitApp

/**
 * Онбординг: анкета. Прогресс, сегменты пола, три поля, карточки активности,
 * дефицит 15–20% и итоговая цель изумрудной карточкой.
 */
@Composable
fun ProfileFormScreen(
    editMode: Boolean,
    onBack: (() -> Unit)?,
    onSaved: () -> Unit,
) {
    val app = fitApp()
    val vm: ProfileFormViewModel = viewModel(factory = factoryOf { ProfileFormViewModel(app) })
    val state by vm.state.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScreenTopBar(
                title = stringResource(
                    if (editMode) R.string.profile_title_edit else R.string.profile_onboarding_title,
                ),
                subtitle = if (!editMode) stringResource(R.string.profile_onboarding_step) else null,
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (!editMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(999.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.66f)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp)),
                    )
                }
                Text(
                    stringResource(R.string.profile_caption),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // --- Пол ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.profile_sex), style = MaterialTheme.typography.titleSmall)
                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.profile_sex_male),
                        stringResource(R.string.profile_sex_female),
                    ),
                    selectedIndex = if (state.sex == Sex.MALE) 0 else 1,
                    onSelect = { index -> vm.onSexChange(if (index == 0) Sex.MALE else Sex.FEMALE) },
                )
            }

            // --- Возраст / рост / вес ---
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    value = state.age,
                    onValueChange = vm::onAgeChange,
                    labelRes = R.string.profile_age,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = state.height,
                    onValueChange = vm::onHeightChange,
                    labelRes = R.string.profile_height,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = state.weight,
                    onValueChange = vm::onWeightChange,
                    labelRes = R.string.profile_weight,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }

            // --- Активность: карточки с иконками, а не радио в столбик ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.profile_activity), style = MaterialTheme.typography.titleSmall)
                Activity.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { activity ->
                            ActivityCard(
                                activity = activity,
                                selected = state.activity == activity,
                                onClick = { vm.onActivityChange(activity) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // --- Дефицит 15–20% ---
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.profile_deficit_title, state.deficitPct),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.profile_deficit_short),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = state.deficitPct.toFloat(),
                    onValueChange = { vm.onDeficitChange(it.roundToInt()) },
                    valueRange = 15f..20f,
                    steps = 4,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
                Text(
                    stringResource(R.string.profile_deficit_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // --- Рассчитанная цель ---
            GoalCard(state)
            PrimaryButton(
                text = stringResource(
                    if (editMode) R.string.action_save else R.string.profile_save_onboarding,
                ),
                onClick = vm::save,
                enabled = state.goals != null && !state.saved,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GoalCard(state: ProfileFormViewModel.FormState) {
    val scheme = MaterialTheme.colorScheme
    val goals = state.goals
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (goals != null) scheme.primary else scheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val content = if (goals != null) scheme.onPrimary else scheme.onSurface
            Text(
                stringResource(R.string.profile_goal_title),
                style = MaterialTheme.typography.labelLarge,
                color = content.copy(alpha = 0.75f),
            )
            if (goals != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        goals.targets.kcal.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        color = content,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.unit_kcal),
                        style = MaterialTheme.typography.titleMedium,
                        color = content.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(
                    stringResource(R.string.profile_goal_macros, goals.targets.proteinG, goals.targets.fatG, goals.targets.carbsG),
                    style = MaterialTheme.typography.bodyMedium,
                    color = content.copy(alpha = 0.9f),
                )
                if (goals.raisedToSafetyFloor) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = content,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.goal_raised_to_safety_floor),
                            style = MaterialTheme.typography.bodySmall,
                            color = content,
                        )
                    }
                }
            } else {
                val anyFilled = state.age.isNotEmpty() || state.height.isNotEmpty() || state.weight.isNotEmpty()
                Text(
                    stringResource(
                        if (anyFilled) R.string.profile_validation else R.string.profile_goal_preview_hint,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActivityCard(
    activity: Activity,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .height(112.dp)
            .background(
                if (selected) scheme.secondaryContainer else scheme.surface,
                RoundedCornerShape(20.dp),
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.outlineVariant,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(
                if (selected) scheme.primary else scheme.surfaceContainerLow,
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                activityIcon(activity),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(activityShortLabelRes(activity)),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(activityDescriptionRes(activity)),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

private fun activityIcon(activity: Activity): ImageVector = when (activity) {
    Activity.SEDENTARY -> Icons.Filled.EventSeat
    Activity.LIGHT -> Icons.AutoMirrored.Filled.DirectionsWalk
    Activity.MODERATE -> Icons.AutoMirrored.Filled.DirectionsRun
    Activity.ACTIVE -> Icons.Filled.FitnessCenter
}

private fun activityShortLabelRes(activity: Activity): Int = when (activity) {
    Activity.SEDENTARY -> R.string.profile_activity_short_low
    Activity.LIGHT -> R.string.profile_activity_short_light
    Activity.MODERATE -> R.string.profile_activity_short_moderate
    Activity.ACTIVE -> R.string.profile_activity_short_high
}

private fun activityDescriptionRes(activity: Activity): Int = when (activity) {
    Activity.SEDENTARY -> R.string.profile_activity_desc_low
    Activity.LIGHT -> R.string.profile_activity_desc_light
    Activity.MODERATE -> R.string.profile_activity_desc_moderate
    Activity.ACTIVE -> R.string.profile_activity_desc_high
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}
