package com.secureledger.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/** SecureLedger's deliberately small palette: neutral surfaces, muted blue, semantic status. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF315968),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E7EC),
    onPrimaryContainer = Color(0xFF102F3B),
    inversePrimary = Color(0xFFA6CAD5),
    secondary = Color(0xFF52656B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE7E9),
    onSecondaryContainer = Color(0xFF253B41),
    tertiary = Color(0xFF63706A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE2E9E3),
    onTertiaryContainer = Color(0xFF2E3D35),
    background = Color(0xFFFAFBF9),
    onBackground = Color(0xFF1B262A),
    surface = Color(0xFFFAFBF9),
    onSurface = Color(0xFF1B262A),
    surfaceDim = Color(0xFFD8DEDC),
    surfaceBright = Color(0xFFFAFBF9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F7F5),
    surfaceContainer = Color(0xFFEEF2F0),
    surfaceContainerHigh = Color(0xFFE8EEEB),
    surfaceContainerHighest = Color(0xFFE1E8E5),
    surfaceVariant = Color(0xFFE7ECEA),
    onSurfaceVariant = Color(0xFF526066),
    surfaceTint = Color(0xFF315968),
    inverseSurface = Color(0xFF2B3539),
    inverseOnSurface = Color(0xFFF1F4F1),
    error = Color(0xFFAD4048),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEE0),
    onErrorContainer = Color(0xFF571C25),
    outline = Color(0xFF8A9A9D),
    outlineVariant = Color(0xFFCCD7D8),
    scrim = Color.Black
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA6CAD5),
    onPrimary = Color(0xFF143843),
    primaryContainer = Color(0xFF254B59),
    onPrimaryContainer = Color(0xFFD7E7EC),
    inversePrimary = Color(0xFF315968),
    secondary = Color(0xFFB4C9CC),
    onSecondary = Color(0xFF233C42),
    secondaryContainer = Color(0xFF394F55),
    onSecondaryContainer = Color(0xFFDCE7E9),
    tertiary = Color(0xFFC3D2C7),
    onTertiary = Color(0xFF2C4032),
    tertiaryContainer = Color(0xFF405649),
    onTertiaryContainer = Color(0xFFE2E9E3),
    background = Color.Black,
    onBackground = Color(0xFFE1E9E8),
    surface = Color.Black,
    onSurface = Color(0xFFE1E9E8),
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF222229),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF101014),
    surfaceContainer = Color(0xFF18181E),
    surfaceContainerHigh = Color(0xFF222229),
    surfaceContainerHighest = Color(0xFF2D2D35),
    surfaceVariant = Color(0xFF3E4A4D),
    onSurfaceVariant = Color(0xFFBECACC),
    surfaceTint = Color(0xFFA6CAD5),
    inverseSurface = Color(0xFFE1E9E8),
    inverseOnSurface = Color(0xFF263236),
    error = Color(0xFFFFB3B6),
    onError = Color(0xFF681F29),
    errorContainer = Color(0xFF872D36),
    onErrorContainer = Color(0xFFFFDADB),
    outline = Color(0xFF89999C),
    outlineVariant = Color(0xFF3D4B4E),
    scrim = Color.Black
)

@Immutable
data class LedgerSemanticColors(
    val income: Color,
    val expense: Color,
    val warning: Color,
    val success: Color
)

private val LightSemanticColors = LedgerSemanticColors(
    income = Color(0xFF287154),
    expense = Color(0xFFAD4048),
    warning = Color(0xFF9D6A27),
    success = Color(0xFF287154)
)
private val DarkSemanticColors = LedgerSemanticColors(
    income = Color(0xFF89CBA8),
    expense = Color(0xFFFFB3B6),
    warning = Color(0xFFE3B879),
    success = Color(0xFF89CBA8)
)

val LocalLedgerSemanticColors = staticCompositionLocalOf { LightSemanticColors }

object LedgerSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val page = 20.dp
}

object LedgerMotion {
    const val shortMillis = 150
    const val mediumMillis = 250
}

private val LedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val LedgerTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
)

@Composable
fun SecureLedgerTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val preferences = remember(context) {
        (context.applicationContext as? com.secureledger.SecureLedgerApplication)?.appearancePreferences
            ?: AppearancePreferences(context.applicationContext)
    }
    val appearance by preferences.state.collectAsState()
    val dark = darkTheme ?: when (appearance.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val accent = Color(android.graphics.Color.parseColor("#${appearance.accentHex}"))
    // Guarantee readable primary controls even for very pale or very dark custom accents.
    var primary = if (dark) lerp(accent, Color.White, 0.42f) else
        if (accent.luminance() > 0.28f) lerp(accent, Color.Black, 0.48f) else accent
    repeat(16) {
        if (dark && primary.luminance() < 0.20f) primary = lerp(primary, Color.White, 0.10f)
        if (!dark && primary.luminance() > 0.18f) primary = lerp(primary, Color.Black, 0.10f)
    }
    SideEffect {
        if (android.os.Build.VERSION.SDK_INT >= 29) view.isForceDarkAllowed = false
        (context as? android.app.Activity)?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val container = lerp(accent, if (dark) Color.Black else Color.White, if (dark) 0.68f else 0.88f)
    val colors = (if (dark) DarkColors else LightColors).copy(
        primary = primary, onPrimary = if (dark) Color.Black else Color.White,
        primaryContainer = container, onPrimaryContainer = if (dark) Color(0xFFF3F1FF) else Color(0xFF211D35),
        secondary = primary, onSecondary = if (dark) Color.Black else Color.White,
        secondaryContainer = container, onSecondaryContainer = if (dark) Color(0xFFF3F1FF) else Color(0xFF211D35),
        surfaceTint = Color.Transparent, inversePrimary = accent
    )
    CompositionLocalProvider(LocalLedgerSemanticColors provides if (dark) DarkSemanticColors else LightSemanticColors) {
        MaterialTheme(
            colorScheme = colors,
            typography = LedgerTypography,
            shapes = LedgerShapes,
            content = content
        )
    }
}

@Composable
fun LedgerSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
}

@Composable
fun LedgerAmountText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(text, modifier = modifier, color = color,
        style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"))
}
