package ru.fitapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GreenPrimary = Color(0xFF2E7D32)
private val GreenDarkPrimary = Color(0xFF8BC34A)
private val Accent = Color(0xFF8BC34A)

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    secondary = Accent,
)

private val DarkColors = darkColorScheme(
    primary = GreenDarkPrimary,
    secondary = Accent,
)

/** Минимальная Material3-тема: заглушка MainActivity и будущие экраны (§5 PLAN). */
@Composable
fun FitAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
