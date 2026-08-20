package dev.eversorhn.gait.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
    outline = BrassDim,
    outlineVariant = LineSoft,
)

/**
 * Two voices, matching the HTML concept demo: a condensed display face for headlines
 * (terminal-poster feel) and monospace for anything that reads as data or a control label
 * (HUD/statusbar feel). No bundled font assets -- both are system generic families every
 * Android device ships with.
 */
private val DisplayFont = FontFamily(
    androidx.compose.ui.text.font.Font(
        familyName = DeviceFontFamilyName("sans-serif-condensed"),
        weight = FontWeight.Black,
    )
)
private val MonoFont = FontFamily.Monospace

private val GaitTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        letterSpacing = 0.01.em,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
    ),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.08.em,
    ),
    labelMedium = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.06.em,
    ),
    labelSmall = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.1.em,
    ),
)

/** Small, technical rounding -- a console panel, not a Material pill button. */
private val GaitShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
)

@Composable
fun GaitTheme(content: @Composable () -> Unit) {
    // Single, deliberate dark world — matches the HTML demo, not device light/dark mode.
    MaterialTheme(
        colorScheme = GaitColorScheme,
        typography = GaitTypography,
        shapes = GaitShapes,
        content = content,
    )
}
