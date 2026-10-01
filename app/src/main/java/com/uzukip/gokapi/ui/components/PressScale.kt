package com.uzukip.gokapi.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Press feel for custom surfaces: a spring scale to [pressedScale] using the
 * expressive motion scheme's fast spatial spring, so custom tiles bounce the
 * same way the M3 components do.
 */
fun Modifier.pressScale(
    pressedScale: Float = 0.96f,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    pressedScaleModifier(interactionSource, pressedScale)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

/**
 * Scale-only variant for components that own their click
 * (`Surface(onClick)`, M3 buttons, `clickable` rows): pass the same
 * [MutableInteractionSource] to both this and the click handler.
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f,
): Modifier = composed { pressedScaleModifier(interactionSource, pressedScale) }

private fun Modifier.pressedScaleModifier(
    interactionSource: InteractionSource,
    pressedScale: Float,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
