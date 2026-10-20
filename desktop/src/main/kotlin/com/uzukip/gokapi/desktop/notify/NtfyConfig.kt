package com.uzukip.gokapi.desktop.notify

import com.uzukip.gokapi.data.server.ServerCredentials
import com.uzukip.gokapi.desktop.data.CredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The ntfy topic URL and secret (an access token or `user:password`), stored like the
 * Gokapi API key, and the subscription's status for the Settings screen.
 */
class NtfyConfig(private val store: CredentialStore) {
    private val state = MutableStateFlow<ServerCredentials?>(null)
    val config: StateFlow<ServerCredentials?> = state.asStateFlow()
    val status = MutableStateFlow<NtfyStatus?>(null)

    suspend fun load(): ServerCredentials = (state.value ?: store.load()).also { state.value = it }

    suspend fun save(topicUrl: String, secret: String) {
        val saved = ServerCredentials(topicUrl.trim().trimEnd('/'), secret.trim())
        store.save(saved)
        state.value = saved
    }
}
