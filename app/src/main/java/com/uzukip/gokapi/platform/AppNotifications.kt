package com.uzukip.gokapi.platform

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.uzukip.gokapi.MainActivity
import com.uzukip.gokapi.data.server.GokapiFile
import kotlinx.coroutines.flow.MutableStateFlow

object MainActivityState { var foreground = false }
object AppMessages { val message = MutableStateFlow<String?>(null) }

class AppNotifications(private val context: Context) {
    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf("new_uploads" to "New uploads", "install_results" to "Install results", "upload_progress" to "Upload progress").forEach { (id, name) ->
            manager.createNotificationChannel(NotificationChannel(id, name,
                if (id == "upload_progress") NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
    fun allowed() = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun builder(channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(android.R.drawable.stat_sys_download_done).setAutoCancel(true)

    fun fileIntent(id: String, open: Boolean = false): PendingIntent = PendingIntent.getActivity(context,
        (id + open).hashCode(), Intent(context, MainActivity::class.java)
            .setAction("${context.packageName}.FILE.$id.$open")
            .putExtra("file_id", id).putExtra("open_file", open), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun newFile(file: GokapiFile) {
        post(file.id.hashCode(), builder("new_uploads").setContentTitle(file.name)
            .setContentText("${file.size} · from agent").setContentIntent(fileIntent(file.id))
            .addAction(0, if (file.isApk) "Install" else "Open", fileIntent(file.id, true)).build())
    }
    fun installResult(title: String, text: String, action: PendingIntent? = null) {
        AppMessages.message.value = "$title. $text"
        post(2001, builder("install_results").setContentTitle(title).setContentText(text)
            .setContentIntent(action ?: PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build())
    }
    fun uploadProgress(name: String, percent: Int, cancel: PendingIntent? = null): Notification = builder("upload_progress")
        .setContentTitle("Uploading $name").setContentText(if (percent < 0) "Preparing upload" else "$percent%")
        .setProgress(100, percent.coerceAtLeast(0), percent < 0).setOngoing(true).setAutoCancel(false)
        .setContentIntent(PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .apply { if (cancel != null) addAction(0, "Cancel", cancel) }.build()

    fun uploadFailed(id: Int, name: String, message: String) {
        AppMessages.message.value = message
        post(id, builder("upload_progress").setContentTitle("Couldn't upload $name").setContentText(message)
            .setContentIntent(PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)).build())
    }

    fun uploadFinished(file: GokapiFile, link: String) {
        val share = PendingIntent.getActivity(context, file.id.hashCode(), Intent.createChooser(Intent(Intent.ACTION_SEND)
            .setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), "Share link").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val copy = PendingIntent.getActivity(context, (file.id + "copy").hashCode(), Intent(context, MainActivity::class.java)
            .setAction("${context.packageName}.COPY.${file.id}").putExtra("copy_link", link), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        post(file.id.hashCode(), builder("upload_progress").setContentTitle("Uploaded ${file.name}").setContentText(file.size)
            .setContentIntent(fileIntent(file.id)).addAction(0, "Copy link", copy).addAction(0, "Share link", share).build())
    }
    @Suppress("MissingPermission")
    private fun post(id: Int, notification: Notification) { if (allowed()) NotificationManagerCompat.from(context).notify(id, notification) }
}
