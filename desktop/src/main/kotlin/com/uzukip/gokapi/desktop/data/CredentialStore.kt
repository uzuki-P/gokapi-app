package com.uzukip.gokapi.desktop.data

import com.sun.jna.platform.win32.Crypt32Util
import com.uzukip.gokapi.data.server.ServerCredentials
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The server URL and API key in `credentials.properties` under the config directory. On
 * Windows the key is encrypted with DPAPI for the signed-in user. On Linux the file is
 * readable by its owner only, the same protection as an SSH key.
 */
class CredentialStore(private val file: File, private val cipher: KeyCipher = KeyCipher.forCurrentOs()) {
    suspend fun load(): ServerCredentials = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext ServerCredentials("", "")
        val properties = Properties().apply { file.inputStream().use(::load) }
        ServerCredentials(
            properties.getProperty(URL).orEmpty(),
            properties.getProperty(KEY)?.let(cipher::decrypt).orEmpty(),
        )
    }

    suspend fun save(credentials: ServerCredentials) = withContext(Dispatchers.IO) {
        val properties = Properties().apply {
            setProperty(URL, credentials.serverUrl)
            setProperty(KEY, cipher.encrypt(credentials.apiKey))
        }
        file.parentFile.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.delete()
        ownerOnly(temporary)
        temporary.outputStream().use { properties.store(it, null) }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        Unit
    }

    // Created with owner-only permissions before the key is written, so it is never readable by others.
    private fun ownerOnly(target: File) {
        runCatching {
            Files.createFile(target.toPath(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        }.onFailure { target.createNewFile() }
    }

    private companion object {
        const val URL = "server_url"
        const val KEY = "api_key"
    }
}

/** Protects the stored API key. */
interface KeyCipher {
    fun encrypt(value: String): String
    fun decrypt(value: String): String

    companion object {
        fun forCurrentOs(): KeyCipher = if (System.getProperty("os.name").orEmpty().startsWith("Windows")) DpapiCipher else PlainCipher
    }
}

/** Relies on the file's owner-only permissions. */
object PlainCipher : KeyCipher {
    override fun encrypt(value: String) = value
    override fun decrypt(value: String) = value
}

/** Windows DPAPI, tied to the signed-in user's account. */
object DpapiCipher : KeyCipher {
    override fun encrypt(value: String): String =
        "dpapi:" + Base64.getEncoder().encodeToString(Crypt32Util.cryptProtectData(value.toByteArray(Charsets.UTF_8)))

    override fun decrypt(value: String): String =
        if (!value.startsWith("dpapi:")) value
        else String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(value.removePrefix("dpapi:"))), Charsets.UTF_8)
}
