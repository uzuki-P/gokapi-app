package com.uzukip.gokapi.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uzukip.gokapi.data.server.ConnectionReport
import com.uzukip.gokapi.data.server.PairingLink
import com.uzukip.gokapi.data.server.Route
import com.uzukip.gokapi.ui.components.BouncyButton
import com.uzukip.gokapi.ui.components.BouncyTextButton
import com.uzukip.gokapi.ui.components.BouncyTonalButton
import com.uzukip.gokapi.ui.components.MciIcon
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The server card's relay row, shared by both apps: whether requests can leave the
 * tailnet, a Paste pairing link button, and the confirmation for a pending pairing.
 */
@Composable
fun RemoteAccess(
    relayUrl: String?,
    pending: PairingLink?,
    enabled: Boolean,
    onPaste: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onTurnOff: () -> Unit,
) {
    if (pending != null) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MciIcon("link-variant", 22.dp, LocalContentColor.current)
                    Text("Pair as ${pending.device}?", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "Server: ${host(pending.credentials.serverUrl)}\nRelay: ${pending.credentials.relay?.url?.let(::host).orEmpty()}\n" +
                        "Replaces the saved server URL and API key. Only continue if you just ran the pair command.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                    BouncyTextButton(onClick = onCancel, enabled = enabled) { Text("Cancel") }
                    BouncyButton(onClick = onConfirm, enabled = enabled) { Text("Pair") }
                }
            }
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MciIcon(if (relayUrl != null) "cloud-lock-outline" else "earth", 22.dp, MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text("Away from Tailscale", style = MaterialTheme.typography.titleSmall)
            Text(
                relayUrl?.let { "Uses the relay at ${host(it)} when the tailnet is unreachable." }
                    ?: "Off. Run the pair command on the server, then open or paste its link.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BouncyTonalButton(onClick = onPaste, enabled = enabled) {
            MciIcon("content-paste", 18.dp, LocalContentColor.current)
            Text("Paste pairing link", Modifier.padding(start = 8.dp))
        }
        if (relayUrl != null) BouncyTextButton(onClick = onTurnOff, enabled = enabled) { Text("Turn off") }
    }
}

private fun host(url: String) = url.toHttpUrlOrNull()?.host ?: url

/** Appended to a successful connection test: which route answered and how the relay fared. */
fun ConnectionReport.routeNote(): String = when {
    route == Route.RELAY -> " Reached through the relay, since the tailnet didn't answer."
    relayError != null -> " The relay check failed: $relayError"
    else -> ""
}
