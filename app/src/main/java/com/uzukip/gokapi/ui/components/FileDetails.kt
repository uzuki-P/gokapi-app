package com.uzukip.gokapi.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uzukip.gokapi.data.server.GokapiFile

/** Window width from which the file screens show the list and a details pane side by side. */
val TwoPaneMinWidth: Dp = 840.dp

/** An action in [FileDetailsPane]. */
class DetailAction(val glyph: String, val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * The details pane of the wide layout, shared by the Android app on tablets and the desktop
 * app: a large preview, the file's name and facts, a primary action, then the other actions
 * as a segmented list. With no file it shows a hint. [preview] draws the image of an image
 * file (Coil on Android, the desktop's thumbnail loader); null falls back to the type badge.
 * Pure Compose, so the desktop app compiles it from here (desktop/build.gradle.kts).
 */
@Composable
fun FileDetailsPane(
    file: GokapiFile?,
    primary: DetailAction?,
    actions: List<DetailAction>,
    progress: Float?,
    modifier: Modifier = Modifier,
    badge: @Composable (() -> Unit)? = null,
    preview: (@Composable (GokapiFile) -> Unit)? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        val enter = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val exit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            targetState = file,
            contentKey = { it?.id },
            transitionSpec = { fadeIn(enter) togetherWith fadeOut(exit) },
            label = "details",
        ) { shown ->
            if (shown == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        glyph = "file-eye-outline",
                        title = "No file selected",
                        message = "Select a file to see its details and actions here.",
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                Details(shown, primary, actions, progress, badge, preview)
            }
        }
    }
}

@Composable
private fun Details(
    file: GokapiFile,
    primary: DetailAction?,
    actions: List<DetailAction>,
    progress: Float?,
    badge: @Composable (() -> Unit)?,
    preview: (@Composable (GokapiFile) -> Unit)?,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
        ) {
            BoxWithConstraints(contentAlignment = Alignment.Center) {
                if (preview != null && file.isImage && file.hotlinkId.isNotBlank() && !file.encrypted) {
                    preview(file)
                } else {
                    IconBadge(
                        file.glyph, if (file.isApk) "#3D8075" else "#6376A8",
                        (maxHeight * 0.45f).coerceAtMost(120.dp),
                        shape = MaterialShapes.Cookie9Sided.toShape(),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(file.name, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 3, overflow = TextOverflow.Ellipsis)
            badge?.invoke()
        }
        AnimatedVisibility(
            visible = progress != null,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            LinearWavyProgressIndicator(progress = { progress ?: 0f }, modifier = Modifier.fillMaxWidth())
        }
        primary?.let { action ->
            BouncyButton(
                onClick = action.onClick,
                enabled = progress == null,
                height = ButtonDefaults.MediumContainerHeight,
                modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            ) {
                MciIcon(action.glyph, 22.dp, LocalContentColor.current)
                Text(action.label, modifier = Modifier.padding(start = 8.dp))
            }
        }
        SegmentedGroup {
            val facts = listOf(
                Triple("weight", "Size", file.size),
                Triple("tag-outline", "Type", file.contentType),
                Triple("calendar-clock", "Uploaded", file.age()),
                Triple(if (file.unlimitedTime) "infinity" else "timer-sand", "Expiry", file.expiry()),
                Triple("download-outline", "Downloads", if (file.unlimitedDownloads) "Unlimited" else "${file.downloadsRemaining} left"),
            )
            facts.forEachIndexed { index, (glyph, label, value) ->
                FactRow(glyph, label, value, segmentCorners(index, facts.size))
            }
        }
        if (actions.isNotEmpty()) {
            SegmentedGroup {
                actions.forEachIndexed { index, action ->
                    ActionRow(action.glyph, action.label, segmentCorners(index, actions.size), onClick = action.onClick, destructive = action.destructive, enabled = progress == null)
                }
            }
        }
    }
}

@Composable
private fun FactRow(glyph: String, label: String, value: String, corners: Corners) {
    MorphSurface(onClick = null, corners = corners, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MciIcon(glyph, 20.dp, MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 88.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}
