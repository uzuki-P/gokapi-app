package com.uzukip.gokapi.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.uzukip.gokapi.data.settings.ColorSource
import com.uzukip.gokapi.data.settings.Settings
import com.uzukip.gokapi.data.settings.ThemeMode

/** Cyan 800, the default brand seed. */
const val DEFAULT_SEED = "#00838F"

/**
 * Preset seeds for the custom theme picker (same set as spendr-kt): 12 core
 * colors first, then a full hue-wheel spread (pinks → reds → oranges →
 * greens → cyans → blues → purples → browns → greys).
 */
val PRESET_SEEDS = listOf(
    "#E91E63", "#E65100", "#F9A825", "#558B2F", "#2E7D32", "#00838F",
    "#1565C0", "#3F51B5", "#6A1B9A", "#AD1457", "#5D4037", "#424242",
    // Extended palette (kept at 30 total: 6 full rows of 5)
    "#D81B60", "#C62828", "#F4511E", "#FB8C00", "#9E9D24",
    "#1B5E20", "#00695C", "#00ACC1", "#039BE5", "#1E88E5",
    "#3949AB", "#5E35B1", "#8E24AA", "#FF6F00", "#6D4C41",
    "#546E7A", "#757575", "#212121",
)

/**
 * M3 Expressive corner scale: base steps 8/12/16/20/28, plus the larger
 * 24/32/48 expressive corners used by hero cards and sheets.
 */
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

/** Parses a `#RRGGBB` seed hex, falling back to the default seed. */
fun parseSeedColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF00838F))

fun resolveSeed(settings: Settings): Color {
    val hex = when (settings.colorSource) {
        ColorSource.USER -> settings.userSeed ?: DEFAULT_SEED
        else -> DEFAULT_SEED
    }
    return parseSeedColor(hex)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GokapiTheme(
    settings: Settings,
    content: @Composable () -> Unit,
) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Composable-call structure must not depend on `settings`: the scheme is
    // computed BEFORE anything user-facing in the tree (nav state included),
    // and a settings-driven branch flip here would re-key the whole subtree
    // below and reset rememberSaveable state (nav back stack → Home). Hoist
    // locals so every path performs the same composable calls.
    val context = LocalContext.current
    val scheme: ColorScheme = if (settings.colorSource == ColorSource.WALLPAPER) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
            dark -> darkColorScheme()
            else -> lightColorScheme()
        }
    } else {
        remember(resolveSeed(settings), dark) {
            dynamicColorScheme(
                resolveSeed(settings),
                dark,
                false,
                style = PaletteStyle.TonalSpot,
                contrastLevel = 0.0,
            )
        }
    }

    // System-bar icons follow the app's theme, not the system's, so a forced
    // Light/Dark choice keeps the status bar readable. Called on every path.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = GokapiShapes,
        typography = GokapiTypography,
        content = content,
    )
}
