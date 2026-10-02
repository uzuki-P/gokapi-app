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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    SegmentedGroup {
        MorphSurface(onClick = null, corners = segmentCorners(0, 2)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Default expiry",
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    ConnectedChoices(
                        options = ExpiryChoices,
                        selected = settings.uploadExpiryDays,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        onSelect = { days -> scope.launch { container.settings.setUploadDefaults(days, settings.uploadDownloads, settings.autoCopyLink) } },
                    )
                }
                var limit by remember(settings.uploadDownloads) { mutableStateOf(settings.uploadDownloads.toString()) }
                val pending = limit.toIntOrNull()?.takeIf { it != settings.uploadDownloads }
                ThemedTextField(
                    value = limit,
                    onValueChange = { limit = it.filter(Char::isDigit).take(9) },
                    label = "Default allowed downloads",
                    placeholder = "0",
                    supportingText = "0 means unlimited",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailing = {
                        AnimatedVisibility(
                            visible = pending != null,
                            enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                            exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                        ) {
                            FilledIconButton(
                                onClick = { pending?.let { scope.launch { container.settings.setUploadDefaults(settings.uploadExpiryDays, it, settings.autoCopyLink) } } },
                                shapes = IconButtonDefaults.shapes(),
                            ) { MciIcon("check", 22.dp, LocalContentColor.current, contentDescription = "Save download limit") }
                        }
                    },
                )
            }
        }
        SettingsRow(
            corners = segmentCorners(1, 2),
            icon = { IconBadge("content-copy", "#3C7CAB", 40.dp) },
            title = "Copy upload link automatically",
            subtitle = "When Gokapi is open or you return to it",
            onClick = { scope.launch { container.settings.setUploadDefaults(settings.uploadExpiryDays, settings.uploadDownloads, !settings.autoCopyLink) } },
            trailing = {
                Switch(
                    checked = settings.autoCopyLink,
                    onCheckedChange = { scope.launch { container.settings.setUploadDefaults(settings.uploadExpiryDays, settings.uploadDownloads, it) } },
                )
            },
        )
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
    Text(
        "Instant delivery needs ntfy as a UnifiedPush distributor. Set its private HTTPS server and read account in ntfy, then copy the device endpoint to your server's endpoint file. Without a distributor, Gokapi checks every 15 minutes.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp),
    )
    AnimatedVisibility(
        visible = settings.notificationMode == NotificationMode.INSTANT,
        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        SegmentedGroup(Modifier.padding(top = 12.dp)) {
            SettingsRow(
                corners = segmentCorners(0, 2),
                icon = { IconBadge("access-point-network", "#7B6A9E", 40.dp) },
                title = "Reconnect distributor",
                subtitle = settings.pushStatus.ifBlank { "Registers this device with ntfy again" },
                onClick = { configure(NotificationMode.INSTANT) },
            )
            SettingsRow(
                corners = segmentCorners(1, 2),
                icon = { IconBadge("content-copy", "#3D8075", 40.dp) },
                title = "Copy device endpoint",
                subtitle = if (settings.pushEndpoint.isBlank()) "No endpoint yet. Reconnect first." else "Add it to the private server endpoint file",
                onClick = if (settings.pushEndpoint.isBlank()) null else ({
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Gokapi device endpoint", settings.pushEndpoint))
                    notice = "Device endpoint copied. Add it to the private server endpoint file."
                }),
            )
        }
    }
    AnimatedVisibility(
        visible = notice != null,
        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(notice.orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
        }
    }

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
    SettingsRow(
        corners = segmentCorners(0, 1),
        icon = { IconBadge(if (enabled) "link-variant" else "link-variant-off", "#A86829", 40.dp) },
        title = "Open Gokapi links",
        subtitle = when {
            BuildConfig.FILES_HOST.isBlank() -> "Link handling isn't included in this build"
            enabled -> "Enabled for your files host"
            else -> "Approve your files host under Open by default"
        },
        onClick = if (BuildConfig.FILES_HOST.isBlank()) null else ({ context.startActivity(Intent(AndroidSettings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:${context.packageName}"))) }),
    )
}
