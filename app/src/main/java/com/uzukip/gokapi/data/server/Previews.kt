package com.uzukip.gokapi.data.server

import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The two sizes the thumbnail service makes: a square for list rows, a fitted image for the details pane. */
enum class PreviewSize(val query: String) { SMALL("s"), LARGE("l") }

/** What the thumbnail service has for a file: a WebP image, the first lines of a text file, or nothing. */
sealed interface FilePreview {
    class Image(val bytes: ByteArray) : FilePreview
    class Text(val text: String) : FilePreview
    data object None : FilePreview
}

/**
 * The thumbnail service's tailnet URL: `-thumbs` added to the first label of the server's
 * host, so `https://files.example.ts.net` becomes `https://files-thumbs.example.ts.net`.
 * Null for hosts without a dot-separated name, such as IP addresses.
 */
fun thumbsUrl(serverUrl: String): String? {
    val url = serverUrl.toHttpUrlOrNull() ?: return null
    val host = url.host
    if (!host.contains('.') || host.all { it.isDigit() || it == '.' } || host.contains(':')) return null
    val label = host.substringBefore('.')
    return url.newBuilder().host("$label-thumbs.${host.substringAfter('.')}").encodedPath("/").build().toString().trimEnd('/')
}

/**
 * Previews from the server's thumbnail service (`~/docker/files/thumbs`), kept on disk by
 * file ID. Gokapi never changes a file's content under its ID, so an image or text answer
 * is kept until the file leaves the list. "No preview" is kept for a day, so a server
 * update that adds a file type shows up the next day.
 */
class PreviewStore(
    private val dir: File,
    private val client: GokapiClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun base(id: String, size: PreviewSize) = File(dir, "${safeName(id)}-${size.query}")
    private fun safeName(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)

    /** The cached preview, or null when there is none yet. */
    suspend fun cached(file: GokapiFile, size: PreviewSize): FilePreview? = withContext(Dispatchers.IO) { read(file.id, size) }

    /** The cached preview, or the service's answer. Null when the service can't be reached now. */
    suspend fun load(c: ServerCredentials, file: GokapiFile, size: PreviewSize): FilePreview? = withContext(Dispatchers.IO) {
        read(file.id, size)?.let { return@withContext it }
        val lock = locks.getOrPut("${file.id}-${size.query}") { Mutex() }
        lock.withLock {
            read(file.id, size)?.let { return@withLock it }
            val fetched = client.preview(c, file, size) ?: return@withLock null
            try { write(file.id, size, fetched) } catch (e: IOException) { /* still show it */ }
            fetched
        }
    }

    /** Drops previews of files that are no longer on the server. */
    suspend fun retain(ids: Set<String>) = withContext(Dispatchers.IO) {
        val keep = ids.mapTo(HashSet(), ::safeName)
        dir.listFiles()?.forEach { f ->
            val id = f.name.substringBeforeLast('.').substringBeforeLast('-')
            if (id !in keep) f.delete()
        }
    }

    private fun read(id: String, size: PreviewSize): FilePreview? {
        val base = base(id, size)
        File(base.path + ".webp").takeIf { it.isFile }?.let { return FilePreview.Image(it.readBytes()) }
        File(base.path + ".txt").takeIf { it.isFile }?.let { return FilePreview.Text(it.readText()) }
        val none = File(base.path + ".none")
        if (none.isFile) {
            if (clock() - none.lastModified() < NONE_TTL_MS) return FilePreview.None
            none.delete()
        }
        return null
    }

    private fun write(id: String, size: PreviewSize, preview: FilePreview) {
        dir.mkdirs()
        val base = base(id, size)
        val (target, bytes) = when (preview) {
            is FilePreview.Image -> File(base.path + ".webp") to preview.bytes
            is FilePreview.Text -> File(base.path + ".txt") to preview.text.toByteArray()
            FilePreview.None -> File(base.path + ".none") to ByteArray(0)
        }
        val partial = File(dir, ".${target.name}.part")
        partial.writeBytes(bytes)
        if (!partial.renameTo(target)) {
            target.delete()
            if (!partial.renameTo(target)) { partial.delete(); throw IOException("Couldn't save the preview.") }
        }
    }

    private companion object {
        const val NONE_TTL_MS = 24 * 60 * 60 * 1000L
    }
}
