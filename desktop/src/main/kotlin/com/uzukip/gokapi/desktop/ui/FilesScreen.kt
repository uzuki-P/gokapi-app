package com.uzukip.gokapi.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.uzukip.gokapi.data.server.GokapiFile
import com.uzukip.gokapi.data.server.formatBytes
import com.uzukip.gokapi.desktop.platform.DesktopShell
import com.uzukip.gokapi.ui.components.BouncyButton
import com.uzukip.gokapi.ui.components.BouncyIconButton
import com.uzukip.gokapi.ui.components.BouncyTextButton
import com.uzukip.gokapi.ui.components.Corners
import com.uzukip.gokapi.ui.components.EmptyState
import com.uzukip.gokapi.ui.components.IconBadge
import com.uzukip.gokapi.ui.components.MciIcon
import com.uzukip.gokapi.ui.components.MorphSurface
import com.uzukip.gokapi.ui.components.SearchPill
import com.uzukip.gokapi.ui.components.SectionHeader
import com.uzukip.gokapi.ui.components.SegmentGap
import com.uzukip.gokapi.ui.components.segmentCorners
import java.awt.datatransfer.DataFlavor
import java.io.File
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private data class FileFilter(val type: String, val glyph: String)

private val Filters = listOf(
    FileFilter("All", "folder-outline"),
    FileFilter("APK", "android"),
    FileFilter("Images", "image-outline"),
    FileFilter("Documents", "file-document-outline"),
    FileFilter("Media", "play-circle-outline"),
)

/** Widest the file list grows; wider windows center it. */
internal val ContentMaxWidth = 880.dp

