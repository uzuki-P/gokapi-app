package com.uzukip.gokapi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Full-width pill search field: leading magnifier, inline text, clear button
 * while non-empty, and an optional [trailing] slot.
 */
@Composable
fun SearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    // A background, not a Surface: Surface clips to the pill, which would cut
    // off anything a trailing button draws past its corner.
    Box(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50)),
    ) {
        Row(
            Modifier
                .heightIn(min = 56.dp)
                .padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MciIcon("magnify", 22.dp, MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = placeholderColor(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            if (value.isNotEmpty()) {
                BouncyIconButton(onClick = { onValueChange("") }) {
                    MciIcon("close-circle", 20.dp, MaterialTheme.colorScheme.onSurfaceVariant, contentDescription = "Clear search")
                }
            }
            trailing?.invoke(this)
        }
    }
}

/**
 * One row of a segmented action list (bottom-sheet menus): glyph, label,
 * error tint when [destructive].
 */
@Composable
fun ActionRow(
    glyph: String,
    label: String,
    corners: Corners,
    onClick: () -> Unit,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    MorphSurface(
        onClick = onClick,
        corners = corners,
        pressedCorners = Corners(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MciIcon(glyph, 24.dp, tint)
            Text(label, style = MaterialTheme.typography.titleMedium, color = tint)
        }
    }
}

/** Hint text color for empty fields. */
@Composable
internal fun placeholderColor() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
