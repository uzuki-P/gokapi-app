package com.uzukip.gokapi.data.server

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.credentialDataStore by preferencesDataStore(name = "gokapi_credentials")

internal class CredentialCipher(private val key: () -> SecretKey) {
    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encoder = Base64.getEncoder()
        return encoder.encodeToString(cipher.iv) + "." +
            encoder.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
    }

    fun decrypt(value: String): String {
        val parts = value.split('.')
        require(parts.size == 2)
        val decoder = Base64.getDecoder()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decoder.decode(parts[0])))
        return String(cipher.doFinal(decoder.decode(parts[1])), Charsets.UTF_8)
    }
}

class CredentialStore(context: Context) {
    private val store = context.applicationContext.credentialDataStore
    private val cipher = CredentialCipher(::keystoreKey)

    suspend fun load(): ServerCredentials = withContext(Dispatchers.IO) {
        val preferences = store.data.first()
        val relay = preferences[RELAY_URL]?.let { url ->
            RelayRoute(
                url,
                preferences[RELAY_ID]?.let(cipher::decrypt).orEmpty(),
                preferences[RELAY_SECRET]?.let(cipher::decrypt).orEmpty(),
            )
        }
        ServerCredentials(
            preferences[URL].orEmpty(),
            preferences[KEY]?.let(cipher::decrypt).orEmpty(),
            relay,
        )
    }

    suspend fun save(credentials: ServerCredentials) = withContext(Dispatchers.IO) {
        val encrypted = cipher.encrypt(credentials.apiKey)
        val relay = credentials.relay
        val relayId = relay?.id?.let(cipher::encrypt)
        val relaySecret = relay?.secret?.let(cipher::encrypt)
        store.edit {
            it[URL] = credentials.serverUrl
            it[KEY] = encrypted
            if (relay == null) {
                it -= RELAY_URL
                it -= RELAY_ID
                it -= RELAY_SECRET
            } else {
                it[RELAY_URL] = relay.url
                it[RELAY_ID] = relayId!!
                it[RELAY_SECRET] = relaySecret!!
            }
        }
        Unit
    }

    suspend fun encryptJobSecret(value: String): String = withContext(Dispatchers.IO) { cipher.encrypt(value) }
    suspend fun decryptJobSecret(value: String): String = withContext(Dispatchers.IO) { cipher.decrypt(value) }

    @Synchronized
    private fun keystoreKey(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val ALIAS = "gokapi_api_key"
        val URL = stringPreferencesKey("server_url")
        val KEY = stringPreferencesKey("encrypted_api_key")
        val RELAY_URL = stringPreferencesKey("relay_url")
        val RELAY_ID = stringPreferencesKey("encrypted_relay_id")
        val RELAY_SECRET = stringPreferencesKey("encrypted_relay_secret")
    }
}
