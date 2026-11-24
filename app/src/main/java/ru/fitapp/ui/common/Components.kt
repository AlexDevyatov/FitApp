package ru.fitapp.ui.common

import androidx.annotation.StringRes
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import ru.fitapp.R
import ru.fitapp.data.db.MealStatus

/** Ресурс подписи статуса карточки (§6: PENDING/RUNNING/DONE/FAILED/AUTH_ERROR/NOT_FOOD). */
@StringRes
fun statusLabelRes(status: String): Int = when (status) {
    MealStatus.PENDING -> R.string.status_pending
    MealStatus.RUNNING -> R.string.status_running
    MealStatus.DONE -> R.string.status_done
    MealStatus.FAILED -> R.string.status_failed
    MealStatus.AUTH_ERROR -> R.string.status_auth_error
    MealStatus.NOT_FOOD -> R.string.status_not_food
    else -> R.string.status_pending
}

/** Ресурс названия типа кадра; null, если тип ещё не определён. */
@StringRes
fun kindLabelRes(kind: String?): Int? = when (kind) {
    "dish" -> R.string.day_kind_dish
    "plate" -> R.string.day_kind_plate
    "label" -> R.string.day_kind_label
    else -> null
}

/** Статус карточки виден явно: цвет + подпись (§9, экран «Сегодня»). */
@Composable
fun MealStatusChip(status: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        MealStatus.PENDING -> scheme.surfaceVariant to scheme.onSurfaceVariant
        MealStatus.RUNNING -> scheme.secondaryContainer to scheme.onSecondaryContainer
        MealStatus.DONE -> scheme.primaryContainer to scheme.onPrimaryContainer
        MealStatus.FAILED -> scheme.errorContainer to scheme.onErrorContainer
        MealStatus.AUTH_ERROR -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        MealStatus.NOT_FOOD -> scheme.surfaceVariant to scheme.onSurfaceVariant
        else -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = container) {
        Text(
            text = stringResource(statusLabelRes(status)),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Карточка-секция с заголовком — единый стиль блоков отчёта и настроек. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Пустое состояние экрана с иконкой, подсказкой и необязательным действием. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** Миниатюра фото карточки (Coil); плейсхолдер, если фото отсутствует. */
@Composable
fun MealThumbnail(photoPath: String?, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    if (photoPath != null) {
        AsyncImage(
            model = File(photoPath),
            contentDescription = stringResource(R.string.cd_meal_photo),
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        Box(
            modifier = modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2),
            )
        }
    }
}

/** Отступ снизу, чтобы FAB/нижняя панель не перекрывали конец списка. */
val listBottomPadding = PaddingValues(bottom = 96.dp)
