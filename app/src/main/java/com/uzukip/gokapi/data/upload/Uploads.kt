package com.uzukip.gokapi.data.upload

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.work.*
import com.uzukip.gokapi.GokapiApplication
import com.uzukip.gokapi.data.server.*
import com.uzukip.gokapi.platform.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class UploadDocument(val uri: Uri, val name: String, val size: Long, val contentType: String)

suspend fun inspectDocuments(context: Context, uris: List<Uri>): List<UploadDocument> = withContext(Dispatchers.IO) {
    uris.map { uri ->
        require(uri.scheme == "content") { "Choose a file from an Android document provider." }
        var name = "file"
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameColumn >= 0) name = cursor.getString(nameColumn) ?: "file"
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn)
            }
        }
        if (size < 0) context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { size = it.length }
        require(size >= 0) { "$name has no known size. Choose a provider that reports file sizes." }
        UploadDocument(uri, name, size, context.contentResolver.getType(uri) ?: "application/octet-stream")
    }
}

suspend fun enqueueUploads(context: Context, documents: List<UploadDocument>, options: UploadOptions): List<java.util.UUID> {
    val container = (context.applicationContext as GokapiApplication).container
    val secret = container.credentials.encryptJobSecret(options.password)
    val manager = WorkManager.getInstance(context)
    val requests = documents.map { doc ->
        // Retain a package grant for share-sheet URIs while the background worker streams them.
        try { context.grantUriPermission(context.packageName, doc.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) { }
        OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf("uri" to doc.uri.toString(), "name" to doc.name, "size" to doc.size,
                "type" to doc.contentType, "days" to options.expiryDays, "downloads" to options.downloads, "password" to secret))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("uploads").build()
    }
    // A failed file does not cancel later files in a selection.
    manager.enqueue(requests)
    return requests.map { it.id }
}

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as GokapiApplication).container
        val notifications = AppNotifications(applicationContext)
        val name = inputData.getString("name") ?: return Result.failure()
        val size = inputData.getLong("size", -1)
        val uri = inputData.getString("uri")?.let(Uri::parse) ?: return Result.failure()
        val notificationId = id.hashCode()
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        suspend fun foreground(percent: Int) {
            val n = notifications.uploadProgress(name, percent, cancel)
            setForeground(if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                else ForegroundInfo(notificationId, n))
            setProgress(workDataOf("name" to name, "percent" to percent))
        }
        try {
            foreground(-1)
            val credentials = container.credentials.load()
            require(credentials.apiKey.isNotBlank()) { "Set up the server before uploading." }
            val limits = container.client.uploadLimits(credentials)
            val password = inputData.getString("password")?.let { container.credentials.decryptJobSecret(it) }.orEmpty()
            val options = UploadOptions(inputData.getInt("days", 7), inputData.getInt("downloads", 0), password)
            var percent = 0
            val file = applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                container.client.upload(credentials, name, inputData.getString("type") ?: "application/octet-stream", size, stream, options, limits) { sent, total ->
                    val next = if (total == 0L) 100 else ((sent * 100) / total).toInt()
                    if (next != percent) {
                        percent = next
                        // Requests execute on IO; WorkManager's future avoids launching a coroutine per chunk.
                        setForegroundAsync(if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notifications.uploadProgress(name, percent, cancel), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                            else ForegroundInfo(notificationId, notifications.uploadProgress(name, percent, cancel)))
                        setProgressAsync(workDataOf("name" to name, "percent" to percent))
                    }
                }
            } ?: throw IOException("Couldn't open $name. Select it again to grant access.")
            val link = file.link(credentials.serverUrl, imageHotlink = true)
            val settings = container.settings.settings.first()
            if (settings.autoCopyLink) {
                val copied = MainActivityState.foreground && runCatching {
                    applicationContext.getSystemService(android.content.ClipboardManager::class.java)
                        .setPrimaryClip(android.content.ClipData.newPlainText("Gokapi link", link))
                }.isSuccess
                if (!copied) container.settings.rememberUploadLink(link)
            }
            notifications.uploadFinished(file, link)
            container.haptics.vibrate(settings.vibrationMs)
            AppMessages.message.value = "Uploaded ${file.name}"
            return Result.success(workDataOf("file_id" to file.id, "link" to link, "name" to name))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val message = e.message ?: "Upload failed. Select the file and try again."
            AppMessages.message.value = message
            notifications.uploadFailed(notificationId, name, message)
            return Result.failure(workDataOf("error" to message, "name" to name))
        }
    }
}
