package com.uzukip.gokapi.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzukip.gokapi.data.server.ConnectionFailure
import com.uzukip.gokapi.data.server.CredentialStore
import com.uzukip.gokapi.data.server.GokapiClient
import com.uzukip.gokapi.data.server.PairingLink
import com.uzukip.gokapi.data.server.RelayRoute
import com.uzukip.gokapi.data.server.ServerCredentials
import com.uzukip.gokapi.data.server.normalizeServerUrl
import com.uzukip.gokapi.data.server.parsePairingLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ServerSettingsState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val relay: RelayRoute? = null,
    val pending: PairingLink? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
) {
    override fun toString() = "ServerSettingsState(loading=$loading, busy=$busy, isError=$isError)"
}

internal class ServerSettingsViewModel(
    private val credentials: CredentialStore,
    private val client: GokapiClient,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ServerSettingsState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val saved = credentials.load()
                mutableState.value = ServerSettingsState(saved.serverUrl, saved.apiKey, saved.relay, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = ServerSettingsState(
                    loading = false, isError = true,
                    message = "Couldn't read the saved credentials. Enter the server URL and API key again.",
                )
            }
        }
    }

    fun setUrl(value: String) {
        if (!state.value.busy) mutableState.value = state.value.copy(serverUrl = value, message = null)
    }

    fun setKey(value: String) {
        if (!state.value.busy) mutableState.value = state.value.copy(apiKey = value, message = null)
    }

    /** Parses a link from the clipboard or an app link and asks before replacing the server. */
    fun offerPairing(text: String) {
        if (state.value.busy) return
        mutableState.value = try {
            state.value.copy(pending = parsePairingLink(text), message = null)
        } catch (e: IllegalArgumentException) {
            state.value.copy(pending = null, message = e.message, isError = true)
        }
    }

    fun cancelPairing() { mutableState.value = state.value.copy(pending = null) }

    fun confirmPairing() {
        val pairing = state.value.pending ?: return
        if (state.value.busy || state.value.loading) return
        val c = pairing.credentials
        mutableState.value = state.value.copy(serverUrl = c.serverUrl, apiKey = c.apiKey, relay = c.relay, pending = null)
        save(test = true)
    }

    fun turnOffRelay() {
        if (state.value.busy || state.value.loading) return
        mutableState.value = state.value.copy(relay = null)
        save(test = false)
    }

    fun save(test: Boolean) {
        val draft = state.value
        if (draft.busy || draft.loading) return
        val normalized = try {
            val url = normalizeServerUrl(draft.serverUrl)
            require(draft.apiKey.trim().isNotEmpty()) { "Enter your dedicated android API key." }
            require(draft.apiKey.trim().all { it.code in 32..126 }) { "The API key contains invalid characters." }
            ServerCredentials(url, draft.apiKey.trim(), draft.relay)
        } catch (e: IllegalArgumentException) {
            mutableState.value = draft.copy(message = e.message, isError = true)
            return
        }
        mutableState.value = draft.copy(busy = true, message = null)
        viewModelScope.launch {
            var saved = false
            try {
                credentials.save(normalized)
                saved = true
                mutableState.value = state.value.copy(serverUrl = normalized.serverUrl, apiKey = normalized.apiKey)
                val report = if (test) client.testConnection(normalized) else null
                mutableState.value = state.value.copy(
                    busy = false,
                    isError = report != null && (!report.canViewFiles || report.relayError != null),
                    message = when {
                        report == null -> "Server settings saved."
                        report.canViewFiles -> "Connected to Gokapi ${report.version}. VIEW works. UPLOAD ${if (report.canUpload) "works" else "was denied"}. DOWNLOAD and DELETE are checked when you use them." + report.routeNote()
                        else -> "Gokapi ${report.version} is reachable. File access was denied. Check that your android key is valid and has VIEW permission."
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = state.value.copy(
                    busy = false, isError = true,
                    message = if (!saved) "Couldn't save the credentials. Try again." else
                        (if (e is ConnectionFailure || e is com.uzukip.gokapi.data.server.PermissionFailure) e.message else null) ?: "Couldn't test the connection. Try again.",
                )
            }
        }
    }
}
