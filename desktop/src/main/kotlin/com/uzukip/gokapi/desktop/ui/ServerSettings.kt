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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.uzukip.gokapi.data.server.ConnectionFailure
import com.uzukip.gokapi.data.server.GokapiClient
import com.uzukip.gokapi.data.server.PermissionFailure
import com.uzukip.gokapi.data.server.ServerCredentials
import com.uzukip.gokapi.data.server.normalizeServerUrl
import com.uzukip.gokapi.desktop.data.CredentialStore
import com.uzukip.gokapi.desktop.platform.AppDirs
import com.uzukip.gokapi.ui.components.BouncyButton
import com.uzukip.gokapi.ui.components.BouncyIconButton
import com.uzukip.gokapi.ui.components.BouncyTonalButton
import com.uzukip.gokapi.ui.components.IconBadge
import com.uzukip.gokapi.ui.components.MciIcon
import com.uzukip.gokapi.ui.components.ThemedTextField
import com.uzukip.gokapi.ui.settings.GroupLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ServerSettingsState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val loading: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
) {
    override fun toString() = "ServerSettingsState(loading=$loading, busy=$busy, isError=$isError)"
}

/** The Android app's ServerSettingsViewModel on a plain coroutine scope. */
internal class ServerSettingsModel(
    private val credentials: CredentialStore,
    private val client: GokapiClient,
    private val scope: CoroutineScope,
    private val onSaved: () -> Unit,
) {
    private val mutableState = MutableStateFlow(ServerSettingsState())
    val state = mutableState.asStateFlow()

    init {
        scope.launch {
            try {
                val saved = credentials.load()
                mutableState.value = ServerSettingsState(saved.serverUrl, saved.apiKey, loading = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutableState.value = ServerSettingsState(
                    loading = false, isError = true,
                    message = "Couldn't read the saved credentials. Enter the server URL and API key again.",
                )
            }
        }
    }

    fun setUrl(value: String) { if (!state.value.busy) mutableState.value = state.value.copy(serverUrl = value, message = null) }
    fun setKey(value: String) { if (!state.value.busy) mutableState.value = state.value.copy(apiKey = value, message = null) }

    fun save(test: Boolean) {
        val draft = state.value
        if (draft.busy || draft.loading) return
        val normalized = try {
            val url = normalizeServerUrl(draft.serverUrl)
            require(draft.apiKey.trim().isNotEmpty()) { "Enter your dedicated desktop API key." }
            require(draft.apiKey.trim().all { it.code in 32..126 }) { "The API key contains invalid characters." }
            ServerCredentials(url, draft.apiKey.trim())
        } catch (e: IllegalArgumentException) {
            mutableState.value = draft.copy(message = e.message, isError = true)
            return
        }
        mutableState.value = draft.copy(busy = true, message = null)
        scope.launch {
            var saved = false
            try {
                credentials.save(normalized)
                saved = true
                onSaved()
                mutableState.value = state.value.copy(serverUrl = normalized.serverUrl, apiKey = normalized.apiKey)
                val report = if (test) client.testConnection(normalized) else null
                mutableState.value = state.value.copy(
                    busy = false,
                    isError = report != null && !report.canViewFiles,
                    message = when {
                        report == null -> "Server settings saved."
                        report.canViewFiles -> "Connected to Gokapi ${report.version}. VIEW works. UPLOAD ${if (report.canUpload) "works" else "was denied"}. DOWNLOAD and DELETE are checked when you use them."
                        else -> "Gokapi ${report.version} is reachable. File access was denied. Check that your key is valid and has VIEW permission."
                    },
                )
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutableState.value = state.value.copy(
                    busy = false, isError = true,
                    message = if (!saved) "Couldn't save the credentials. Try again." else
                        (if (e is ConnectionFailure || e is PermissionFailure) e.message else null) ?: "Couldn't test the connection. Try again.",
                )
            }
        }
    }
}

/** Server card: URL and API key fields, Save and Test connection, and a tonal status panel. */
@Composable
internal fun ServerSettings(credentials: CredentialStore, client: GokapiClient, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val model = remember { ServerSettingsModel(credentials, client, scope, onSaved) }
    val state by model.state.collectAsState()
    var showKey by remember { mutableStateOf(false) }
    val editable = !state.loading && !state.busy
    GroupLabel("Server")
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge("server-network", "#00838F", 48.dp)
                    Text("Connect your files", style = MaterialTheme.typography.titleLargeEmphasized)
                }
                Text(
                    "Keep Tailscale connected. Use a dedicated API key with VIEW, UPLOAD, DOWNLOAD, and DELETE permissions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ThemedTextField(
                value = state.serverUrl,
                onValueChange = model::setUrl,
                label = "Server URL",
                placeholder = "https://files.example.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                enabled = editable,
            )
            ThemedTextField(
                value = state.apiKey,
                onValueChange = model::setKey,
                label = "API key",
                supportingText = if (AppDirs.isWindows) "Encrypted for your Windows account. Test connection also saves."
                    else "Stored in a file only your user can read. Test connection also saves.",
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = editable,
                trailing = {
                    BouncyIconButton(onClick = { showKey = !showKey }) {
                        MciIcon(
                            if (showKey) "eye-off-outline" else "eye-outline",
                            22.dp,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            contentDescription = if (showKey) "Hide API key" else "Show API key",
                        )
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BouncyTonalButton(
                    onClick = { model.save(test = false) },
                    enabled = editable,
                    height = ButtonDefaults.MediumContainerHeight,
                    modifier = Modifier.height(ButtonDefaults.MediumContainerHeight),
                ) { Text("Save") }
                BouncyButton(
                    onClick = { model.save(test = true) },
                    enabled = editable,
                    height = ButtonDefaults.MediumContainerHeight,
                    modifier = Modifier.weight(1f).height(ButtonDefaults.MediumContainerHeight),
                ) { Text("Test connection") }
            }
            AnimatedVisibility(
                visible = !editable,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LoadingIndicator(Modifier.size(40.dp))
                    Text(if (state.loading) "Loading server settings" else "Saving and checking settings", style = MaterialTheme.typography.bodyMedium)
                }
            }
            AnimatedVisibility(
                visible = state.message != null,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                val error = state.isError
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MciIcon(if (error) "alert-circle-outline" else "check-circle-outline", 22.dp, LocalContentColor.current)
                        Text(state.message.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