/**
 * Files home, the desktop layout of the Android app's HomeScreen: title with refresh and
 * settings buttons, search and filter toggles, live upload cards, then the files as one
 * segmented group. Clicking a file opens it; its overflow button or a right click opens its
 * menu. Ctrl-click or long-press starts selecting, which swaps the Upload button for a
 * floating toolbar with bulk save, copy, and delete. Dropping files on the window uploads them.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun FilesScreen(
    model: FilesModel,
    keys: KeyActions,
    onPickFiles: () -> List<File>,
    uploadPending: Boolean,
    onUploadHandled: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var deleteTarget by remember { mutableStateOf<GokapiFile?>(null) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var pendingUploads by remember { mutableStateOf<List<File>>(emptyList()) }
    var dragging by remember { mutableStateOf(false) }
    val windowInfo = LocalWindowInfo.current

    LaunchedEffect(model) {
        model.notices.collect { notice ->
            launch {
                val result = snackbar.showSnackbar(notice.text, actionLabel = notice.folder?.let { "Open folder" }, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) notice.folder?.let { folder ->
                    runCatching { DesktopShell.open(folder) }.onFailure { model.report(it.message ?: "Couldn't open the folder.") }
                }
            }
        }
    }

    val filtered = state.files.filter { it.name.contains(query, ignoreCase = true) && (filter == "All" || it.type == filter) }
    val visibleIds = filtered.mapTo(HashSet(), GokapiFile::id)
    LaunchedEffect(visibleIds) { model.retainChecked(visibleIds) }
    val pickUploads = { onPickFiles().takeIf { it.isNotEmpty() }?.let { pendingUploads = it } }
    LaunchedEffect(uploadPending) {
        if (uploadPending) {
            onUploadHandled()
            pickUploads()
        }
    }
    keys.handler = { key ->
        when (key) {
            AppKey.ESCAPE -> if (state.selecting) { model.clearChecked(); true } else false
            AppKey.REFRESH -> { model.refresh(); true }
            AppKey.UPLOAD -> { pickUploads(); true }
            AppKey.SELECT_ALL -> { model.checkAll(filtered); true }
            AppKey.DELETE -> if (state.selecting) { confirmBulkDelete = true; true } else false
        }
    }

    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { dragging = true }
            override fun onExited(event: DragAndDropEvent) { dragging = false }
            override fun onEnded(event: DragAndDropEvent) { dragging = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dragging = false
                val transferable = event.awtTransferable
                if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
                val files = (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                    .orEmpty().filterIsInstance<File>().filter(File::isFile)
                if (files.isEmpty()) { model.report("Drop files, not folders."); return false }
                pendingUploads = files
                return true
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget)) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 136.dp),
            ) {
                val column = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()
                item(key = "header") {
                    HomeHeader(state.server, state.files.size, state.loading, model::refresh, onOpenSettings, column)
                }
                item(key = "search") {
                    SearchPill(value = query, onValueChange = { query = it }, placeholder = "Search files", modifier = column)
                }
                item(key = "filters") {
                    FilterRow(selected = filter, onSelect = { filter = it }, modifier = column)
                }
                if (state.uploads.isNotEmpty()) {
                    item(key = "uploads-header") { SectionHeader(title = "Uploading", modifier = column) }
                    itemsIndexed(state.uploads, key = { _, job -> "job-${job.id}" }) { index, job ->
                        UploadJobRow(
                            job = job,
                            corners = segmentCorners(index, state.uploads.size),
                            onCancel = { model.cancelUpload(job.id) },
                            modifier = column.animateItem().padding(bottom = SegmentGap),
                        )
                    }
                }
                state.error?.let { error ->
                    item(key = "error") {
                        ErrorCard(
                            message = error,
                            needsSetup = state.needsSetup,
                            onAction = if (state.needsSetup) onOpenSettings else model::refresh,
                            modifier = column,
                        )
                    }
                }
                when {
                    state.loading && state.files.isEmpty() && state.error == null -> item(key = "loading") {
                        Box(column.padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                            ContainedLoadingIndicator(Modifier.size(64.dp))
                        }
                    }
                    filtered.isEmpty() && state.error == null -> item(key = "empty") {
                        val unfiltered = query.isBlank() && filter == "All"
                        EmptyState(
                            glyph = if (unfiltered) "cloud-upload-outline" else "file-search-outline",
                            title = if (unfiltered) "No files yet" else "No matching files",
                            message = if (unfiltered) "Upload a file or drop one on this window." else "Try another name or file type.",
                            modifier = column.padding(top = 24.dp),
                        )
                    }
                    filtered.isNotEmpty() -> {
                        item(key = "files-header") {
                            SectionHeader(
                                title = if (query.isBlank() && filter == "All") "Recent files" else "Results",
                                modifier = column,
                                action = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${filtered.size} ${if (filtered.size == 1) "file" else "files"}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        ToolbarAction("checkbox-multiple-outline", "Select all (Ctrl+A)", MaterialTheme.colorScheme.onSurfaceVariant, enabled = state.transferring == null) {
                                            model.checkAll(filtered)
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
                                onClick = {
                                    if (state.selecting || windowInfo.keyboardModifiers.isCtrlPressed) model.toggle(file) else model.open(file)
                                },
                                onLongClick = { model.toggle(file) },
                                onOpen = { model.open(file) },
                                onSave = { model.save(file) },
                                onCopyLink = { model.copyLinks(listOf(file)) },
                                onSelect = { model.toggle(file) },
                                onDelete = { deleteTarget = file },
                                onCancel = model::cancelDownload,
                                modifier = column.animateItem().padding(bottom = SegmentGap),
                            )
                        }
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp, horizontal = 2.dp),
            )

            AnimatedVisibility(
                visible = state.selecting,
                enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it } + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { -it } + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
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
                    onClose = model::clearChecked,
                    onToggleAll = { all -> if (all) model.checkAll(filtered) else model.clearChecked() },
                )
            }

            AnimatedVisibility(
                visible = !state.selecting,
                enter = scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp),
            ) {
                MediumExtendedFloatingActionButton(
                    text = { Text("Upload") },
                    icon = { MciIcon("upload", 28.dp, LocalContentColor.current) },
                    onClick = { pickUploads() },
                    expanded = true,
                )
            }
            AnimatedVisibility(
                visible = state.selecting,
                enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = FloatingToolbarDefaults.ScreenOffset),
            ) {
                SelectionToolbar(
                    downloading = state.batchSize > 0,
                    onSave = model::saveChecked,
                    onCopyLinks = { model.copyLinks(state.checkedFiles); model.clearChecked() },
                    onDelete = { confirmBulkDelete = true },
                    onCancel = model::cancelDownload,
                )
            }
            SnackbarHost(
                snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = 112.dp).widthIn(max = 600.dp),
            )

            AnimatedVisibility(
                visible = dragging,
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                modifier = Modifier.fillMaxSize(),
            ) { DropOverlay() }
        }
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
    if (pendingUploads.isNotEmpty()) {
        UploadDialog(
            files = pendingUploads,
            defaults = model.settings,
            onDismiss = { pendingUploads = emptyList() },
            onUpload = { options -> model.upload(pendingUploads, options); pendingUploads = emptyList() },
        )
    }
}

@Composable
private fun DropOverlay() {
    Box(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)) {}
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge("cloud-upload-outline", "#00838F", 96.dp)
            Text("Drop files to upload", style = MaterialTheme.typography.headlineMediumEmphasized, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun HomeHeader(server: String, count: Int, loading: Boolean, onRefresh: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier) {
    val host = server.toHttpUrlOrNull()?.host
    Row(
        modifier = modifier.padding(start = 4.dp, top = 24.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Gokapi", style = MaterialTheme.typography.displaySmallEmphasized, color = MaterialTheme.colorScheme.primary)
            Text(
                if (host == null) "Not connected" else "$count ${if (count == 1) "file" else "files"} · $host",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalIconButton(onClick = onRefresh, enabled = !loading, shapes = IconButtonDefaults.shapes(), modifier = Modifier.height(48.dp)) {
            if (loading) LoadingIndicator(Modifier.size(28.dp), color = LocalContentColor.current)
            else MciIcon("refresh", 24.dp, LocalContentColor.current, contentDescription = "Refresh (F5)")
        }
        FilledTonalIconButton(onClick = onOpenSettings, shapes = IconButtonDefaults.shapes(), modifier = Modifier.height(48.dp)) {
            MciIcon("cog-outline", 24.dp, LocalContentColor.current, contentDescription = "Settings")
        }
    }
}

/** File-type toggles; the checked one fills and morphs its shape. */
@Composable
private fun FilterRow(selected: String, onSelect: (String) -> Unit, modifier: Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Filters.forEach { option ->
            val checked = selected == option.type
            TonalToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(option.type) },
                shapes = ToggleButtonDefaults.shapesFor(40.dp),
                elevation = null,
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

/** Pinned bar shown while selecting: close, a rolling count, and a Select all toggle. */
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
                // Tall enough to hide the Gokapi title and its subtitle underneath
                .heightIn(min = 116.dp)
                .padding(start = 16.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BouncyIconButton(onClick = onClose) {
                MciIcon("close", 24.dp, LocalContentColor.current, contentDescription = "Clear selection (Esc)")
            }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                val slide = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
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
            TonalToggleButton(
                checked = allChecked,
                onCheckedChange = onToggleAll,
                enabled = batchSize == 0,
                shapes = ToggleButtonDefaults.shapesFor(40.dp),
                elevation = null,
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

/** Vibrant floating toolbar with the bulk actions, or a cancel button while a batch downloads. */
@Composable
private fun SelectionToolbar(downloading: Boolean, onSave: () -> Unit, onCopyLinks: () -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    HorizontalFloatingToolbar(expanded = true, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()) {
        AnimatedContent(targetState = downloading, label = "toolbarContent") { busy ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (busy) {
                    LoadingIndicator(Modifier.size(40.dp), color = LocalContentColor.current)
                    Text("Downloading", style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.padding(horizontal = 8.dp))
                    ToolbarAction("close", "Cancel download", LocalContentColor.current, onClick = onCancel)
                } else {
                    ToolbarAction("tray-arrow-down", "Save to Downloads", LocalContentColor.current, onClick = onSave)
                    ToolbarAction("link-variant", "Copy links", LocalContentColor.current, onClick = onCopyLinks)
                    ToolbarAction("trash-can-outline", "Delete (Del)", LocalContentColor.current, onClick = onDelete)
                }
            }
        }
    }
}

/** Icon button whose label shows as a tooltip on hover. */
@Composable
private fun ToolbarAction(glyph: String, label: String, tint: androidx.compose.ui.graphics.Color, enabled: Boolean = true, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        BouncyIconButton(onClick = onClick, enabled = enabled) {
            MciIcon(glyph, 24.dp, tint, contentDescription = label)
        }
    }
}

@Composable
private fun UploadJobRow(job: UploadJob, corners: Corners, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val fraction = if (job.size > 0) (job.sent.toFloat() / job.size).coerceIn(0f, 1f) else null
    MorphSurface(
        onClick = null,
        corners = corners,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconBadge("cloud-upload-outline", "#00838F", 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(job.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (fraction == null || job.sent == 0L) {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
                Text(
                    if (job.sent == 0L) "Waiting · ${formatBytes(job.size)}" else "${formatBytes(job.sent)} of ${formatBytes(job.size)}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            BouncyIconButton(onClick = onCancel) {
                MciIcon("close", 22.dp, LocalContentColor.current, contentDescription = "Cancel upload")
            }
        }
    }
}

/** First-run prompt (tonal) or connection failure (error colors) with its recovery action. */
@Composable
private fun ErrorCard(message: String, needsSetup: Boolean, onAction: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (needsSetup) scheme.secondaryContainer else scheme.errorContainer,
        contentColor = if (needsSetup) scheme.onSecondaryContainer else scheme.onErrorContainer,
        modifier = modifier.padding(top = 16.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MciIcon(if (needsSetup) "server-network" else "cloud-alert-outline", 28.dp, LocalContentColor.current)
                Text(if (needsSetup) "Connect your server" else "Couldn't reach the server", style = MaterialTheme.typography.titleMediumEmphasized)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            BouncyButton(
                onClick = onAction,
                colors = if (needsSetup) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
            ) { Text(if (needsSetup) "Set up server" else "Try again") }
        }
    }
}

@Composable
private fun FileThumbnail(file: GokapiFile, server: String) {
    val bitmap = if (file.isImage && file.hotlinkId.isNotBlank() && !file.encrypted) Thumbnails.remember(file.link(server, true)).value else null
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)))
    } else {
        IconBadge(file.glyph, if (file.isApk) "#3D8075" else "#6376A8", 48.dp)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FileRow(
    file: GokapiFile,
    server: String,
    corners: Corners,
    progress: Float?,
    enabled: Boolean,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onCopyLink: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A checked row leaves the segmented group: it rounds out fully and fills with the secondary container
    val color by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "rowColor",
    )
    var menuOpen by remember { mutableStateOf(false) }
    // Null while the overflow button opened the menu; the pointer position for a right click.
    var pointerOffset by remember { mutableStateOf<DpOffset?>(null) }
    var rowHeight by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box(
        modifier
            .onPointerEvent(PointerEventType.Press) { event ->
                if (enabled && event.buttons.isSecondaryPressed) {
                    val position = event.changes.first().position
                    with(density) { pointerOffset = DpOffset(position.x.toDp(), (position.y - rowHeight).toDp()) }
                    menuOpen = true
                }
            }
            .onSizeChanged { rowHeight = it.height },
    ) {
        MorphSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            corners = if (checked) Corners(24.dp) else corners,
            color = color,
            contentColor = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().then(if (selecting) Modifier.semantics { selected = checked } else Modifier),
        ) {
            Column {
                Row(Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(
                        visible = selecting,
                        enter = expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) + scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) + scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        // The whole row toggles, so the checkbox only shows the state
                        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 14.dp))
                    }
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        FileThumbnail(file, server)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(file.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${file.size} · ${file.age()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                MciIcon(if (file.unlimitedTime) "infinity" else "timer-sand", 14.dp, MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    file.expiry() + if (file.unlimitedDownloads) "" else " · ${file.downloadsRemaining} downloads left",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        when {
                            progress != null -> BouncyIconButton(onClick = onCancel) {
                                MciIcon("close", 22.dp, MaterialTheme.colorScheme.onSurfaceVariant, contentDescription = "Cancel download")
                            }
                            selecting -> Box(Modifier.size(width = 8.dp, height = 48.dp))
                            else -> BouncyIconButton(onClick = { pointerOffset = null; menuOpen = true }, enabled = enabled) {
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
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    )
                }
            }
        }
        // The overflow button opens the menu at the row's end; a right click opens it at the pointer.
        Box(Modifier.align(if (pointerOffset == null) Alignment.BottomEnd else Alignment.BottomStart)) {
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                offset = pointerOffset ?: DpOffset(0.dp, (-12).dp),
                shape = MaterialTheme.shapes.large,
            ) {
                MenuEntry("open-in-new", "Open") { menuOpen = false; onOpen() }
                MenuEntry("tray-arrow-down", "Save to Downloads") { menuOpen = false; onSave() }
                MenuEntry("link-variant", "Copy link") { menuOpen = false; onCopyLink() }
                MenuEntry(if (checked) "checkbox-blank-off-outline" else "checkbox-marked-outline", if (checked) "Deselect" else "Select") { menuOpen = false; onSelect() }
                MenuEntry("trash-can-outline", "Delete", destructive = true) { menuOpen = false; onDelete() }
            }
        }
    }
}

@Composable
private fun MenuEntry(glyph: String, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(label, color = tint, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { MciIcon(glyph, 20.dp, tint) },
        onClick = onClick,
    )
}
