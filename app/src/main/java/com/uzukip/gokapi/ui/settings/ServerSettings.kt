package com.uzukip.gokapi.ui.settings

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uzukip.gokapi.AppContainer
import com.uzukip.gokapi.ui.components.BouncyButton
import com.uzukip.gokapi.ui.components.BouncyIconButton
import com.uzukip.gokapi.ui.components.BouncyTonalButton
import com.uzukip.gokapi.ui.components.IconBadge
import com.uzukip.gokapi.ui.components.MciIcon
import com.uzukip.gokapi.ui.components.ThemedTextField

/**
 * Server card: URL and API key fields, Save and Test connection, and a
 * tonal status panel that reports the result.
 */
@Composable
internal fun ServerSettings(container: AppContainer) {
    val model: ServerSettingsViewModel = viewModel(factory = remember(container) {
        viewModelFactory { initializer { ServerSettingsViewModel(container.credentials, container.client) } }
    })
    val state by model.state.collectAsState()
    var showKey by remember { mutableStateOf(false) }
    val editable = !state.loading && !state.busy
    GroupLabel("Server")
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge("server-network", "#00838F", 48.dp)
                    Text("Connect your files", style = MaterialTheme.typography.titleLargeEmphasized)
                }
                Text(
                    "Keep Tailscale connected. Use a dedicated android API key with VIEW and DOWNLOAD permissions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ThemedTextField(
                value = state.serverUrl,
                onValueChange = model::setUrl,
                label = "Server URL",
                placeholder = "https://files.example.com",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                enabled = editable,
            )
            ThemedTextField(
                value = state.apiKey,
                onValueChange = model::setKey,
                label = "API key",
                supportingText = "Encrypted on this device and excluded from backups. Test connection also saves.",
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
                    modifier = Modifier
                        .weight(1f)
                        .height(ButtonDefaults.MediumContainerHeight),
                ) { Text("Test connection") }
            }
            AnimatedVisibility(
                visible = !editable,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LoadingIndicator(Modifier.size(40.dp))
                    Text(
                        if (state.loading) "Loading server settings" else "Saving and checking settings",
                        style = MaterialTheme.typography.bodyMedium,
                    )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MciIcon(if (error) "alert-circle-outline" else "check-circle-outline", 22.dp, androidx.compose.material3.LocalContentColor.current)
                        Text(state.message.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
