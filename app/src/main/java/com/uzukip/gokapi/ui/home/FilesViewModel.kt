package com.uzukip.gokapi.ui.home

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzukip.gokapi.AppContainer
import com.uzukip.gokapi.data.server.*
import com.uzukip.gokapi.platform.ApkDetails
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class FilesState(
    val files: List<GokapiFile> = emptyList(), val loading: Boolean = false, val needsSetup: Boolean = false,
    val server: String = "", val error: String? = null, val transferring: String? = null,
    val bytes: Long = 0, val total: Long = 0, val selected: GokapiFile? = null,
    val apk: ApkDetails? = null, val message: String? = null,
    /** IDs ticked in selection mode; selection mode is on while this is non-empty. */
    val checked: Set<String> = emptySet(),
    /** Position in a multi-file download, 1-based, and its size; 0 when idle. */
    val batchIndex: Int = 0, val batchSize: Int = 0,
) {
    val selecting get() = checked.isNotEmpty()
    val checkedFiles get() = files.filter { it.id in checked }
}
/** DOWNLOAD saves to Downloads/Gokapi, then asks whether to open the file: the default tap. */
internal enum class FileAction { OPEN, SHARE, SAVE, DOWNLOAD }
internal class CachedFile(val file: File, val metadata: GokapiFile)
internal class CachedAction(val items: List<CachedFile>, val action: FileAction)

