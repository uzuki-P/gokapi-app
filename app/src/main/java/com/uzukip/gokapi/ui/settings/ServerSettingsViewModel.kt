package com.uzukip.gokapi.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzukip.gokapi.data.server.ConnectionFailure
import com.uzukip.gokapi.data.server.CredentialStore
import com.uzukip.gokapi.data.server.GokapiClient
import com.uzukip.gokapi.data.server.ServerCredentials
import com.uzukip.gokapi.data.server.normalizeServerUrl
import kotlinx.coroutines.CancellationException
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
                mutableState.value = ServerSettingsState(saved.serverUrl, saved.apiKey, loading = false)
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

    fun save(test: Boolean) {
        val draft = state.value
        if (draft.busy || draft.loading) return
        val normalized = try {
            val url = normalizeServerUrl(draft.serverUrl)
            require(draft.apiKey.trim().isNotEmpty()) { "Enter your dedicated android API key." }
            require(draft.apiKey.trim().all { it.code in 32..126 }) { "The API key contains invalid characters." }
            ServerCredentials(url, draft.apiKey.trim())
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
                    isError = report != null && !report.canViewFiles,
                    message = when {
                        report == null -> "Server settings saved."
                        report.canViewFiles -> "Connected to Gokapi ${report.version}. VIEW works. UPLOAD ${if (report.canUpload) "works" else "was denied"}. DOWNLOAD and DELETE are checked when you use them."
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
