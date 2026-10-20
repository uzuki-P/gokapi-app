package com.uzukip.gokapi.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.uzukip.gokapi.desktop.data.ColorSource
import com.uzukip.gokapi.desktop.data.Settings
import com.uzukip.gokapi.desktop.data.ThemeMode
import com.uzukip.gokapi.desktop.platform.rememberSystemThemeIsDark

/** Cyan 800, the default brand seed (same as the Android app). */
const val DEFAULT_SEED = "#00838F"

/** Preset seeds for the custom theme picker, the Android app's set. */
val PRESET_SEEDS = listOf(
    "#E91E63", "#E65100", "#F9A825", "#558B2F", "#2E7D32", "#00838F",
    "#1565C0", "#3F51B5", "#6A1B9A", "#AD1457", "#5D4037", "#424242",
    "#D81B60", "#C62828", "#F4511E", "#FB8C00", "#9E9D24",
    "#1B5E20", "#00695C", "#00ACC1", "#039BE5", "#1E88E5",
    "#3949AB", "#5E35B1", "#8E24AA", "#FF6F00", "#6D4C41",
    "#546E7A", "#757575", "#212121",
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val GokapiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    largeIncreased = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

/** Parses a `#RRGGBB` hex, falling back to the default seed. */
fun parseSeedColor(hex: String): Color =
    hex.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: Color(0xFF00838F)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GokapiTheme(settings: Settings, content: @Composable () -> Unit) {
    val systemDark by rememberSystemThemeIsDark()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val seed = parseSeedColor(if (settings.colorSource == ColorSource.USER) settings.userSeed ?: DEFAULT_SEED else DEFAULT_SEED)
    val scheme = remember(seed, dark) {
        dynamicColorScheme(seed, dark, false, style = PaletteStyle.TonalSpot, contrastLevel = 0.0)
    }
    // One theme call on every path, so settings changes keep the content's state.
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = GokapiShapes,
        typography = GokapiTypography,
        content = content,
    )
}
