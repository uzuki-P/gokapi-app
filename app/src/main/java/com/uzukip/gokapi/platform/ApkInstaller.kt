package com.uzukip.gokapi.platform

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.IntentCompat
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class ApkDetails(
    val file: File, val packageName: String, val label: String,
    val version: String, val installedVersion: String?, val signatureMatches: Boolean,
) {
    val title get() = if (installedVersion == null) "Install $label $version" else "Update $label $installedVersion → $version"
}

class ApkInstaller(private val context: Context) {
    @Suppress("DEPRECATION")
    suspend fun inspect(file: File): ApkDetails = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val apk = pm.getPackageArchiveInfo(file.path, flags) ?: error("This APK could not be read.")
        val info = apk.applicationInfo ?: error("This APK has no application information.")
        info.sourceDir = file.path
        info.publicSourceDir = file.path
        val installed = try { pm.getPackageInfo(apk.packageName, flags) } catch (e: PackageManager.NameNotFoundException) { null }
        ApkDetails(file, apk.packageName, pm.getApplicationLabel(info).toString(), apk.versionName ?: "Unknown version",
            installed?.versionName, installed == null || signaturesMatch(apk, installed))
    }

    @Suppress("DEPRECATION")
    private fun signaturesMatch(apk: PackageInfo, installed: PackageInfo): Boolean {
        if (Build.VERSION.SDK_INT >= 28) {
            val signing = apk.signingInfo ?: return false
            val candidates = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
            val old = installed.signingInfo ?: return false
            if (signing.hasMultipleSigners() || old.hasMultipleSigners()) {
                return signing.apkContentsSigners.map { it.toCharsString() }.toSet() == old.apkContentsSigners.map { it.toCharsString() }.toSet()
            }
            return candidates.any { signature -> context.packageManager.hasSigningCertificate(
                installed.packageName, MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()), PackageManager.CERT_INPUT_SHA256,
            ) }
        }
        return apk.signatures?.map { it.toCharsString() }?.toSet() == installed.signatures?.map { it.toCharsString() }?.toSet()
    }

    fun canInstall() = context.packageManager.canRequestPackageInstalls()

    suspend fun install(apk: ApkDetails) = withContext(Dispatchers.IO) {
        check(apk.signatureMatches) { "The signing certificate differs. Uninstall the existing app before installing this APK." }
        check(canInstall()) { "Allow Gokapi to install apps in Android settings, then tap Install again." }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(apk.packageName)
            setSize(apk.file.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, apk.file.length()).use { output ->
                    apk.file.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                val intent = Intent(context, InstallStatusReceiver::class.java).putExtra("label", apk.label)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
            }
        } catch (e: Exception) { installer.abandonSession(id); throw e }
    }
}

class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("label") ?: "App"
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                val action = PendingIntent.getActivity(context, intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, 0),
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                AppNotifications(context).installResult("Confirm installation", "Tap to install $label.", action)
                if (MainActivityState.foreground) context.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                AppNotifications(context).installResult("Installed $label", "The app is ready to open.")
                val app = context.applicationContext as com.uzukip.gokapi.GokapiApplication
                AppMessages.message.value = "Installed $label"
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    app.container.haptics.vibrate(app.container.settings.settings.first().vibrationMs)
                }
            }
            else -> AppNotifications(context).installResult("Couldn't install $label",
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Android rejected the installation.")
        }
    }
}
