package com.uzukip.gokapi.ui.upload

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.uzukip.gokapi.AppContainer
import com.uzukip.gokapi.data.server.UploadOptions
import com.uzukip.gokapi.data.server.formatBytes
import com.uzukip.gokapi.data.upload.*
import com.uzukip.gokapi.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun UploadSheet(container: AppContainer, uris: List<Uri>, onDismiss: () -> Unit, onQueued: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var documents by remember(uris) { mutableStateOf<List<UploadDocument>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var days by remember { mutableStateOf(7) }
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
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Upload files", style = MaterialTheme.typography.headlineMediumEmphasized)
            val selection = documents
            if (selection == null && error == null) CircularProgressIndicator()
            selection?.let { files ->
                Text("${files.size} files · ${formatBytes(files.sumOf { it.size })}", style = MaterialTheme.typography.titleMedium)
                SegmentedGroup {
                    files.forEachIndexed { index, doc ->
                        MorphSurface(onClick = null, corners = segmentCorners(index, files.size)) {
                            Column(Modifier.padding(16.dp)) { Text(doc.name); Text(formatBytes(doc.size), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            Text("Expiry", style = MaterialTheme.typography.titleSmallEmphasized)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                listOf(1 to "1 d", 7 to "7 d", 30 to "30 d", 0 to "Never").forEachIndexed { index, option ->
                    ToggleButton(checked = days == option.first, onCheckedChange = { days = option.first }, enabled = !busy,
                        shapes = when (index) { 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes(); 3 -> ButtonGroupDefaults.connectedTrailingButtonShapes(); else -> ButtonGroupDefaults.connectedMiddleButtonShapes() },
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(option.second) }
                }
            }
            OutlinedTextField(downloads, { downloads = it.filter(Char::isDigit).take(9) }, label = { Text("Allowed downloads") }, supportingText = { Text("0 means unlimited") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            OutlinedTextField(password, { password = it }, label = { Text("Password, optional") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            BouncyButton(onClick = {
                val count = downloads.toIntOrNull()
                if (count == null || count < 0) { error = "Enter a valid download limit."; return@BouncyButton }
                if (!password.all { it.code in 32..126 }) { error = "Use printable ASCII characters for the file password."; return@BouncyButton }
                val files = documents ?: return@BouncyButton
                busy = true
                scope.launch {
                    try { enqueueUploads(context, files, UploadOptions(days, count, password)); onQueued() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { busy = false; error = e.message ?: "Couldn't queue the upload." }
                }
            }, enabled = selection != null && !busy, height = 56.dp, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(if (busy) "Queuing upload" else "Upload") }
        }
    }
}
