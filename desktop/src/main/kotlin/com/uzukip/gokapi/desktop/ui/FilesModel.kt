package com.uzukip.gokapi.desktop.ui

import com.uzukip.gokapi.data.server.GokapiFile
import com.uzukip.gokapi.data.server.ServerCredentials
import com.uzukip.gokapi.data.server.UploadOptions
import com.uzukip.gokapi.desktop.DesktopContainer
import com.uzukip.gokapi.desktop.platform.DesktopShell
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class FilesState(
    val files: List<GokapiFile> = emptyList(), val loading: Boolean = false, val needsSetup: Boolean = false,
    val server: String = "", val error: String? = null, val transferring: String? = null,
    val bytes: Long = 0, val total: Long = 0,
    /** IDs ticked in selection mode; selection mode is on while this is non-empty. */
    val checked: Set<String> = emptySet(),
    /** Position in a multi-file download, 1-based, and its size; 0 when idle. */
    val batchIndex: Int = 0, val batchSize: Int = 0,
    val uploads: List<UploadJob> = emptyList(),
) {
    val selecting get() = checked.isNotEmpty()
    val checkedFiles get() = files.filter { it.id in checked }
}

internal data class UploadJob(val id: String, val name: String, val size: Long, val sent: Long = 0)

/** A snackbar message, with an optional folder the snackbar can open. */
internal class Notice(val text: String, val folder: File? = null)

/**
 * The files screen's state and actions: the Android app's FilesViewModel, with uploads run
 * in-process instead of WorkManager, and Open and Save going to the desktop's apps and folders.
 */
