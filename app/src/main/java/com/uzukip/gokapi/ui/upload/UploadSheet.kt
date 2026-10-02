package com.uzukip.gokapi.ui.upload

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uzukip.gokapi.AppContainer
import com.uzukip.gokapi.data.server.UploadOptions
import com.uzukip.gokapi.data.server.formatBytes
import com.uzukip.gokapi.data.upload.UploadDocument
import com.uzukip.gokapi.data.upload.enqueueUploads
import com.uzukip.gokapi.data.upload.inspectDocuments
import com.uzukip.gokapi.ui.components.BouncyButton
import com.uzukip.gokapi.ui.components.ConnectedChoices
import com.uzukip.gokapi.ui.components.ExpiryChoices
import com.uzukip.gokapi.ui.components.IconBadge
import com.uzukip.gokapi.ui.components.MciIcon
import com.uzukip.gokapi.ui.components.MorphSurface
import com.uzukip.gokapi.ui.components.SegmentedGroup
import com.uzukip.gokapi.ui.components.ThemedTextField
import com.uzukip.gokapi.ui.components.segmentCorners
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Upload options sheet: the picked files as a segmented list, expiry as
 * connected toggles, download limit and optional password, then a large
 * Upload button that queues the work.
 */
@Composable
fun UploadSheet(container: AppContainer, uris: List<Uri>, onDismiss: () -> Unit, onQueued: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var documents by remember(uris) { mutableStateOf<List<UploadDocument>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var days by remember { mutableIntStateOf(7) }
    var downloads by remember { mutableStateOf("0") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(uris) {
        try {
            val defaults = container.settings.settings.first()
            days = defaults.uploadExpiryDays
            downloads = defaults.uploadDownloads.toString()
            documents = inspectDocuments(context, uris)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "Couldn't read these files." }
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val selection = documents
            Column(Modifier.padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Upload files", style = MaterialTheme.typography.headlineMediumEmphasized)
                if (selection != null) {
                    Text(
                        "${selection.size} ${if (selection.size == 1) "file" else "files"} · ${formatBytes(selection.sumOf { it.size })}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selection == null && error == null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
            selection?.let { files ->
                SegmentedGroup {
                    files.forEachIndexed { index, doc ->
                        MorphSurface(
                            onClick = null,
                            corners = segmentCorners(index, files.size),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                IconBadge("file-outline", "#6376A8", 40.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(doc.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        formatBytes(doc.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
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
                ConnectedChoices(options = ExpiryChoices, selected = days, onSelect = { days = it }, enabled = !busy,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            }
            ThemedTextField(
                value = downloads,
                onValueChange = { downloads = it.filter(Char::isDigit).take(9) },
                label = "Allowed downloads",
                placeholder = "0",
                supportingText = "0 means unlimited",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !busy,
            )
            ThemedTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password, optional",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !busy,
            )
            AnimatedVisibility(
                visible = error != null,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        MciIcon("alert-circle-outline", 22.dp, LocalContentColor.current)
                        Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            BouncyButton(
                onClick = {
                    val count = downloads.toIntOrNull()
                    if (count == null || count < 0) { error = "Enter a valid download limit."; return@BouncyButton }
                    if (!password.all { it.code in 32..126 }) { error = "Use printable ASCII characters for the file password."; return@BouncyButton }
                    val files = documents ?: return@BouncyButton
                    busy = true
                    error = null
                    scope.launch {
                        try { enqueueUploads(context, files, UploadOptions(days, count, password)); onQueued() }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { busy = false; error = e.message ?: "Couldn't queue the upload." }
                    }
                },
                enabled = selection != null && !busy,
                height = ButtonDefaults.MediumContainerHeight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonDefaults.MediumContainerHeight),
            ) {
                if (busy) {
                    LoadingIndicator(Modifier.size(24.dp), color = LocalContentColor.current)
                    Text("Queuing upload", modifier = Modifier.padding(start = 8.dp))
                } else {
                    MciIcon("upload", 22.dp, LocalContentColor.current)
                    Text("Upload", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
