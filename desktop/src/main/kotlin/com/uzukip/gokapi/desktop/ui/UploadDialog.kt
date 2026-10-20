package com.uzukip.gokapi.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uzukip.gokapi.data.server.UploadOptions
import com.uzukip.gokapi.data.server.formatBytes
import com.uzukip.gokapi.desktop.data.Settings
import com.uzukip.gokapi.ui.components.BouncyButton
import com.uzukip.gokapi.ui.components.BouncyTextButton
import com.uzukip.gokapi.ui.components.ConnectedChoices
import com.uzukip.gokapi.ui.components.ExpiryChoices
import com.uzukip.gokapi.ui.components.IconBadge
import com.uzukip.gokapi.ui.components.MciIcon
import com.uzukip.gokapi.ui.components.MorphSurface
import com.uzukip.gokapi.ui.components.SegmentedGroup
import com.uzukip.gokapi.ui.components.ThemedTextField
import com.uzukip.gokapi.ui.components.segmentCorners
import java.io.File
import kotlinx.coroutines.flow.StateFlow

/**
 * Upload options, the desktop form of the Android app's UploadSheet: the picked files,
 * expiry as connected toggles, download limit and optional password, then Upload.
 */
@Composable
internal fun UploadDialog(files: List<File>, defaults: StateFlow<Settings>, onDismiss: () -> Unit, onUpload: (UploadOptions) -> Unit) {
    val initial = remember { defaults.value }
    var days by remember { mutableIntStateOf(initial.uploadExpiryDays) }
    var downloads by remember { mutableStateOf(initial.uploadDownloads.toString()) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val submit = submit@{
        val count = downloads.toIntOrNull()
        if (count == null || count < 0) { error = "Enter a valid download limit."; return@submit }
        if (!password.all { it.code in 32..126 }) { error = "Use printable ASCII characters for the file password."; return@submit }
        onUpload(UploadOptions(days, count, password))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 420.dp, max = 560.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Upload files", style = MaterialTheme.typography.headlineMediumEmphasized)
                Text(
                    "${files.size} ${if (files.size == 1) "file" else "files"} · ${formatBytes(files.sumOf(File::length))}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                SegmentedGroup(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    files.forEachIndexed { index, file ->
                        MorphSurface(onClick = null, corners = segmentCorners(index, files.size), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                IconBadge("file-outline", "#6376A8", 40.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatBytes(file.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Expiry",
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    ConnectedChoices(options = ExpiryChoices, selected = days, onSelect = { days = it }, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                }
                ThemedTextField(
                    value = downloads,
                    onValueChange = { downloads = it.filter(Char::isDigit).take(9) },
                    label = "Allowed downloads",
                    placeholder = "0",
                    supportingText = "0 means unlimited",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                ThemedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password, optional",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                AnimatedVisibility(visible = error != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        MciIcon("alert-circle-outline", 22.dp, MaterialTheme.colorScheme.error)
                        Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            BouncyButton(onClick = submit) {
                MciIcon("upload", 20.dp, LocalContentColor.current)
                Text("Upload", modifier = Modifier.padding(start = 8.dp))
            }
        },
        dismissButton = { BouncyTextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
