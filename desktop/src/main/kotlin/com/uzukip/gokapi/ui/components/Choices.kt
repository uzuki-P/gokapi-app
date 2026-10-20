package com.uzukip.gokapi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Desktop copy of the Android app's Choices.kt: Compose Multiplatform material3 still names
// ToggleButtonDefaults.colors() toggleButtonColors().

/** One option of [ConnectedChoices]; a null [glyph] gives a text-only button. */
data class Choice<T>(val value: T, val label: String, val glyph: String? = null)

/**
 * Full-width connected toggle buttons; the checked one rounds fully. With
 * glyphs the buttons are 72dp tiles, without them 48dp pills. Raise
 * [containerColor] when the group sits on a container of the same tone.
 */
@Composable
fun <T> ConnectedChoices(
    options: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val colors = ToggleButtonDefaults.toggleButtonColors(containerColor = containerColor)
    val tall = options.any { it.glyph != null }
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = option.value == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(option.value) },
                enabled = enabled,
                colors = colors,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = if (tall) 10.dp else 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(if (tall) 72.dp else 48.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    option.glyph?.let { MciIcon(it, 22.dp, LocalContentColor.current) }
                    Text(
                        option.label,
                        style = if (checked) {
                            MaterialTheme.typography.labelLargeEmphasized
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Upload expiry options shared by the upload sheet and its defaults. */
val ExpiryChoices = listOf(
    Choice(1, "1 day"),
    Choice(7, "7 days"),
    Choice(30, "30 days"),
    Choice(0, "Never"),
)