internal class FilesModel(private val container: DesktopContainer, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(FilesState())
    val state = mutableState.asStateFlow()
    private val noticeChannel = Channel<Notice>(Channel.BUFFERED)
    val notices = noticeChannel.receiveAsFlow()
    private var transfer: Job? = null
    private var refreshJob: Job? = null
    private val uploadJobs = mutableMapOf<String, Job>()
    private var uploadQueue: Job? = null

    val settings get() = container.settings.settings

    fun report(text: String, folder: File? = null) { noticeChannel.trySend(Notice(text, folder)) }
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
        refreshJob = scope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                val c = credentials()
                if (state.value.server.isNotBlank() && state.value.server != c.serverUrl) mutableState.update { it.copy(files = emptyList(), server = c.serverUrl) }
                val files = container.client.list(c)
                val ids = files.mapTo(HashSet(), GokapiFile::id)
                mutableState.update { it.copy(files = files, server = c.serverUrl, needsSetup = false, loading = false, checked = it.checked intersect ids) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutableState.update {
                    it.copy(loading = false, needsSetup = e is IllegalArgumentException,
                        error = e.message ?: "Couldn't load files. Check Tailscale and try again.")
                }
            }
        }
    }

    /** Downloads [file] to the cache and opens it in its default app. */
    fun open(file: GokapiFile) = single(file) { c ->
        val target = fetch(c, file, File(container.dirs.cacheDir, "files/${file.id}/${safeFilename(file.name)}"))
        DesktopShell.open(target)
    }

    /** Downloads [file] into Downloads/Gokapi. */
    fun save(file: GokapiFile) = single(file) { c ->
        val target = fetch(c, file, uniqueTarget(container.dirs.downloadsDir, file.name))
        report("Saved ${target.name} to Downloads/Gokapi", target.parentFile)
    }

    private fun single(file: GokapiFile, action: suspend (ServerCredentials) -> Unit) {
        if (transfer?.isActive == true) return
        mutableState.update { it.copy(transferring = file.id, bytes = 0, total = file.sizeBytes) }
        transfer = scope.launch {
            try { action(credentials()) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e.message ?: "Couldn't download this file.") }
            finally { mutableState.update { it.copy(transferring = null) } }
        }
    }

    /** Saves the ticked files one at a time into Downloads/Gokapi. */
    fun saveChecked() {
        val files = state.value.checkedFiles
        if (transfer?.isActive == true || files.isEmpty()) return
        transfer = scope.launch {
            try {
                val c = credentials()
                files.forEachIndexed { index, file ->
                    mutableState.update { it.copy(transferring = file.id, bytes = 0, total = file.sizeBytes, batchIndex = index + 1, batchSize = files.size) }
                    fetch(c, file, uniqueTarget(container.dirs.downloadsDir, file.name))
                }
                mutableState.update { it.copy(checked = emptySet()) }
                report(if (files.size == 1) "Saved to Downloads/Gokapi" else "Saved ${files.size} files to Downloads/Gokapi", container.dirs.downloadsDir)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e.message ?: "Couldn't download these files.") }
            finally { mutableState.update { it.copy(transferring = null, batchIndex = 0, batchSize = 0) } }
        }
    }

    private suspend fun fetch(c: ServerCredentials, file: GokapiFile, target: File): File {
        require(state.value.server.isBlank() || c.serverUrl == state.value.server) { "The server changed. Refresh the files before downloading." }
        var lastUpdate = 0L
        container.client.download(c, file, target) { bytes, total ->
            val now = System.currentTimeMillis()
            if (now - lastUpdate >= 100 || bytes == total) {
                lastUpdate = now
                mutableState.update { it.copy(bytes = bytes, total = total) }
            }
        }
        return target
    }

    fun cancelDownload() { transfer?.cancel(); mutableState.update { it.copy(transferring = null, batchIndex = 0, batchSize = 0) } }

    fun copyLinks(files: List<GokapiFile>) {
        DesktopShell.copy(files.joinToString("\n") { it.link(state.value.server) })
        report(if (files.size == 1) "Link copied" else "Copied ${files.size} links")
    }

    fun delete(file: GokapiFile) {
        scope.launch {
            try { container.client.delete(credentials(), file.id); refresh(); report("Deleted ${file.name}") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e.message ?: "Couldn't delete this file.") }
        }
    }

    /** Deletes every ticked file, carrying on past failures, and reports how many were deleted. */
    fun deleteChecked() {
        val files = state.value.checkedFiles
        if (files.isEmpty()) return
        scope.launch {
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

    /** Queues [files] for upload; they upload one at a time, in order, after any earlier queue. */
    fun upload(files: List<File>, options: UploadOptions) {
        val jobs = files.map { UploadJob(UUID.randomUUID().toString(), it.name, it.length()) }
        mutableState.update { it.copy(uploads = it.uploads + jobs) }
        val previous = uploadQueue
        uploadQueue = scope.launch {
            previous?.join()
            files.zip(jobs).forEach { (file, job) ->
                if (state.value.uploads.none { it.id == job.id }) return@forEach
                val work = launch { uploadOne(file, job, options) }
                uploadJobs[job.id] = work
                work.join()
                uploadJobs.remove(job.id)
                mutableState.update { s -> s.copy(uploads = s.uploads.filterNot { it.id == job.id }) }
            }
        }
    }

    private suspend fun uploadOne(file: File, job: UploadJob, options: UploadOptions) {
        try {
            val c = credentials()
            val limits = container.client.uploadLimits(c)
            val type = runCatching { Files.probeContentType(file.toPath()) }.getOrNull() ?: "application/octet-stream"
            var lastUpdate = 0L
            val uploaded = file.inputStream().buffered().use { input ->
                container.client.upload(c, file.name, type, job.size, input, options, limits) { sent, _ ->
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate >= 100 || sent == job.size) {
                        lastUpdate = now
                        mutableState.update { s -> s.copy(uploads = s.uploads.map { if (it.id == job.id) it.copy(sent = sent) else it }) }
                    }
                }
            }
            // Your own upload is not news: keep it out of the new-file notifications.
            container.seenFiles.add(c.serverUrl, uploaded.id)
            refresh()
            if (container.settings.settings.value.autoCopyLink) {
                DesktopShell.copy(uploaded.link(c.serverUrl))
                report("Uploaded ${uploaded.name}. Link copied")
            } else report("Uploaded ${uploaded.name}")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { report(e.message ?: "Couldn't upload ${file.name}.") }
    }

    fun cancelUpload(id: String) {
        uploadJobs[id]?.cancel()
        mutableState.update { s -> s.copy(uploads = s.uploads.filterNot { it.id == id }) }
    }
}

/** A name safe on Windows and Linux, keeping the extension. */
internal fun safeFilename(name: String): String {
    val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimEnd('.', ' ')
    return cleaned.ifBlank { "file" }.take(200)
}

/** [name] in [directory], with " (2)", " (3)" … before the extension when it already exists. */
internal fun uniqueTarget(directory: File, name: String): File {
    val safe = safeFilename(name)
    val base = safe.substringBeforeLast('.', safe)
    val extension = safe.substringAfterLast('.', "").let { if (it.isEmpty() || it == safe) "" else ".$it" }
    var candidate = File(directory, safe)
    var index = 2
    while (candidate.exists()) candidate = File(directory, "$base ($index)$extension").also { index++ }
    return candidate
}
