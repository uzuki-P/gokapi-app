package com.uzukip.gokapi.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/** M3 emphasized-decelerate curve, for values that should land without overshoot. */
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** M3 emphasized-accelerate curve, for exits that leave quickly and cleanly. */
val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** Gap between rows of a segmented group (M3 Expressive lists). */
val SegmentGap = 2.dp

/** Per-corner radii, so a shape can morph one corner at a time. */
@Immutable
data class Corners(val topStart: Dp, val topEnd: Dp, val bottomEnd: Dp, val bottomStart: Dp) {
    constructor(all: Dp) : this(all, all, all, all)

    fun toShape() = RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
}

/**
 * Corners for row [index] of [count] in a segmented group: the group's outer
 * edge is round, the seams between rows are tight.
 */
fun segmentCorners(index: Int, count: Int, outer: Dp = 24.dp, inner: Dp = 6.dp): Corners {
    val first = index == 0
    val last = index == count - 1
    return Corners(
        topStart = if (first) outer else inner,
        topEnd = if (first) outer else inner,
        bottomEnd = if (last) outer else inner,
        bottomStart = if (last) outer else inner,
    )
}

/** A column of segmented rows separated by [SegmentGap]. */
@Composable
fun SegmentedGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SegmentGap),
        content = content,
    )
}

/**
 * Surface whose corners morph toward [pressedCorners] while held, the M3
 * Expressive shape-change press feedback, plus the shared press spring.
 * Segmented rows round out fully on press; standalone tiles can square off.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MorphSurface(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    corners: Corners = Corners(20.dp),
    pressedCorners: Corners = Corners(28.dp),
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    pressedScale: Float = 0.98f,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val target = if (pressed && onClick != null) pressedCorners else corners
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val topStart by animateDpAsState(target.topStart, spec, label = "ts")
    val topEnd by animateDpAsState(target.topEnd, spec, label = "te")
    val bottomEnd by animateDpAsState(target.bottomEnd, spec, label = "be")
    val bottomStart by animateDpAsState(target.bottomStart, spec, label = "bs")
    val shape = RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)
    Surface(
        modifier = modifier
            .pressScale(source, pressedScale)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = source,
                        indication = ripple(),
                        enabled = enabled,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = color,
        contentColor = contentColor,
        border = border,
        content = content,
    )
}

/**
 * [Shape] drawn from a [Morph] at [progress] (0 = start polygon, 1 = end),
 * scaled to the bounds and centered. Rebuilt per progress value, so animate
 * the progress and pass it in.
 */
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotation: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress, Path())
        path.transform(
            Matrix().apply {
                scale(size.width, size.height)
            },
        )
        val bounds = path.getBounds()
        path.translate(androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f) - bounds.center)
        if (rotation != 0f) {
            path.transform(
                Matrix().apply {
                    translate(size.width / 2f, size.height / 2f)
                    rotateZ(rotation)
                    translate(-size.width / 2f, -size.height / 2f)
                },
            )
        }
        return Outline.Generic(path)
    }
}

/** Remembers a [Morph] between two Material shapes. */
@Composable
fun rememberMorph(start: RoundedPolygon, end: RoundedPolygon): Morph =
    remember(start, end) { Morph(start, end) }

/**
 * Badge whose outline morphs from a circle to a scalloped cookie when
 * [selected], with a slight spin: the M3 Expressive selected-state shape.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingBadge(
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    start: RoundedPolygon = MaterialShapes.Circle,
    end: RoundedPolygon = MaterialShapes.Cookie9Sided,
    content: @Composable () -> Unit,
) {
    val morph = rememberMorph(start, end)
    val progress by animateFloatAsState(
        if (selected) 1f else 0f,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "badgeMorph",
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = progress * 40f }
            .background(color, MorphShape(morph, progress.coerceIn(0f, 1f))),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { rotationZ = -progress * 40f }) { content() }
    }
}

/**
 * Empty state: glyph inside a soft-burst shape that springs in, then a title
 * and message.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyState(
    glyph: String,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, androidx.compose.animation.core.spring(0.5f, 300f)) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(88.dp)
                .graphicsLayer {
                    scaleX = entrance.value
                    scaleY = entrance.value
                    rotationZ = (1f - entrance.value) * -90f
                }
                .background(MaterialTheme.colorScheme.secondaryContainer, MaterialShapes.SoftBurst.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            MciIcon(glyph, 36.dp, MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Box(Modifier.padding(top = 8.dp)) { action() }
        }
    }
}
