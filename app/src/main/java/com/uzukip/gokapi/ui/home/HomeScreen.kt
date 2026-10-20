package com.uzukip.gokapi.ui.home

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import com.uzukip.gokapi.ui.components.TwoPaneMinWidth
import com.uzukip.gokapi.ui.components.FileDetailsPane
import com.uzukip.gokapi.ui.components.DetailAction
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import com.uzukip.gokapi.data.files.safeFilename
import com.uzukip.gokapi.data.server.GokapiFile
import com.uzukip.gokapi.data.server.formatBytes
import com.uzukip.gokapi.data.server.newerAppVersion
import com.uzukip.gokapi.platform.ApkDetails
import com.uzukip.gokapi.platform.AppMessages
import com.uzukip.gokapi.ui.components.ActionRow
import com.uzukip.gokapi.ui.components.BouncyButton
import com.uzukip.gokapi.ui.components.BouncyIconButton
import com.uzukip.gokapi.ui.components.BouncyTextButton
import com.uzukip.gokapi.ui.components.BouncyTonalButton
import com.uzukip.gokapi.ui.components.Corners
import com.uzukip.gokapi.ui.components.EmptyState
import com.uzukip.gokapi.ui.components.IconBadge
import com.uzukip.gokapi.ui.components.MciIcon
import com.uzukip.gokapi.ui.components.MorphSurface
import com.uzukip.gokapi.ui.components.SearchPill
import com.uzukip.gokapi.ui.components.SectionHeader
import com.uzukip.gokapi.ui.components.SegmentGap
import com.uzukip.gokapi.ui.components.SegmentedGroup
import com.uzukip.gokapi.ui.components.segmentCorners
import com.uzukip.gokapi.ui.upload.UploadSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private data class FileFilter(val type: String, val glyph: String)

private val Filters = listOf(
    FileFilter("All", "folder-outline"),
    FileFilter("APK", "android"),
    FileFilter("Images", "image-outline"),
    FileFilter("Documents", "file-document-outline"),
    FileFilter("Media", "play-circle-outline"),
)

