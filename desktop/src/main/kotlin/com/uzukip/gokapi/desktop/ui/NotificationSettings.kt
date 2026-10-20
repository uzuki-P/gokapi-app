package com.uzukip.gokapi.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.uzukip.gokapi.desktop.DesktopContainer
import com.uzukip.gokapi.desktop.data.NotificationMode
import com.uzukip.gokapi.desktop.notify.NtfySubscriber
import com.uzukip.gokapi.ui.components.BouncyIconButton
import com.uzukip.gokapi.ui.components.BouncyTonalButton
import com.uzukip.gokapi.ui.components.Choice
import com.uzukip.gokapi.ui.components.ConnectedChoices
import com.uzukip.gokapi.ui.components.MciIcon
import com.uzukip.gokapi.ui.components.ThemedTextField
import com.uzukip.gokapi.ui.settings.GroupLabel
import kotlinx.coroutines.launch

/**
 * New-file notifications: Off, a check every 5 minutes, or Instant through ntfy. Instant
 * needs the topic the upload hook publishes to and an account or token that can read it.
 */
@Composable
internal fun NotificationSettings(container: DesktopContainer, trayAvailable: Boolean) {
    val settings by container.settings.settings.collectAsState()
    val saved by container.ntfy.config.collectAsState()
    val status by container.ntfy.status.collectAsState()
    val scope = rememberCoroutineScope()
    var topic by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val loaded = container.ntfy.load()
        topic = loaded.serverUrl
        secret = loaded.apiKey
    }
    val changed = topic.trim().trimEnd('/') != saved?.serverUrl.orEmpty() || secret.trim() != saved?.apiKey.orEmpty()

    GroupLabel("New file notifications")
    ConnectedChoices(
        options = listOf(
            Choice(NotificationMode.INSTANT, "Instant", "lightning-bolt"),
            Choice(NotificationMode.POLL, "Every 5 min", "clock-outline"),
            Choice(NotificationMode.OFF, "Off", "bell-off-outline"),
        ),
        selected = settings.notificationMode,
        onSelect = { mode -> scope.launch { container.settings.update { it.copy(notificationMode = mode) } } },
    )
    Text(
        when {
            settings.notificationMode == NotificationMode.OFF -> "Gokapi won't tell you about files others upload."
            !trayAvailable && !com.uzukip.gokapi.desktop.platform.AppDirs.isWindows ->
                "Notifications come while Gokapi runs. Keep it running in the tray to get them with the window closed."
            else -> "Notifications come while Gokapi runs, including from the tray. Your own uploads don't notify."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
    AnimatedVisibility(
        visible = settings.notificationMode == NotificationMode.INSTANT,
        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Instant uses your private ntfy server. Enter the topic the upload helper publishes to: the regular topic " +
                        "set as NTFY_URL, such as /gokapi-uploads, or a desktop topic added to ntfy-endpoints. Polling every 15 min stays on as a fallback.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ThemedTextField(
                    value = topic,
                    onValueChange = { topic = it; error = null },
                    label = "ntfy topic URL",
                    placeholder = "https://ntfy.example.com/gokapi-uploads",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                )
                ThemedTextField(
                    value = secret,
                    onValueChange = { secret = it; error = null },
                    label = "Access token, or user:password",
                    supportingText = "The android account can read these topics. Stored like the API key.",
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailing = {
                        BouncyIconButton(onClick = { showSecret = !showSecret }) {
                            MciIcon(
                                if (showSecret) "eye-off-outline" else "eye-outline", 22.dp, MaterialTheme.colorScheme.onSurfaceVariant,
                                contentDescription = if (showSecret) "Hide ntfy secret" else "Show ntfy secret",
                            )
                        }
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BouncyTonalButton(
                        enabled = changed,
                        onClick = {
                            val problem = runCatching { NtfySubscriber.streamUrl(topic) }.exceptionOrNull()?.message
                                ?: if (secret.isBlank()) "Enter an access token or user:password." else null
                            if (problem != null) { error = problem; return@BouncyTonalButton }
                            scope.launch {
                                runCatching { container.ntfy.save(topic, secret) }
                                    .onFailure { error = "Couldn't save the ntfy settings. Try again." }
                            }
                        },
                    ) { Text("Save") }
                    val line = error?.let { com.uzukip.gokapi.desktop.notify.NtfyStatus(it, error = true) } ?: status
                    if (line != null) {
                        val tint = if (line.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        MciIcon(if (line.error) "alert-circle-outline" else "access-point", 20.dp, tint)
                        Text(line.text, style = MaterialTheme.typography.bodyMedium, color = if (line.error) tint else LocalContentColor.current, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
