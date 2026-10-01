package com.uzukip.gokapi.ui.settings

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uzukip.gokapi.AppContainer
import com.uzukip.gokapi.BuildConfig
import com.uzukip.gokapi.data.settings.NotificationMode
import com.uzukip.gokapi.data.settings.Settings
import com.uzukip.gokapi.platform.scheduleNotifications
import com.uzukip.gokapi.ui.components.*
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.UnifiedPush

private tailrec fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

@Composable
internal fun TransferSettings(container: AppContainer, settings: Settings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var requestedMode by remember { mutableStateOf(NotificationMode.POLL) }
    var notice by remember { mutableStateOf<String?>(null) }
    fun configure(mode: NotificationMode) {
        scope.launch {
            container.settings.setNotifications(mode)
            scheduleNotifications(context, mode)
            if (mode == NotificationMode.INSTANT) {
                val activity = context.activity()
                if (activity != null) UnifiedPush.tryUseCurrentOrDefaultDistributor(activity) { success ->
                    if (success) UnifiedPush.register(context, messageForDistributor = "Gokapi uploads")
                    else scope.launch {
                        container.settings.setPushEndpoint("", "Install ntfy and set up your private HTTPS server. Polling every 15 minutes until then.")
                    }
                }
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) configure(requestedMode) else notice = "Notifications are blocked. You can enable them in Android's app settings."
    }
    GroupLabel("Uploads")
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Default expiry", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                listOf(1 to "1 d", 7 to "7 d", 30 to "30 d", 0 to "Never").forEachIndexed { index, option ->
                    ToggleButton(checked = settings.uploadExpiryDays == option.first, onCheckedChange = {
                        scope.launch { container.settings.setUploadDefaults(option.first, settings.uploadDownloads, settings.autoCopyLink) }
                    }, shapes = when (index) { 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes(); 3 -> ButtonGroupDefaults.connectedTrailingButtonShapes(); else -> ButtonGroupDefaults.connectedMiddleButtonShapes() },
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text(option.second) }
                }
            }
            var limit by remember(settings.uploadDownloads) { mutableStateOf(settings.uploadDownloads.toString()) }
            OutlinedTextField(limit, { limit = it.filter(Char::isDigit).take(9) }, label = { Text("Default allowed downloads") },
                supportingText = { Text("0 means unlimited") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large)
            BouncyTextButton(onClick = { limit.toIntOrNull()?.let { scope.launch { container.settings.setUploadDefaults(settings.uploadExpiryDays, it, settings.autoCopyLink) } } }) { Text("Save download limit") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Copy upload link automatically")
                    Text("Copies when Gokapi is open or when you return to it.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = settings.autoCopyLink, onCheckedChange = { scope.launch { container.settings.setUploadDefaults(settings.uploadExpiryDays, settings.uploadDownloads, it) } })
            }
        }
    }
    GroupLabel("Notifications")
    ConnectedChoices(
        listOf(Choice(NotificationMode.INSTANT, "Instant", "lightning-bolt"), Choice(NotificationMode.POLL, "15 min", "clock-outline"), Choice(NotificationMode.OFF, "Off", "bell-off-outline")),
        selected = settings.notificationMode,
        onSelect = { mode ->
            requestedMode = mode
            if (mode != NotificationMode.OFF && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else configure(mode)
        },
    )
    Text("Instant delivery needs ntfy as a UnifiedPush distributor. Set its private HTTPS server and read account in ntfy. Copy the device endpoint below to your server's endpoint file. Without a distributor, Gokapi checks every 15 minutes.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
    if (settings.notificationMode == NotificationMode.INSTANT) {
        if (settings.pushStatus.isNotBlank()) Text(settings.pushStatus, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BouncyTonalButton(onClick = { configure(NotificationMode.INSTANT) }) { Text("Reconnect") }
            BouncyTonalButton(onClick = {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Gokapi device endpoint", settings.pushEndpoint))
                notice = "Device endpoint copied. Add it to the private server endpoint file."
            }, enabled = settings.pushEndpoint.isNotBlank()) { Text("Copy endpoint") }
        }
    }
    notice?.let { Text(it, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
    GroupLabel("Open links")
    var linkRefresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) linkRefresh++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val enabled = remember(linkRefresh) { if (Build.VERSION.SDK_INT >= 31 && BuildConfig.FILES_HOST.isNotBlank()) runCatching {
        val state = context.getSystemService(DomainVerificationManager::class.java).getDomainVerificationUserState(context.packageName)
        state?.isLinkHandlingAllowed == true && state.hostToStateMap[BuildConfig.FILES_HOST] in listOf(DomainVerificationUserState.DOMAIN_STATE_SELECTED, DomainVerificationUserState.DOMAIN_STATE_VERIFIED)
    }.getOrDefault(false) else false }
    SegmentedGroup {
        SettingsRow(corners = segmentCorners(0, 1), title = "Open Gokapi links",
            subtitle = when { BuildConfig.FILES_HOST.isBlank() -> "Link handling isn't included in this build."; enabled -> "Enabled for your files host"; else -> "Approve your files host under Open by default" },
            onClick = if (BuildConfig.FILES_HOST.isBlank()) null else ({ context.startActivity(Intent(AndroidSettings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:${context.packageName}"))) }))
    }
}
