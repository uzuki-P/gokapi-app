package com.uzukip.gokapi.data.server

/**
 * A public route to the server through the relay Worker, used when the tailnet URL is
 * unreachable. The Worker checks this device's token before forwarding through the
 * tunnel; Gokapi still checks the API key.
 */
class RelayRoute(val url: String, val id: String, val secret: String) {
    override fun toString() = "RelayRoute($url)"
}

// No toString() containing the key, and no SavedStateHandle or Compose saveable state.
// serverUrl stays the server's identity (links, seen files, saved downloads) whichever
// route a request takes.
class ServerCredentials(val serverUrl: String, val apiKey: String, val relay: RelayRoute? = null)
