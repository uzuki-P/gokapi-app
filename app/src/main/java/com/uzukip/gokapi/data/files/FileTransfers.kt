package com.uzukip.gokapi.data.files

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.uzukip.gokapi.data.server.*
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

fun safeFilename(name: String): String = name.substringAfterLast('/').substringAfterLast('\\')
    .replace(Regex("[\\p{Cntrl}]"), "_").take(180).trim().takeUnless { it.isEmpty() || it == "." || it == ".." } ?: "file"

class FileTransfers(private val context: Context, private val client: GokapiClient) {
    private val mutex = Mutex()
    private val root = File(context.cacheDir, "downloads")

    suspend fun prune() = withContext(Dispatchers.IO) { mutex.withLock { pruneLocked() } }
    private fun pruneLocked() {
        val files = root.walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toList()
        var total = files.sumOf { it.length() }
        files.forEach {
            if (it.name.endsWith(".part") || total > 500L * 1024 * 1024) {
                val size = it.length()
                if (it.delete()) total -= size
            }
        }
    }

    suspend fun download(c: ServerCredentials, file: GokapiFile, progress: (Long, Long) -> Unit): File =
        withContext(Dispatchers.IO) { mutex.withLock {
            val server = MessageDigest.getInstance("SHA-256").digest(c.serverUrl.toByteArray()).joinToString("") { "%02x".format(it) }
            val target = File(root, "$server/${file.id}/${safeFilename(file.name)}")
            if (target.exists() && target.length() == file.sizeBytes) {
                target.setLastModified(System.currentTimeMillis())
                return@withLock target
            }
            pruneLocked()
            client.download(c, file, target, progress)
            target
        } }

    fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    @RequiresApi(29)
    suspend fun saveToDownloads(file: File, metadata: GokapiFile): Uri = withContext(Dispatchers.IO) {
        check(Build.VERSION.SDK_INT >= 29)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeFilename(metadata.name))
            put(MediaStore.Downloads.MIME_TYPE, metadata.contentType)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Gokapi")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Couldn't create the file in Downloads.")
        try {
            resolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                ?: throw IOException("Couldn't write to Downloads.")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
    }
}
