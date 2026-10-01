package com.uzukip.gokapi.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uzukip.gokapi.AppContainer
import com.uzukip.gokapi.ui.components.BouncyButton
import com.uzukip.gokapi.ui.components.BouncyTextButton
import com.uzukip.gokapi.ui.components.BouncyTonalButton

@Composable
internal fun ServerSettings(container: AppContainer) {
    val model: ServerSettingsViewModel = viewModel(factory = remember(container) {
        viewModelFactory { initializer { ServerSettingsViewModel(container.credentials, container.client) } }
    })
    val state by model.state.collectAsState()
    var showKey by remember { mutableStateOf(false) }
    GroupLabel("Server")
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Connect your files", style = MaterialTheme.typography.titleLargeEmphasized)
            Text(
                "Keep Tailscale connected. Create a dedicated android API key with VIEW and DOWNLOAD permissions in Gokapi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = model::setUrl,
                label = { Text("Server URL") },
                placeholder = { Text("https://files.example.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                enabled = !state.loading && !state.busy,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = model::setKey,
                label = { Text("API key") },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                enabled = !state.loading && !state.busy,
                trailingIcon = {
                    BouncyTextButton(onClick = { showKey = !showKey }) {
                        Text(if (showKey) "Hide" else "Show")
                    }
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Your API key is encrypted on this device and excluded from backups. Test connection also saves these settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BouncyTonalButton(
                    onClick = { model.save(test = false) },
                    enabled = !state.loading && !state.busy,
                    modifier = Modifier.height(56.dp), height = 56.dp,
                ) { Text("Save") }
                BouncyButton(
                    onClick = { model.save(test = true) },
                    enabled = !state.loading && !state.busy,
                    modifier = Modifier.weight(1f).height(56.dp), height = 56.dp,
                ) { Text("Test connection") }
            }
            if (state.busy || state.loading) {
                CircularProgressIndicator()
                Text(if (state.loading) "Loading server settings" else "Saving and checking settings")
            }
            state.message?.let { message ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (state.isError) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
