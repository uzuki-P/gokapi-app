package com.uzukip.gokapi.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.uzukip.gokapi.R

/**
 * Google Sans Flex (OFL, see assets/licenses), subset to Latin with only the
 * `wght` and `ROND` axes kept. Text uses the square-cornered cut; display
 * headlines and emphasized titles use the fully rounded cut, the M3 Expressive voice.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun flex(weight: Int, round: Float): Font = Font(
    R.font.google_sans_flex,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("ROND", round),
    ),
)

private val Weights = listOf(400, 500, 600, 700, 800)

val GokapiFontFamily = FontFamily(Weights.map { flex(it, round = 0f) })

/** Rounded cut for hero headlines and emphasized titles. */
val GokapiRoundedFamily = FontFamily(Weights.map { flex(it, round = 100f) })

private fun TextStyle.text() = copy(fontFamily = GokapiFontFamily)

/** Emphasized styles: rounded, heavier, slightly tighter — the M3E accent voice. */
private fun TextStyle.accent(weight: FontWeight, tracking: Float = -0.01f) = copy(
    fontFamily = GokapiRoundedFamily,
    fontWeight = weight,
    letterSpacing = tracking.em,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val GokapiTypography: Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.accent(FontWeight.SemiBold, -0.02f),
        displayMedium = base.displayMedium.accent(FontWeight.SemiBold, -0.02f),
        displaySmall = base.displaySmall.accent(FontWeight.SemiBold, -0.015f),
        headlineLarge = base.headlineLarge.accent(FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.accent(FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.accent(FontWeight.SemiBold),
        titleLarge = base.titleLarge.text().copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.text(),
        titleSmall = base.titleSmall.text(),
        bodyLarge = base.bodyLarge.text(),
        bodyMedium = base.bodyMedium.text(),
        bodySmall = base.bodySmall.text(),
        labelLarge = base.labelLarge.text(),
        labelMedium = base.labelMedium.text(),
        labelSmall = base.labelSmall.text(),
        displayLargeEmphasized = base.displayLargeEmphasized.accent(FontWeight.Bold, -0.02f),
        displayMediumEmphasized = base.displayMediumEmphasized.accent(FontWeight.Bold, -0.02f),
        displaySmallEmphasized = base.displaySmallEmphasized.accent(FontWeight.Bold, -0.015f),
        headlineLargeEmphasized = base.headlineLargeEmphasized.accent(FontWeight.Bold),
        headlineMediumEmphasized = base.headlineMediumEmphasized.accent(FontWeight.Bold),
        headlineSmallEmphasized = base.headlineSmallEmphasized.accent(FontWeight.Bold),
        titleLargeEmphasized = base.titleLargeEmphasized.accent(FontWeight.Bold, 0f),
        titleMediumEmphasized = base.titleMediumEmphasized.accent(FontWeight.Bold, 0f),
        titleSmallEmphasized = base.titleSmallEmphasized.accent(FontWeight.Bold, 0f),
        bodyLargeEmphasized = base.bodyLargeEmphasized.text().copy(fontWeight = FontWeight.SemiBold),
        bodyMediumEmphasized = base.bodyMediumEmphasized.text().copy(fontWeight = FontWeight.SemiBold),
        bodySmallEmphasized = base.bodySmallEmphasized.text().copy(fontWeight = FontWeight.SemiBold),
        labelLargeEmphasized = base.labelLargeEmphasized.text().copy(fontWeight = FontWeight.Bold),
        labelMediumEmphasized = base.labelMediumEmphasized.text().copy(fontWeight = FontWeight.Bold),
        labelSmallEmphasized = base.labelSmallEmphasized.text().copy(fontWeight = FontWeight.Bold),
    )
}
