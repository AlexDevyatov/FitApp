package ru.fitapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fitapp.R

// --- Палитра из design/tokens.json: «спокойная энергия» ---
private val Paper = Color(0xFFF4F1E8)
private val Ink = Color(0xFF17211C)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E6B4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEBDD),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFFC8F26A),
    onSecondary = Color(0xFF243300),
    secondaryContainer = Color(0xFFDFF5B4),
    onSecondaryContainer = Color(0xFF2F3B00),
    tertiary = Color(0xFFFFB77D),
    onTertiary = Color(0xFF4A2600),
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF3A1B00),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFFFF),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9E5D9),
    onSurfaceVariant = Color(0xFF53645C),
    surfaceTint = Color(0xFF0E6B4F),
    inverseSurface = Color(0xFF2C342E),
    inverseOnSurface = Color(0xFFEDF2ED),
    inversePrimary = Color(0xFF7FD8B0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF7E8C85),
    outlineVariant = Color(0xFFDCE3DD),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFDF7),
    surfaceDim = Color(0xFFE4E0D5),
    surfaceContainer = Color(0xFFF0EDE2),
    surfaceContainerHigh = Color(0xFFEAE6D9),
    surfaceContainerHighest = Color(0xFFE4E0D3),
    surfaceContainerLow = Color(0xFFFAF8F1),
    surfaceContainerLowest = Color(0xFFFFFDF7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD8B0),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF005138),
    onPrimaryContainer = Color(0xFFA4F2CE),
    secondary = Color(0xFFC8F26A),
    onSecondary = Color(0xFF243300),
    secondaryContainer = Color(0xFF3A4A00),
    onSecondaryContainer = Color(0xFFDFF5B4),
    tertiary = Color(0xFFFFB77D),
    onTertiary = Color(0xFF4A2600),
    tertiaryContainer = Color(0xFF6B3A00),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF101613),
    onBackground = Color(0xFFE3EAE4),
    surface = Color(0xFF181F1B),
    onSurface = Color(0xFFE3EAE4),
    surfaceVariant = Color(0xFF3F4943),
    onSurfaceVariant = Color(0xFFB7C5BC),
    surfaceTint = Color(0xFF7FD8B0),
    inverseSurface = Color(0xFFE3EAE4),
    inverseOnSurface = Color(0xFF2C342E),
    inversePrimary = Color(0xFF0E6B4F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8A9A91),
    outlineVariant = Color(0xFF3F4943),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF353C37),
    surfaceDim = Color(0xFF101613),
    surfaceContainer = Color(0xFF232B26),
    surfaceContainerHigh = Color(0xFF2E3731),
    surfaceContainerHighest = Color(0xFF39423C),
    surfaceContainerLow = Color(0xFF1F2722),
    surfaceContainerLowest = Color(0xFF0B100D),
)

/** Manrope — гарнитура редизайна; цифры табличные, чтобы счётчики не «прыгали». */
val FitAppFontFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = FitAppFontFamily,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
)

/** Типографика из tokens.json: крупные числа, тихие подписи. */
val FitAppTypography = Typography(
    displayLarge = style(57, 64, FontWeight.Bold, (-0.5).toDouble()),
    displayMedium = style(45, 52, FontWeight.Bold, (-0.4).toDouble()),
    displaySmall = style(36, 44, FontWeight.Bold, (-0.3).toDouble()),
    headlineLarge = style(32, 40, FontWeight.Bold, (-0.4).toDouble()),
    headlineMedium = style(28, 36, FontWeight.Bold, (-0.2).toDouble()),
    headlineSmall = style(24, 32, FontWeight.Bold),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.1),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.1),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.2),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.2),
    bodySmall = style(12, 16, FontWeight.Normal, 0.3),
    labelLarge = style(15, 20, FontWeight.SemiBold, 0.1),
    labelMedium = style(12, 16, FontWeight.Medium, 0.4),
    labelSmall = style(11, 16, FontWeight.Medium, 0.4),
)

val FitAppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

/** Тема приложения. Dynamic color намеренно отключён: бренд узнаваем по паре изумруд/крем. */
@Composable
fun FitAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = FitAppTypography,
        shapes = FitAppShapes,
        content = content,
    )
}
