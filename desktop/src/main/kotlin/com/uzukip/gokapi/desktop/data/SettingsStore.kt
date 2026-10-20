package com.uzukip.gokapi.desktop.data

import java.io.File
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ColorSource { DEFAULT, USER }

/** The Android app's appearance and upload settings, minus haptics, wallpaper colors, and notifications. */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorSource: ColorSource = ColorSource.DEFAULT,
    val userSeed: String? = null,
    val uploadExpiryDays: Int = 7,
    val uploadDownloads: Int = 0,
    val autoCopyLink: Boolean = false,
)

/** [Settings] in `settings.properties` under the config directory. Loaded once, written on each change. */
class SettingsStore(private val file: File) {
    private val writes = Mutex()
    private val state = MutableStateFlow(read())
    val settings: StateFlow<Settings> = state.asStateFlow()

    suspend fun update(change: (Settings) -> Settings) = writes.withLock {
        state.update(change)
        val current = state.value
        withContext(Dispatchers.IO) {
            val properties = Properties().apply {
                setProperty("theme_mode", current.themeMode.name)
                setProperty("color_source", current.colorSource.name)
                current.userSeed?.let { setProperty("user_seed", it) }
                setProperty("upload_expiry", current.uploadExpiryDays.toString())
                setProperty("upload_downloads", current.uploadDownloads.toString())
                setProperty("auto_copy_link", current.autoCopyLink.toString())
            }
            file.parentFile.mkdirs()
            val temporary = File(file.parentFile, file.name + ".tmp")
            temporary.outputStream().use { properties.store(it, null) }
            check(temporary.renameTo(file) || (file.delete() && temporary.renameTo(file))) { "Couldn't save settings." }
        }
    }

    private fun read(): Settings {
        val p = Properties()
        runCatching { file.inputStream().use(p::load) }
        val defaults = Settings()
        return Settings(
            themeMode = enumOr(p.getProperty("theme_mode"), defaults.themeMode),
            colorSource = enumOr(p.getProperty("color_source"), defaults.colorSource),
            userSeed = p.getProperty("user_seed")?.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) },
            uploadExpiryDays = p.getProperty("upload_expiry")?.toIntOrNull()?.coerceAtLeast(0) ?: defaults.uploadExpiryDays,
            uploadDownloads = p.getProperty("upload_downloads")?.toIntOrNull()?.coerceAtLeast(0) ?: defaults.uploadDownloads,
            autoCopyLink = p.getProperty("auto_copy_link")?.toBooleanStrictOrNull() ?: defaults.autoCopyLink,
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
