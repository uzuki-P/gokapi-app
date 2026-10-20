package com.uzukip.gokapi.data.server

// The Android app declares this class in its Android-only CredentialStore.kt; the shared
// GokapiClient needs it in this package. No toString() containing the key.
class ServerCredentials(val serverUrl: String, val apiKey: String)
