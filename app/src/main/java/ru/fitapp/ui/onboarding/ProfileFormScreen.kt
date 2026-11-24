package ru.fitapp.ui.onboarding

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import ru.fitapp.R
import ru.fitapp.domain.Activity
import ru.fitapp.domain.Sex
import ru.fitapp.ui.factoryOf
import ru.fitapp.ui.fitApp

/**
 * Онбординг: анкета (§9). Пол, возраст, рост, вес, активность (4 коэффициента словами),
 * дефицит 15–20%. На финише — рассчитанная цель (ккал + Б/Ж/У) через domain/Tdee,
 * при срабатывании порога безопасности — пометка.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (editMode) R.string.profile_title_edit else R.string.profile_title_onboarding,
                        ),
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!editMode) {
                Text(
                    stringResource(R.string.profile_caption),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // --- Пол ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.profile_sex), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.sex == Sex.MALE,
                        onClick = { vm.onSexChange(Sex.MALE) },
                        label = { Text(stringResource(R.string.profile_sex_male)) },
                    )
                    FilterChip(
                        selected = state.sex == Sex.FEMALE,
                        onClick = { vm.onSexChange(Sex.FEMALE) },
                        label = { Text(stringResource(R.string.profile_sex_female)) },
                    )
                }
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

            // --- Активность: 4 коэффициента словами (§3: 1.2 / 1.375 / 1.5 / 1.725) ---
            Column {
                Text(stringResource(R.string.profile_activity), style = MaterialTheme.typography.titleSmall)
                Activity.entries.forEach { activity ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.onActivityChange(activity) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = state.activity == activity,
                            onClick = { vm.onActivityChange(activity) },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(activityLabelRes(activity)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            // --- Дефицит 15–20% ---
            Column {
                Text(
                    stringResource(R.string.profile_deficit_title, state.deficitPct),
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = state.deficitPct.toFloat(),
                    onValueChange = { vm.onDeficitChange(it.roundToInt()) },
                    valueRange = 15f..20f,
                    steps = 4,
                )
                Text(
                    stringResource(R.string.profile_deficit_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // --- Рассчитанная цель ---
            androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.profile_goal_title), style = MaterialTheme.typography.titleMedium)
                    val goals = state.goals
                    if (goals != null) {
                        Text(
                            stringResource(R.string.profile_goal_kcal, goals.targets.kcal),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(
                                R.string.profile_goal_macros,
                                goals.targets.proteinG,
                                goals.targets.fatG,
                                goals.targets.carbsG,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (goals.raisedToSafetyFloor) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.goal_raised_to_safety_floor),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    } else {
                        val anyFilled = state.age.isNotEmpty() || state.height.isNotEmpty() || state.weight.isNotEmpty()
                        Text(
                            stringResource(
                                if (anyFilled) R.string.profile_validation else R.string.profile_goal_preview_hint,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Button(
                onClick = vm::save,
                enabled = state.goals != null && !state.saved,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(if (editMode) R.string.action_save else R.string.profile_save_onboarding),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
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
        label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

private fun activityLabelRes(activity: Activity): Int = when (activity) {
    Activity.SEDENTARY -> R.string.profile_activity_low
    Activity.LIGHT -> R.string.profile_activity_light
    Activity.MODERATE -> R.string.profile_activity_moderate
    Activity.ACTIVE -> R.string.profile_activity_high
}
