package dev.eversorhn.gait.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val GaitColorScheme = darkColorScheme(
    primary = Brass,
    onPrimary = Ink,
    secondary = Cyan,
    onSecondary = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = Ink2,
    onSurface = TextPrimary,
    surfaceVariant = Ink3,
    onSurfaceVariant = TextDim,
    error = Alert,
    onError = TextPrimary,
    outline = Line,
    outlineVariant = LineSoft,
)

private val GaitTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

@Composable
fun GaitTheme(content: @Composable () -> Unit) {
    // Single, deliberate dark world — matches the HTML demo, not device light/dark mode.
    MaterialTheme(
        colorScheme = GaitColorScheme,
        typography = GaitTypography,
        content = content,
    )
}
