package to.wim.tvfavorites.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import to.wim.tvfavorites.data.ThemeMode

// Fallback palette (no dynamic color, or dynamic color turned off): Material 3 tonal scheme
// generated from the icon's middle gradient stop #E83A7A.
private val LightColors =
    lightColorScheme(
        primary = Color(0xFF8D4A5C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD9E0),
        onPrimaryContainer = Color(0xFF713344),
        secondary = Color(0xFF75565D),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFD9E0),
        onSecondaryContainer = Color(0xFF5B3F45),
        tertiary = Color(0xFF7B5733),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDCBE),
        onTertiaryContainer = Color(0xFF60401E),
        background = Color(0xFFFFF8F7),
        onBackground = Color(0xFF22191B),
        surface = Color(0xFFFFF8F7),
        onSurface = Color(0xFF22191B),
        surfaceVariant = Color(0xFFF3DDE0),
        onSurfaceVariant = Color(0xFF514346),
        outline = Color(0xFF847376),
        outlineVariant = Color(0xFFD6C2C5),
        inverseSurface = Color(0xFF382E30),
        inverseOnSurface = Color(0xFFFEEDEF),
        inversePrimary = Color(0xFFFFB1C3),
        surfaceDim = Color(0xFFE7D6D8),
        surfaceBright = Color(0xFFFFF8F7),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFF0F2),
        surfaceContainer = Color(0xFFFBEAEC),
        surfaceContainerHigh = Color(0xFFF5E4E6),
        surfaceContainerHighest = Color(0xFFEFDEE1),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFFFB1C3),
        onPrimary = Color(0xFF551D2E),
        primaryContainer = Color(0xFF713344),
        onPrimaryContainer = Color(0xFFFFD9E0),
        secondary = Color(0xFFE4BDC4),
        onSecondary = Color(0xFF43292F),
        secondaryContainer = Color(0xFF5B3F45),
        onSecondaryContainer = Color(0xFFFFD9E0),
        tertiary = Color(0xFFEDBD92),
        onTertiary = Color(0xFF472A0A),
        tertiaryContainer = Color(0xFF60401E),
        onTertiaryContainer = Color(0xFFFFDCBE),
        background = Color(0xFF191113),
        onBackground = Color(0xFFEFDEE1),
        surface = Color(0xFF191113),
        onSurface = Color(0xFFEFDEE1),
        surfaceVariant = Color(0xFF514346),
        onSurfaceVariant = Color(0xFFD6C2C5),
        outline = Color(0xFF9E8C8F),
        outlineVariant = Color(0xFF514346),
        inverseSurface = Color(0xFFEFDEE1),
        inverseOnSurface = Color(0xFF382E30),
        inversePrimary = Color(0xFF8D4A5C),
        surfaceDim = Color(0xFF191113),
        surfaceBright = Color(0xFF413738),
        surfaceContainerLowest = Color(0xFF140C0E),
        surfaceContainerLow = Color(0xFF22191B),
        surfaceContainer = Color(0xFF261D1F),
        surfaceContainerHigh = Color(0xFF312829),
        surfaceContainerHighest = Color(0xFF3C3234),
    )

/** The app's colors, for the phone ([AppTheme]) and the TV screens (which map them to TV Material). */
@Composable
fun appColorScheme(mode: ThemeMode, dynamicColor: Boolean): Pair<androidx.compose.material3.ColorScheme, Boolean> {
    val dark =
        when (mode) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
    val context = LocalContext.current
    val colors =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> DarkColors
            else -> LightColors
        }
    return colors to dark
}

@Composable
fun AppTheme(
    mode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val (colors, dark) = appColorScheme(mode, dynamicColor)
    SystemBarsFollowTheme(dark)
    MaterialTheme(colorScheme = colors, content = content)
}
