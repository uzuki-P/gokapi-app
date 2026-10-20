package com.uzukip.gokapi.data.files

import android.content.ContentUris
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
            // A copy saved to Downloads earlier is quicker to copy than to download again.
            if (Build.VERSION.SDK_INT < 29 || !copyFromDownloads(file, target)) client.download(c, file, target, progress)
            target
        } }

    @RequiresApi(29)
    private fun copyFromDownloads(metadata: GokapiFile, target: File): Boolean {
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, target.name + ".part")
        return try {
            val saved = savedCopy(metadata) ?: return false
            val copied = context.contentResolver.openInputStream(saved)?.use { input -> partial.outputStream().use { input.copyTo(it) } }
            copied == metadata.sizeBytes && partial.renameTo(target)
        } catch (e: Exception) { false }
        finally { partial.delete() }
    }

    /**
     * The entry in Downloads/Gokapi this app saved earlier for [metadata], matched by name and size.
     * Android only lists the app's own entries, so a copy from before a reinstall isn't found.
     */
    @RequiresApi(29)
    private fun savedCopy(metadata: GokapiFile): Uri? {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.SIZE} = ?"
        val args = arrayOf("Download/Gokapi/", safeFilename(metadata.name), metadata.sizeBytes.toString())
        return context.contentResolver.query(collection, arrayOf(MediaStore.Downloads._ID), selection, args, null)?.use {
            if (it.moveToFirst()) ContentUris.withAppendedId(collection, it.getLong(0)) else null
        }
    }

    fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** Copies [file] into Downloads/Gokapi, or returns the copy saved there earlier. */
    @RequiresApi(29)
    suspend fun saveToDownloads(file: File, metadata: GokapiFile): Uri = withContext(Dispatchers.IO) {
        check(Build.VERSION.SDK_INT >= 29)
        savedCopy(metadata)?.let { return@withContext it }
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