internal class FilesViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(FilesState())
    val state = mutableState.asStateFlow()
    private val actions = Channel<CachedAction>(Channel.BUFFERED)
    val cachedActions = actions.receiveAsFlow()
    private var transfer: Job? = null
    private var refreshJob: Job? = null

    // Files already saved to Downloads in this session, so tapping again doesn't save a duplicate.
    private val savedToDownloads = mutableSetOf<String>()
    fun wasSaved(file: GokapiFile) = file.id in savedToDownloads
    fun markSaved(file: GokapiFile) { savedToDownloads += file.id }

    init { refresh() }
    fun dismiss() { mutableState.value = state.value.copy(selected = null, apk = null) }
    fun select(file: GokapiFile) { mutableState.value = state.value.copy(selected = file, apk = null) }
    fun clearMessage() { mutableState.value = state.value.copy(message = null) }
    fun report(message: String) { mutableState.value = state.value.copy(message = message) }
    fun toggle(file: GokapiFile) = mutableState.update { it.copy(checked = if (file.id in it.checked) it.checked - file.id else it.checked + file.id) }
    fun checkAll(files: List<GokapiFile>) = mutableState.update { it.copy(checked = it.checked + files.map(GokapiFile::id)) }
    fun clearChecked() = mutableState.update { it.copy(checked = emptySet()) }
    /** Drops ticks on files that left the list or the current search, so bulk actions only touch visible files. */
    fun retainChecked(ids: Set<String>) = mutableState.update { if (it.checked.all(ids::contains)) it else it.copy(checked = it.checked intersect ids) }

    private suspend fun credentials(): ServerCredentials {
        val saved = container.credentials.load()
        if (saved.apiKey.isBlank() || saved.serverUrl.isBlank()) throw IllegalArgumentException("Connect your server in Settings first.")
        return saved
    }
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            mutableState.value = state.value.copy(loading = true, error = null)
            try {
                val c = credentials()
                if (state.value.server.isNotBlank() && state.value.server != c.serverUrl) mutableState.value = state.value.copy(files = emptyList(), server = c.serverUrl)
                val files = container.client.list(c)
                val ids = files.mapTo(HashSet(), GokapiFile::id)
                mutableState.value = state.value.copy(files = files, server = c.serverUrl, needsSetup = false, loading = false, checked = state.value.checked intersect ids)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutableState.value = state.value.copy(loading = false, needsSetup = e is IllegalArgumentException,
                    error = e.message ?: "Couldn't load files. Check Tailscale and try again.")
            }
        }
    }

    fun acceptIntent(intent: Intent) {
        viewModelScope.launch {
            try {
                val c = credentials()
                val data = intent.data
                val id = if (data != null) {
                    val url = data.toString().toHttpUrlOrNull() ?: error("Invalid file link.")
                    val server = c.serverUrl.toHttpUrlOrNull() ?: error("Set up the server first.")
                    require(url.scheme == "https" && url.host == server.host && url.port == server.port) { "This link belongs to another server. Check your Server settings." }
                    when {
                        url.encodedPath == "/d" -> url.queryParameter("id")
                        url.encodedPath.startsWith("/h/") -> {
                            val hotlink = url.pathSegments.drop(1).joinToString("/")
                            container.client.list(c).firstOrNull { it.hotlinkId == hotlink }?.id
                        }
                        else -> null
                    }
                } else intent.getStringExtra("file_id")
                require(id != null && id.matches(Regex("[A-Za-z0-9_-]+"))) { "This file link has no valid file ID." }
                val file = container.client.get(c, id)
                mutableState.value = state.value.copy(selected = file, server = c.serverUrl)
                if (intent.getBooleanExtra("open_file", false)) download(file, FileAction.OPEN)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e.message ?: "Couldn't open this file link.") }
        }
    }

    fun download(file: GokapiFile, action: FileAction) {
        if (transfer?.isActive == true) return
        mutableState.value = state.value.copy(transferring = file.id, bytes = 0, total = file.sizeBytes, selected = null)
        transfer = viewModelScope.launch {
            try {
                val cached = fetch(credentials(), file)
                if (action == FileAction.OPEN && file.isApk) mutableState.value = state.value.copy(apk = container.installer.inspect(cached))
                else actions.send(CachedAction(listOf(CachedFile(cached, file)), action))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e.message ?: "Couldn't download this file.") }
            finally { mutableState.value = state.value.copy(transferring = null) }
        }
    }

    /** Downloads the ticked files one at a time, then hands them to the screen as one share or save. */
    fun downloadChecked(action: FileAction) {
        val files = state.value.checkedFiles
        if (transfer?.isActive == true || files.isEmpty()) return
        transfer = viewModelScope.launch {
            try {
                val c = credentials()
                val cached = files.mapIndexed { index, file ->
                    mutableState.update { it.copy(transferring = file.id, bytes = 0, total = file.sizeBytes, batchIndex = index + 1, batchSize = files.size) }
                    CachedFile(fetch(c, file), file)
                }
                mutableState.update { it.copy(checked = emptySet()) }
                actions.send(CachedAction(cached, action))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e.message ?: "Couldn't download these files.") }
            finally { mutableState.update { it.copy(transferring = null, batchIndex = 0, batchSize = 0) } }
        }
    }

    private suspend fun fetch(c: ServerCredentials, file: GokapiFile): File {
        require(state.value.server.isBlank() || c.serverUrl == state.value.server) { "The server changed. Refresh the files before downloading." }
        var lastUpdate = 0L
        return container.transfers.download(c, file) { bytes, total ->
            val now = System.currentTimeMillis()
            if (now - lastUpdate >= 100 || bytes == total) {
                lastUpdate = now
                mutableState.update { it.copy(bytes = bytes, total = total) }
            }
        }
    }
    fun recheckApk() {
        val current = state.value.apk ?: return
        viewModelScope.launch {
            try { mutableState.value = state.value.copy(apk = container.installer.inspect(current.file)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { dismiss(); report(e.message ?: "Couldn't read this APK.") }
        }
    }
    fun cancelDownload() { transfer?.cancel(); mutableState.value = state.value.copy(transferring = null, batchIndex = 0, batchSize = 0) }
    fun install(apk: ApkDetails) {
        viewModelScope.launch {
            try { container.installer.install(apk); dismiss(); report("Installation started. Android may ask for confirmation.") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e.message ?: "Couldn't install this APK.") }
        }
    }
    fun delete(file: GokapiFile) {
        viewModelScope.launch {
            try { container.client.delete(credentials(), file.id); dismiss(); refresh(); report("Deleted ${file.name}") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e.message ?: "Couldn't delete this file.") }
        }
    }
    /** Deletes every ticked file, carrying on past failures, and reports how many were deleted. */
    fun deleteChecked() {
        val files = state.value.checkedFiles
        if (files.isEmpty()) return
        viewModelScope.launch {
            try {
                val c = credentials()
                var failure: String? = null
                val failed = files.filterNot { file ->
                    try { container.client.delete(c, file.id); true }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { failure = e.message; false }
                }
                val deleted = files.size - failed.size
                // Files that failed stay ticked so the user can retry them
                mutableState.update { it.copy(checked = failed.mapTo(HashSet(), GokapiFile::id)) }
                refresh()
                report(when {
                    deleted == files.size -> "Deleted ${files.size} ${if (files.size == 1) "file" else "files"}"
                    deleted == 0 -> failure ?: "Couldn't delete these files."
                    else -> "Deleted $deleted of ${files.size} files. ${failure ?: ""}".trim()
                })
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e.message ?: "Couldn't delete these files.") }
        }
    }
}
