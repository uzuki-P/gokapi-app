package com.uzukip.gokapi.platform

import android.content.Context
import androidx.work.*
import com.uzukip.gokapi.GokapiApplication
import com.uzukip.gokapi.data.settings.NotificationMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import java.util.concurrent.TimeUnit

class UploadPushService : PushService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val container get() = (application as GokapiApplication).container
    override fun onMessage(message: PushMessage, instance: String) {
        val id = runCatching { Json.parseToJsonElement(message.content.toString(Charsets.UTF_8)).jsonObject["Id"]?.jsonPrimitive?.content }.getOrNull()
        if (id != null && id.matches(Regex("[A-Za-z0-9_-]+"))) {
            WorkManager.getInstance(this).enqueueUniqueWork("push-$id", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<PushFileWorker>().setInputData(workDataOf("file_id" to id))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        scope.launch {
            val url = endpoint.url.toHttpUrlOrNull()
            if (url?.isHttps == true && url.username.isEmpty() && url.password.isEmpty()) {
                container.settings.setPushEndpoint(endpoint.url, "Registered. Copy this device endpoint to the server's endpoint file.")
            } else container.settings.setPushEndpoint("", "Choose an HTTPS ntfy server in your distributor settings.")
        }
    }
    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        scope.launch { container.settings.setPushEndpoint("", "Push registration failed. Polling every 15 minutes until you reconnect.") }
    }
    override fun onUnregistered(instance: String) {
        scope.launch { container.settings.setPushEndpoint("", "Push is disconnected. Polling every 15 minutes until you reconnect.") }
    }
}

class PushFileWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as GokapiApplication).container
        if (c.settings.settings.first().notificationMode != NotificationMode.INSTANT) return Result.success()
        val id = inputData.getString("file_id") ?: return Result.failure()
        return try {
            // Trust file metadata from the configured Gokapi server, never URLs in a push payload.
            val file = c.client.get(c.credentials.load(), id)
            AppNotifications(applicationContext).newFile(file)
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }
}

class PollFilesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as GokapiApplication).container
        val settings = c.settings.settings.first()
        if (settings.notificationMode == NotificationMode.OFF) return Result.success()
        if (settings.notificationMode == NotificationMode.INSTANT && settings.pushEndpoint.isNotBlank() && UnifiedPush.getAckDistributor(applicationContext) != null) return Result.success()
        return try {
            val credentials = c.credentials.load()
            if (credentials.apiKey.isBlank()) return Result.success()
            val files = c.client.list(credentials)
            val newIds = c.settings.newFileIds(credentials.serverUrl, files.map { it.id }.toSet())
            val notifications = AppNotifications(applicationContext)
            files.filter { it.id in newIds }.forEach(notifications::newFile)
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.retry() }
    }
}

fun scheduleNotifications(context: Context, mode: NotificationMode) {
    val manager = WorkManager.getInstance(context)
    if (mode == NotificationMode.OFF) {
        manager.cancelUniqueWork("file-poll")
        UnifiedPush.unregister(context)
    } else {
        manager.enqueueUniquePeriodicWork("file-poll", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PollFilesWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        if (mode == NotificationMode.POLL) UnifiedPush.unregister(context)
    }
}
