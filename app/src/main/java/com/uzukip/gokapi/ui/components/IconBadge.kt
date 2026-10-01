package com.uzukip.gokapi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.uzukip.gokapi.R

fun badgeColor(hex: String): Color =
    runCatching { Color(hex.toColorInt()) }.getOrDefault(Color(0xFFA86086))

internal val MciFamily = FontFamily(Font(R.font.mci))

/**
 * Material Community Icons glyph rendered from the bundled font. Pass
 * [contentDescription] for icon-only controls; the glyph itself is a
 * private-use character that screen readers cannot name.
 */
@Composable
fun MciIcon(
    name: String,
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val glyphSizeSp = with(LocalDensity.current) { size.toSp() }
    val glyph = MciGlyphs.glyphString(name) ?: MciGlyphs.glyphString("help-circle") ?: "?"
    Text(
        text = glyph,
        fontFamily = MciFamily,
        fontSize = glyphSizeSp,
        lineHeight = glyphSizeSp,
        color = tint,
        textAlign = TextAlign.Center,
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
    )
}

/**
 * Icon badge: the Material Community Icons glyph in [color], on a 16% tint of
 * that color clipped to the M3 Expressive scalloped cookie shape (pass [shape]
 * to override, e.g. a circle for neutral chips).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IconBadge(
    icon: String,
    color: String,
    size: Dp,
    modifier: Modifier = Modifier,
    glyphSize: Dp = size / 2,
    shape: Shape = MaterialShapes.Cookie9Sided.toShape(),
) {
    val resolved = badgeColor(color)
    val glyphSizeSp = with(LocalDensity.current) { glyphSize.toSp() }
    Box(
        modifier = modifier
            .size(size)
            .background(resolved.copy(alpha = 0.16f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val glyph = MciGlyphs.glyphString(icon) ?: MciGlyphs.glyphString("tag") ?: "?"
        Text(
            text = glyph,
            fontFamily = MciFamily,
            fontSize = glyphSizeSp,
            lineHeight = glyphSizeSp,
            color = resolved,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        if (action != null) action()
    }
}