/**
 * Files home: large rounded title with a settings button, a search pill and
 * filter toggles, live upload cards, then the files as one segmented group.
 * Upload is a medium extended FAB that shrinks to its icon while scrolling
 * down. Tapping a file shows its details: full screen on phones, in a pane beside the list on
 * tablets. Download there saves it to Downloads/Gokapi, then asks whether to open it; Install
 * downloads an APK, then opens the install sheet. A file's overflow button opens its action sheet. Long-press a file to
 * start selecting: rows grow checkboxes, a selection bar covers the title, and
 * a floating toolbar replaces the FAB with bulk share, save, copy, and delete.
 */
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
    var legacySave by remember { mutableStateOf<CachedFile?>(null) }
    var legacyBatch by remember { mutableStateOf<List<CachedFile>>(emptyList()) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    // A file Download just saved, waiting for Open or Close.
    var openPrompt by remember { mutableStateOf<CachedFile?>(null) }
    val openCached = { item: CachedFile ->
        val uri = container.transfers.uri(item.file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.metadata.contentType)
            .apply { clipData = ClipData.newUri(context.contentResolver, item.metadata.name, uri) }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { context.startActivity(intent) } catch (e: ActivityNotFoundException) { context.startActivity(Intent.createChooser(intent.setType("*/*"), "Open file")) }
    }
    // The file in the details view: a pane beside the list on tablets, full screen on phones.
    var focusedId by remember { mutableStateOf<String?>(null) }
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
    // Android 8–9 has no MediaStore Downloads, so a multi-file save asks for a folder instead
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        val batch = legacyBatch
        legacyBatch = emptyList()
        if (tree != null && batch.isNotEmpty()) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                    batch.forEach { cached ->
                        val target = DocumentsContract.createDocument(context.contentResolver, parent, cached.metadata.contentType, safeFilename(cached.metadata.name))
                            ?: error("Couldn't create ${cached.metadata.name}.")
                        context.contentResolver.openOutputStream(target)?.use { out -> cached.file.inputStream().use { it.copyTo(out) } } ?: error("Couldn't save ${cached.metadata.name}.")
                    }
                }
                snackbar.showSnackbar("Saved ${batch.size} files")
            } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Couldn't save these files.") }
        }
    }
    LaunchedEffect(model) {
        model.refresh()
        model.cachedActions.collect { cached ->
            val items = cached.items
            val first = items.first()
            try {
                when (cached.action) {
                    FileAction.SAVE -> when {
                        Build.VERSION.SDK_INT >= 29 -> {
                            items.forEach { container.transfers.saveToDownloads(it.file, it.metadata) }
                            snackbar.showSnackbar(if (items.size == 1) "Saved to Downloads/Gokapi" else "Saved ${items.size} files to Downloads/Gokapi")
                        }
                        items.size == 1 -> { legacySave = first; savePicker.launch(first.metadata.name) }
                        else -> { legacyBatch = items; folderPicker.launch(null) }
                    }
                    FileAction.SHARE -> {
                        val uris = items.map { container.transfers.uri(it.file) }
                        val intent = if (uris.size == 1) {
                            Intent(Intent.ACTION_SEND).setType(first.metadata.contentType).putExtra(Intent.EXTRA_STREAM, uris[0])
                        } else {
                            Intent(Intent.ACTION_SEND_MULTIPLE).setType(sharedMimeType(items.map { it.metadata.contentType }))
                                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                        }
                        intent.clipData = ClipData.newUri(context.contentResolver, first.metadata.name, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(intent, if (uris.size == 1) "Share file" else "Share ${uris.size} files"))
                    }
                    FileAction.OPEN -> openCached(first)
                    FileAction.DOWNLOAD -> {
                        // Android 8 and 9 have no MediaStore Downloads; the file stays in the cache.
                        // A copy already in Downloads/Gokapi is reused instead of saved twice.
                        if (Build.VERSION.SDK_INT >= 29) container.transfers.saveToDownloads(first.file, first.metadata)
                        openPrompt = first
                    }
                }
            } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "No app can open this file.") }
        }
    }
    val filtered = state.files.filter { it.name.contains(query, ignoreCase = true) && (filter == "All" || it.type == filter) }
    val activeJobs = jobs.filter { !it.state.isFinished }
    val listState = rememberLazyListState()
    val fabExpanded by rememberExpandedOnScrollUp(listState)
    val refreshState = rememberPullToRefreshState()
    val visibleIds = filtered.mapTo(HashSet(), GokapiFile::id)
    LaunchedEffect(visibleIds) { model.retainChecked(visibleIds) }
    BackHandler(enabled = state.selecting) { model.clearChecked() }
    val toggle = { file: GokapiFile -> vibrate(); model.toggle(file) }

    // Surface (not a bare background) so text inherits onSurface in both themes
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        // Tablets and unfolded foldables show the list and a details pane side by side.
        // Narrower screens cover the list with the details instead.
        val wide = maxWidth >= TwoPaneMinWidth
        val focused = filtered.firstOrNull { it.id == focusedId }
        val paneWidth = (maxWidth * 0.42f).coerceIn(340.dp, 480.dp)
        val listEnd = if (wide) paneWidth + 16.dp else 0.dp
        BackHandler(enabled = focused != null && !state.selecting) { focusedId = null }
        PullToRefreshBox(
            isRefreshing = state.loading && state.files.isNotEmpty(),
            onRefresh = { vibrate(); model.refresh() },
            state = refreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = refreshState,
                    isRefreshing = state.loading && state.files.isNotEmpty(),
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(end = listEnd)
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            LazyColumn(
                state = listState,
                // Portrait tablets keep the list at a readable width.
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 136.dp),
            ) {
                item(key = "header") {
                    HomeHeader(server = state.server, count = state.files.size, onOpenSettings = onOpenSettings)
                }
                item(key = "search") {
                    SearchPill(value = query, onValueChange = { query = it }, placeholder = "Search files")
                }
                item(key = "filters") {
                    FilterRow(selected = filter, onSelect = { vibrate(); filter = it })
                }
                if (activeJobs.isNotEmpty()) {
                    item(key = "uploads-header") { SectionHeader(title = "Uploading") }
                    itemsIndexed(activeJobs, key = { _, job -> "job-${job.id}" }) { index, job ->
                        UploadJobRow(
                            job = job,
                            corners = segmentCorners(index, activeJobs.size),
                            onCancel = { WorkManager.getInstance(context).cancelWorkById(job.id) },
                            modifier = Modifier.animateItem().padding(bottom = SegmentGap),
                        )
                    }
                }
                state.error?.let { error ->
                    item(key = "error") {
                        ErrorCard(
                            message = error,
                            needsSetup = state.needsSetup,
                            onAction = if (state.needsSetup) onOpenSettings else { { model.refresh() } },
                        )
                    }
                }
                when {
                    state.loading && state.files.isEmpty() && state.error == null -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                            ContainedLoadingIndicator(Modifier.size(64.dp))
                        }
                    }
                    filtered.isEmpty() && state.error == null -> item(key = "empty") {
                        val unfiltered = query.isBlank() && filter == "All"
                        EmptyState(
                            glyph = if (unfiltered) "cloud-upload-outline" else "file-search-outline",
                            title = if (unfiltered) "No files yet" else "No matching files",
                            message = if (unfiltered) "Upload a file or share one from your computer." else "Try another name or file type.",
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                    filtered.isNotEmpty() -> {
                        item(key = "files-header") {
                            SectionHeader(
                                title = if (query.isBlank() && filter == "All") "Recent files" else "Results",
                                action = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${filtered.size} ${if (filtered.size == 1) "file" else "files"}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        BouncyIconButton(onClick = { vibrate(); model.checkAll(filtered) }, enabled = state.transferring == null) {
                                            MciIcon("checkbox-multiple-outline", 20.dp, MaterialTheme.colorScheme.onSurfaceVariant, contentDescription = "Select all")
                                        }
                                    }
                                },
                            )
                        }
                        itemsIndexed(filtered, key = { _, file -> file.id }) { index, file ->
                            FileRow(
                                file = file,
                                server = state.server,
                                corners = segmentCorners(index, filtered.size),
                                progress = if (state.transferring == file.id) {
                                    if (state.total > 0) (state.bytes.toFloat() / state.total).coerceIn(0f, 1f) else 0f
                                } else null,
                                enabled = state.transferring == null,
                                selecting = state.selecting,
                                checked = file.id in state.checked,
                                focused = focused?.id == file.id,
                                onClick = {
                                    when {
                                        state.selecting -> toggle(file)
                                        else -> { vibrate(); focusedId = file.id }
                                    }
                                },
                                onLongClick = { toggle(file) },
                                onMore = { vibrate(); model.select(file) },
                                onCancel = model::cancelDownload,
                                modifier = Modifier.animateItem().padding(bottom = SegmentGap),
                            )
                        }
                    }
                }
            }
        }
        val details = @Composable { file: GokapiFile?, modifier: Modifier ->
            val progress = if (file != null && state.transferring == file.id) {
                if (state.total > 0) (state.bytes.toFloat() / state.total).coerceIn(0f, 1f) else 0f
            } else null
            val update = file != null && newerAppVersion(file.name, BuildConfig.VERSION_NAME, BuildConfig.FLAVOR == "dev")
            FileDetailsPane(
                file = file,
                primary = file?.let {
                    if (it.isApk) DetailAction("download", "Install") { vibrate(); model.download(it, FileAction.OPEN) }
                    else DetailAction("download", "Download") { vibrate(); model.download(it, FileAction.DOWNLOAD) }
                },
                actions = file?.let {
                    listOfNotNull(
                        if (it.isApk) null else DetailAction("open-in-new", "Open") { vibrate(); model.download(it, FileAction.OPEN) },
                        DetailAction("share-variant-outline", "Share file") { vibrate(); model.download(it, FileAction.SHARE) },
                        if (it.isApk) DetailAction("tray-arrow-down", "Save to Downloads") { vibrate(); model.download(it, FileAction.SAVE) } else null,
                        DetailAction("link-variant", "Copy link") {
                            vibrate()
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Gokapi link", it.link(state.server)))
                            model.report("Link copied")
                        },
                        DetailAction("trash-can-outline", "Delete file", destructive = true) { vibrate(); deleteTarget = it },
                    )
                }.orEmpty(),
                progress = progress,
                badge = if (update) { { UpdateBadge() } } else null,
                preview = { shown ->
                    AsyncImage(
                        model = shown.link(state.server, true),
                        contentDescription = shown.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                modifier = modifier,
            )
        }
        if (wide) {
            details(
                focused,
                Modifier
                    .align(Alignment.TopEnd)
                    .width(paneWidth)
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(top = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
        // Phones show the details full screen, over the list.
        AnimatedVisibility(
            visible = !wide && focused != null,
            enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 4 } + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 4 } + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Keep the last file while the view slides out
            val last = remember { mutableStateOf<GokapiFile?>(null) }
            focused?.let { last.value = it }
            PhoneDetails(onBack = { vibrate(); focusedId = null }) { details(last.value, Modifier.fillMaxSize()) }
        }
        // The selection bar spans both panes
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = state.selecting,
                enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it } + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { -it } + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            ) {
                // Keep the last selection while the bar slides out, so it never reads "0 selected"
                val lastChecked = remember { mutableStateOf(emptyList<GokapiFile>()) }
                state.checkedFiles.takeIf { it.isNotEmpty() }?.let { lastChecked.value = it }
                val checkedFiles = lastChecked.value
                SelectionBar(
                    count = checkedFiles.size,
                    totalBytes = checkedFiles.sumOf { it.sizeBytes },
                    allChecked = filtered.isNotEmpty() && filtered.all { it.id in state.checked },
                    batchIndex = state.batchIndex,
                    batchSize = state.batchSize,
                    onClose = { vibrate(); model.clearChecked() },
                    onToggleAll = { all -> vibrate(); if (all) model.checkAll(filtered) else model.clearChecked() },
                )
            }
        }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
            AnimatedVisibility(
                visible = !state.selecting && (wide || focused == null),
                enter = scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp + listEnd, bottom = 16.dp),
            ) {
                MediumExtendedFloatingActionButton(
                    text = { Text("Upload") },
                    icon = { MciIcon("upload", 28.dp, LocalContentColor.current) },
                    onClick = { vibrate(); picker.launch(arrayOf("*/*")) },
                    expanded = fabExpanded,
                )
            }
            AnimatedVisibility(
                visible = state.selecting,
                enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(end = listEnd, bottom = FloatingToolbarDefaults.ScreenOffset),
            ) {
                SelectionToolbar(
                    downloading = state.batchSize > 0,
                    onShare = { vibrate(); model.downloadChecked(FileAction.SHARE) },
                    onSave = { vibrate(); model.downloadChecked(FileAction.SAVE) },
                    onCopyLinks = {
                        vibrate()
                        val files = state.checkedFiles
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("Gokapi links", files.joinToString("\n") { it.link(state.server) }))
                        model.clearChecked()
                        model.report(if (files.size == 1) "Link copied" else "Copied ${files.size} links")
                    },
                    onDelete = { vibrate(); confirmBulkDelete = true },
                    onCancel = { vibrate(); model.cancelDownload() },
                )
            }
            SnackbarHost(
                snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp + listEnd, bottom = 112.dp),
            )
        }
      }
    }

    state.selected?.let { file ->
        FileActionsSheet(
            file = file,
            onDismiss = model::dismiss,
            onOpen = { model.download(file, FileAction.OPEN) },
            onShare = { model.download(file, FileAction.SHARE) },
            onSave = { model.download(file, FileAction.SAVE) },
            onCopyLink = {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Gokapi link", file.link(state.server)))
                model.dismiss(); model.report("Link copied")
            },
            onDelete = { deleteTarget = file },
        )
    }
    openPrompt?.let { item ->
        val saved = Build.VERSION.SDK_INT >= 29
        AlertDialog(
            onDismissRequest = { openPrompt = null },
            icon = { MciIcon("check-circle-outline", 24.dp, MaterialTheme.colorScheme.primary) },
            title = { Text("Downloaded ${item.metadata.name}") },
            text = { Text(if (saved) "Saved to Downloads/Gokapi. Open it now?" else "Open it now?") },
            confirmButton = {
                BouncyTextButton(onClick = {
                    openPrompt = null
                    try { openCached(item) } catch (e: Exception) { model.report(e.message ?: "No app can open this file.") }
                }) { Text("Open") }
            },
            dismissButton = { BouncyTextButton(onClick = { openPrompt = null }) { Text("Close") } },
        )
    }
    deleteTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { MciIcon("trash-can-outline", 24.dp, MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${file.name}?") },
            text = { Text("This permanently removes the file from the server. You need DELETE permission.") },
            confirmButton = {
                BouncyTextButton(
                    onClick = { deleteTarget = null; model.delete(file) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { BouncyTextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
    if (confirmBulkDelete) {
        val files = state.checkedFiles
        val label = "${files.size} ${if (files.size == 1) "file" else "files"}"
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            icon = { MciIcon("trash-can-outline", 24.dp, MaterialTheme.colorScheme.error) },
            title = { Text("Delete $label?") },
            text = { Text("This permanently removes $label (${formatBytes(files.sumOf { it.sizeBytes })}) from the server. You need DELETE permission.") },
            confirmButton = {
                BouncyTextButton(
                    onClick = { confirmBulkDelete = false; model.deleteChecked() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { BouncyTextButton(onClick = { confirmBulkDelete = false }) { Text("Cancel") } },
        )
    }
    state.apk?.let { apk ->
        ApkSheet(
            apk = apk,
            canInstall = container.installer.canInstall(),
            onDismiss = model::dismiss,
            onUninstall = { context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${apk.packageName}"))) },
            onAllowInstalls = { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) },
            onInstall = { model.install(apk) },
        )
    }
    if (uploadUris.isNotEmpty()) UploadSheet(container, uploadUris, onDismiss = { uploadUris = emptyList() }, onQueued = { uploadUris = emptyList(); model.report("Upload queued") })
}

/** Full-screen details on phones: a back button above the shared details pane. */
@Composable
private fun PhoneDetails(onBack: () -> Unit, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Row(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BouncyIconButton(onClick = onBack) {
                    MciIcon("arrow-left", 24.dp, LocalContentColor.current, contentDescription = "Back")
                }
                Text("Details", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
            }
            Box(Modifier.weight(1f).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { content() }
        }
    }
}

/**
 * True while the list rests at the top or the user scrolls back up, so the
 * Upload FAB shrinks to its icon while reading and grows back on the way up.
 */
@Composable
private fun rememberExpandedOnScrollUp(state: LazyListState): State<Boolean> {
    val expanded = remember { mutableStateOf(true) }
    LaunchedEffect(state) {
        var lastIndex = state.firstVisibleItemIndex
        var lastOffset = state.firstVisibleItemScrollOffset
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val delta = if (index != lastIndex) (index - lastIndex) * 1000 else offset - lastOffset
                when {
                    index == 0 && offset < 24 -> expanded.value = true
                    delta > 8 -> expanded.value = false
                    delta < -8 -> expanded.value = true
                }
                lastIndex = index
                lastOffset = offset
            }
    }
    return expanded
}

@Composable
private fun HomeHeader(server: String, count: Int, onOpenSettings: () -> Unit) {
    val host = server.toHttpUrlOrNull()?.host
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 12.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Gokapi",
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                when {
                    host == null -> "Not connected"
                    else -> "$count ${if (count == 1) "file" else "files"} · $host"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalIconButton(
            onClick = onOpenSettings,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.height(48.dp),
        ) {
            MciIcon("cog-outline", 24.dp, MaterialTheme.colorScheme.onSecondaryContainer, contentDescription = "Settings")
        }
    }
}

/** Scrollable file-type toggles; the checked one fills and morphs its shape. */
@Composable
private fun FilterRow(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Filters.forEach { option ->
            val checked = selected == option.type
            FilledTonalToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(option.type) },
                shapes = ToggleButtonDefaults.shapesFor(40.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                modifier = Modifier.height(40.dp),
            ) {
                MciIcon(option.glyph, 18.dp, LocalContentColor.current)
                Text(
                    option.type,
                    style = if (checked) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * Pinned bar shown while selecting. It covers the title with a close button,
 * a rolling count, and a Select all toggle that morphs when every visible file
 * is ticked.
 */
@Composable
private fun SelectionBar(
    count: Int,
    totalBytes: Long,
    allChecked: Boolean,
    batchIndex: Int,
    batchSize: Int,
    onClose: () -> Unit,
    onToggleAll: (Boolean) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                // Tall enough to hide the Gokapi title and its subtitle underneath
                .heightIn(min = 104.dp)
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BouncyIconButton(onClick = onClose) {
                MciIcon("close", 24.dp, LocalContentColor.current, contentDescription = "Clear selection")
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                // The count rolls up when files are added and down when removed
                val slide = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntOffset>()
                val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                AnimatedContent(
                    targetState = count,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically(slide) { if (up) it else -it } + fadeIn(fade))
                            .togetherWith(slideOutVertically(slide) { if (up) -it else it } + fadeOut(fade))
                    },
                    label = "selectedCount",
                ) { value ->
                    Text("$value selected", style = MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    if (batchSize > 0) "Downloading $batchIndex of $batchSize" else formatBytes(totalBytes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalToggleButton(
                checked = allChecked,
                onCheckedChange = onToggleAll,
                enabled = batchSize == 0,
                shapes = ToggleButtonDefaults.shapesFor(40.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                modifier = Modifier.height(40.dp),
            ) {
                MciIcon(if (allChecked) "checkbox-multiple-marked-outline" else "checkbox-multiple-outline", 18.dp, LocalContentColor.current)
                Text(
                    "All",
                    style = if (allChecked) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * Vibrant floating toolbar with the bulk actions. While a multi-file download
 * runs it shows a loading indicator and a cancel button.
 */
@Composable
private fun SelectionToolbar(
    downloading: Boolean,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onCopyLinks: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        AnimatedContent(targetState = downloading, label = "toolbarContent") { busy ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (busy) {
                    LoadingIndicator(Modifier.size(40.dp), color = LocalContentColor.current)
                    Text("Downloading", style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.padding(horizontal = 8.dp))
                    ToolbarAction("close", "Cancel download", onCancel)
                } else {
                    ToolbarAction("share-variant-outline", "Share", onShare)
                    ToolbarAction("tray-arrow-down", "Save to Downloads", onSave)
                    ToolbarAction("link-variant", "Copy links", onCopyLinks)
                    ToolbarAction("trash-can-outline", "Delete", onDelete)
                }
            }
        }
    }
}

/** Toolbar icon button whose label shows as a tooltip on long-press. */
@Composable
private fun ToolbarAction(glyph: String, label: String, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        BouncyIconButton(onClick = onClick) {
            MciIcon(glyph, 24.dp, LocalContentColor.current, contentDescription = label)
        }
    }
}

/** The narrowest MIME type that covers every shared file: one exact type, one family wildcard, or any type. */
internal fun sharedMimeType(types: List<String>): String {
    val distinct = types.map { it.ifBlank { "application/octet-stream" }.lowercase() }.distinct()
    if (distinct.size == 1) return distinct[0]
    val family = distinct.map { it.substringBefore('/') }.distinct()
    return if (family.size == 1) "${family[0]}/*" else "*/*"
}

@Composable
private fun UploadJobRow(job: WorkInfo, corners: Corners, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val percent = job.progress.getInt("percent", -1)
    MorphSurface(
        onClick = null,
        corners = corners,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconBadge("cloud-upload-outline", "#00838F", 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    job.progress.getString("name") ?: "Upload queued",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (percent < 0) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearWavyProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("$percent%", style = MaterialTheme.typography.labelMedium)
                }
            }
            BouncyIconButton(onClick = onCancel) {
                MciIcon("close", 22.dp, LocalContentColor.current, contentDescription = "Cancel upload")
            }
        }
    }
}

/** First-run prompt (tonal) or connection failure (error colors) with its recovery action. */
@Composable
private fun ErrorCard(message: String, needsSetup: Boolean, onAction: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (needsSetup) scheme.secondaryContainer else scheme.errorContainer,
        contentColor = if (needsSetup) scheme.onSecondaryContainer else scheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MciIcon(if (needsSetup) "server-network" else "cloud-alert-outline", 28.dp, LocalContentColor.current)
                Text(
                    if (needsSetup) "Connect your server" else "Couldn't reach the server",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            BouncyButton(
                onClick = onAction,
                colors = if (needsSetup) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError)
                },
            ) { Text(if (needsSetup) "Set up server" else "Try again") }
        }
    }
}

@Composable
private fun FileThumbnail(file: GokapiFile, server: String, size: androidx.compose.ui.unit.Dp) {
    if (file.isImage && file.hotlinkId.isNotBlank() && !file.encrypted) {
        AsyncImage(
            model = file.link(server, true),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(16.dp)),
        )
    } else {
        IconBadge(file.glyph, if (file.isApk) "#3D8075" else "#6376A8", size)
    }
}

@Composable
private fun FileRow(
    file: GokapiFile,
    server: String,
    corners: Corners,
    progress: Float?,
    enabled: Boolean,
    selecting: Boolean,
    checked: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val update = newerAppVersion(file.name, BuildConfig.VERSION_NAME, BuildConfig.FLAVOR == "dev")
    // A checked row leaves the segmented group: it rounds out fully and fills with the secondary container
    val color by animateColorAsState(
        when {
            checked -> MaterialTheme.colorScheme.secondaryContainer
            focused -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "rowColor",
    )
    MorphSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        corners = if (checked || focused) Corners(24.dp) else corners,
        color = color,
        // The file shown in the details pane.
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        contentColor = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .then(if (selecting) Modifier.semantics { selected = checked } else Modifier),
    ) {
        Column {
            Row(
                Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    visible = selecting,
                    enter = expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) + scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) + scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    // The whole row toggles, so the checkbox only shows the state
                    Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 14.dp))
                }
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    FileThumbnail(file, server, 48.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(file.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${file.size} · ${file.age()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            MciIcon(if (file.unlimitedTime) "infinity" else "timer-sand", 14.dp, MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                file.expiry() + if (file.unlimitedDownloads) "" else " · ${file.downloadsRemaining} downloads left",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (update) UpdateBadge(Modifier.padding(top = 4.dp))
                    }
                    when {
                        progress != null -> BouncyIconButton(onClick = onCancel) {
                            MciIcon("close", 22.dp, MaterialTheme.colorScheme.onSurfaceVariant, contentDescription = "Cancel download")
                        }
                        selecting -> Box(Modifier.size(width = 8.dp, height = 48.dp))
                        else -> BouncyIconButton(onClick = onMore, enabled = enabled) {
                            MciIcon("dots-vertical", 22.dp, MaterialTheme.colorScheme.onSurfaceVariant, contentDescription = "More actions for ${file.name}")
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = progress != null,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                LinearWavyProgressIndicator(
                    progress = { progress ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }
        }
    }
}

/** Marks an APK that is a newer build of this app. */
@Composable
private fun UpdateBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier,
    ) {
        Text(
            "Update available",
            style = MaterialTheme.typography.labelMediumEmphasized,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Overflow sheet: file header, a large primary Open/Install, then the segmented action list. */
@Composable
private fun FileActionsSheet(
    file: GokapiFile,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                Modifier.padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconBadge(file.glyph, if (file.isApk) "#3D8075" else "#6376A8", 56.dp, shape = MaterialShapes.Cookie9Sided.toShape())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(file.name, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${file.size} · ${file.expiry()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            BouncyButton(
                onClick = onOpen,
                height = ButtonDefaults.MediumContainerHeight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonDefaults.MediumContainerHeight),
            ) {
                MciIcon(if (file.isApk) "download" else "open-in-new", 22.dp, LocalContentColor.current)
                Text(if (file.isApk) "Install" else "Open", modifier = Modifier.padding(start = 8.dp))
            }
            SegmentedGroup {
                ActionRow("share-variant-outline", "Share file", segmentCorners(0, 4), onClick = onShare)
                ActionRow("tray-arrow-down", "Save to Downloads", segmentCorners(1, 4), onClick = onSave)
                ActionRow("link-variant", "Copy link", segmentCorners(2, 4), onClick = onCopyLink)
                ActionRow("trash-can-outline", "Delete file", segmentCorners(3, 4), onClick = onDelete, destructive = true)
            }
        }
    }
}

@Composable
private fun ApkSheet(
    apk: ApkDetails,
    canInstall: Boolean,
    onDismiss: () -> Unit,
    onUninstall: () -> Unit,
    onAllowInstalls: () -> Unit,
    onInstall: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                Modifier.padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                IconBadge("android", "#3D8075", 56.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(apk.title, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(apk.packageName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val buttonModifier = Modifier
                .fillMaxWidth()
                .height(ButtonDefaults.MediumContainerHeight)
            when {
                !apk.signatureMatches -> {
                    Notice("alert-outline", "The signing certificate differs. Uninstall the existing app first. Uninstalling removes its local data.", error = true)
                    BouncyButton(
                        onClick = onUninstall,
                        height = ButtonDefaults.MediumContainerHeight,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = buttonModifier,
                    ) { Text("Uninstall existing app") }
                }
                !canInstall -> {
                    Notice("shield-alert-outline", "Allow Gokapi to install apps so it can install the APKs you choose.", error = false)
                    BouncyTonalButton(onClick = onAllowInstalls, height = ButtonDefaults.MediumContainerHeight, modifier = buttonModifier) {
                        Text("Allow app installs")
                    }
                }
                else -> BouncyButton(onClick = onInstall, height = ButtonDefaults.MediumContainerHeight, modifier = buttonModifier) {
                    MciIcon("download", 22.dp, LocalContentColor.current)
                    Text(if (apk.installedVersion == null) "Install" else "Update", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun Notice(glyph: String, text: String, error: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MciIcon(glyph, 22.dp, LocalContentColor.current)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
