package com.uzukip.gokapi.ui.home

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil.compose.AsyncImage
import com.uzukip.gokapi.AppContainer
import com.uzukip.gokapi.BuildConfig
import com.uzukip.gokapi.LocalVibrate
import com.uzukip.gokapi.data.server.*
import com.uzukip.gokapi.platform.AppMessages
import com.uzukip.gokapi.ui.components.*
import com.uzukip.gokapi.ui.upload.UploadSheet
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(container: AppContainer, incomingIntent: Intent?, consumeIntent: () -> Unit, onOpenSettings: () -> Unit) {
    val model: FilesViewModel = viewModel(factory = remember(container) { viewModelFactory { initializer { FilesViewModel(container) } } })
    val state by model.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vibrate = LocalVibrate.current
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var deleteTarget by remember { mutableStateOf<GokapiFile?>(null) }
    var uploadUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var legacySave by remember { mutableStateOf<CachedAction?>(null) }
    val jobs by remember(context) { WorkManager.getInstance(context).getWorkInfosByTagFlow("uploads") }.collectAsState(initial = emptyList())
    val message by AppMessages.message.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { model.refresh(); model.recheckApk() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(jobs.filter { it.state == WorkInfo.State.SUCCEEDED }.map { it.id }) { model.refresh() }
    LaunchedEffect(incomingIntent) { if (incomingIntent != null) { model.acceptIntent(incomingIntent); consumeIntent() } }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); model.clearMessage() } }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); AppMessages.message.value = null } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        uploadUris = uris
    }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val cached = legacySave
        legacySave = null
        if (uri != null && cached != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { out -> cached.file.inputStream().use { it.copyTo(out) } } ?: error("Couldn't save the file.") }
                snackbar.showSnackbar("Saved ${cached.metadata.name}")
            } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Couldn't save this file.") }
        }
    }
    LaunchedEffect(model) {
        model.refresh()
        model.cachedActions.collect { cached ->
            try {
                when (cached.action) {
                    FileAction.SAVE -> if (Build.VERSION.SDK_INT >= 29) {
                        container.transfers.saveToDownloads(cached.file, cached.metadata)
                        snackbar.showSnackbar("Saved to Downloads/Gokapi")
                    } else { legacySave = cached; savePicker.launch(cached.metadata.name) }
                    FileAction.SHARE -> {
                        val uri = container.transfers.uri(cached.file)
                        val intent = Intent(Intent.ACTION_SEND).setType(cached.metadata.contentType).putExtra(Intent.EXTRA_STREAM, uri)
                            .apply { clipData = ClipData.newUri(context.contentResolver, cached.metadata.name, uri) }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(intent, "Share file"))
                    }
                    FileAction.OPEN -> {
                        val uri = container.transfers.uri(cached.file)
                        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, cached.metadata.contentType)
                            .apply { clipData = ClipData.newUri(context.contentResolver, cached.metadata.name, uri) }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        try { context.startActivity(intent) } catch (e: ActivityNotFoundException) { context.startActivity(Intent.createChooser(intent.setType("*/*"), "Open file")) }
                    }
                }
            } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "No app can open this file.") }
        }
    }
    val filtered = state.files.filter { it.name.contains(query, ignoreCase = true) && (filter == "All" || it.type == filter) }
    Scaffold(
        topBar = { GokapiTopBar(title = "Files", onBack = null, actions = {
            BouncyIconButton(onClick = onOpenSettings) { MciIcon("cog-outline", 24.dp, LocalContentColor.current, contentDescription = "Settings") }
        }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { picker.launch(arrayOf("*/*")) }, icon = { MciIcon("plus", 24.dp, LocalContentColor.current) }, text = { Text("Upload") })
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = { vibrate(); model.refresh() }, modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item {
                    OutlinedTextField(query, { query = it }, label = { Text("Search files") }, singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                        listOf("All", "APK", "Images", "Documents", "Media").forEach { type ->
                            FilterChip(selected = filter == type, onClick = { filter = type }, label = { Text(type) })
                        }
                    }
                }
                item {
                    jobs.filter { !it.state.isFinished }.forEach { job ->
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(job.progress.getString("name") ?: "Upload queued", style = MaterialTheme.typography.titleMedium)
                                val percent = job.progress.getInt("percent", -1)
                                if (percent < 0) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                                BouncyTextButton(onClick = { WorkManager.getInstance(context).cancelWorkById(job.id) }) { Text("Cancel upload") }
                            }
                        }
                    }
                }
                state.error?.let { error -> item {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.padding(20.dp)) {
                            Text(error)
                            BouncyTextButton(onClick = if (state.needsSetup) onOpenSettings else { { model.refresh() } }) { Text(if (state.needsSetup) "Set up server" else "Try again") }
                        }
                    }
                } }
                if (filtered.isEmpty() && !state.loading && state.error == null) item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp)) {
                        EmptyState(glyph = "cloud-download-outline", title = if (query.isBlank() && filter == "All") "No files yet" else "No matching files",
                            message = if (query.isBlank() && filter == "All") "Upload a file or share one from your computer." else "Try another name or file type.")
                    }
                }
                itemsIndexed(filtered, key = { _, file -> file.id }) { index, file ->
                    SegmentedGroup {
                        MorphSurface(onClick = { vibrate(); model.download(file, FileAction.OPEN) }, onLongClick = { vibrate(); model.select(file) },
                            corners = segmentCorners(index, filtered.size), enabled = state.transferring == null) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                if (file.isImage && file.hotlinkId.isNotBlank() && !file.encrypted) AsyncImage(model = file.link(state.server, true), contentDescription = null, modifier = Modifier.size(48.dp))
                                else IconBadge(file.glyph, if (file.isApk) "#3D8075" else "#6376A8", 48.dp)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(file.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${file.size} · ${file.age()}", style = MaterialTheme.typography.bodySmall)
                                    Text(file.expiry() + if (file.unlimitedDownloads) "" else " · ${file.downloadsRemaining} downloads left", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (newerAppVersion(file.name, BuildConfig.VERSION_NAME, BuildConfig.FLAVOR == "dev")) Text("Update available", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                    if (state.transferring == file.id) LinearProgressIndicator(progress = { if (state.total > 0) (state.bytes.toFloat() / state.total).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                                }
                                MciIcon("chevron-right", 20.dp, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (state.transferring != null) item { BouncyTextButton(onClick = model::cancelDownload) { Text("Cancel download") } }
            }
        }
    }
    state.selected?.let { file ->
        ModalBottomSheet(onDismissRequest = model::dismiss) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(file.name, style = MaterialTheme.typography.headlineSmallEmphasized)
                Text("${file.size} · ${file.expiry()}")
                BouncyButton(onClick = { model.download(file, FileAction.OPEN) }, modifier = Modifier.fillMaxWidth()) { Text(if (file.isApk) "Install" else "Open") }
                BouncyTonalButton(onClick = { model.download(file, FileAction.SHARE) }, modifier = Modifier.fillMaxWidth()) { Text("Share file") }
                BouncyTonalButton(onClick = { model.download(file, FileAction.SAVE) }, modifier = Modifier.fillMaxWidth()) { Text("Save to Downloads") }
                BouncyTextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Gokapi link", file.link(state.server)))
                    model.dismiss(); model.report("Link copied")
                }) { Text("Copy link") }
                BouncyTextButton(onClick = { deleteTarget = file }) { Text("Delete file", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    deleteTarget?.let { file -> AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Delete ${file.name}?") },
        text = { Text("This permanently removes the file from the server. You need DELETE permission.") },
        confirmButton = { BouncyTextButton(onClick = { deleteTarget = null; model.delete(file) }) { Text("Delete") } },
        dismissButton = { BouncyTextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }) }
    state.apk?.let { apk ->
        ModalBottomSheet(onDismissRequest = model::dismiss) {
            Column(Modifier.padding(24.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(apk.title, style = MaterialTheme.typography.headlineSmallEmphasized)
                Text(apk.packageName, style = MaterialTheme.typography.bodyMedium)
                if (!apk.signatureMatches) {
                    Text("The signing certificate differs. Uninstall the existing app first. Uninstalling removes its local data.", color = MaterialTheme.colorScheme.error)
                    BouncyButton(onClick = { context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${apk.packageName}"))) }) { Text("Uninstall existing app") }
                } else if (!container.installer.canInstall()) {
                    Text("Allow Gokapi to install apps so it can install the APKs you choose.")
                    BouncyButton(onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) }) { Text("Allow app installs") }
                } else {
                    BouncyButton(onClick = { model.install(apk) }, modifier = Modifier.fillMaxWidth()) { Text(if (apk.installedVersion == null) "Install" else "Update") }
                }
            }
        }
    }
    if (uploadUris.isNotEmpty()) UploadSheet(container, uploadUris, onDismiss = { uploadUris = emptyList() }, onQueued = { uploadUris = emptyList(); model.report("Upload queued") })
}
