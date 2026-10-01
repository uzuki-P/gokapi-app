package com.uzukip.gokapi.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class NotificationMode { OFF, INSTANT, POLL }

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ColorSource { DEFAULT, WALLPAPER, USER }
enum class VibrationStrength { OFF, LIGHT, DEFAULT, STRONG, CUSTOM }

data class Settings(
    val notificationMode: NotificationMode = NotificationMode.OFF,
    val uploadExpiryDays: Int = 7,
    val uploadDownloads: Int = 0,
    val autoCopyLink: Boolean = false,
    val pushEndpoint: String = "",
    val pushStatus: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorSource: ColorSource = ColorSource.DEFAULT,
    val userSeed: String? = null,
    val vibrationStrength: VibrationStrength = VibrationStrength.DEFAULT,
    val customVibrationMs: Int = 25,
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "gokapi_settings")

/** App preferences in DataStore; same keys and shape as spendr-kt's settings. */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsStore

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            notificationMode = p[KEY_NOTIFICATIONS]?.let { runCatching { NotificationMode.valueOf(it) }.getOrNull() } ?: NotificationMode.OFF,
            uploadExpiryDays = (p[KEY_EXPIRY] ?: 7).coerceAtLeast(0),
            uploadDownloads = (p[KEY_DOWNLOADS] ?: 0).coerceAtLeast(0),
            autoCopyLink = p[KEY_AUTO_COPY] ?: false,
            pushEndpoint = p[KEY_PUSH_ENDPOINT].orEmpty(),
            pushStatus = p[KEY_PUSH_STATUS].orEmpty(),
            themeMode = p[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            colorSource = p[KEY_COLOR_SOURCE]?.let { runCatching { ColorSource.valueOf(it) }.getOrNull() }
                ?: ColorSource.DEFAULT,
            userSeed = p[KEY_USER_SEED],
            vibrationStrength = p[KEY_VIBRATION]?.let { runCatching { VibrationStrength.valueOf(it) }.getOrNull() }
                ?: VibrationStrength.DEFAULT,
            customVibrationMs = (p[KEY_CUSTOM_VIBRATION_MS] ?: 25).coerceIn(1, 200),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[KEY_THEME_MODE] = mode.name }
    suspend fun setColorSource(source: ColorSource) = store.edit { it[KEY_COLOR_SOURCE] = source.name }
    suspend fun setUserSeed(hex: String?) = store.edit {
        if (hex == null) it.remove(KEY_USER_SEED) else it[KEY_USER_SEED] = hex
    }

    suspend fun setVibration(strength: VibrationStrength, customMs: Int? = null) = store.edit {
        it[KEY_VIBRATION] = strength.name
        if (customMs != null) it[KEY_CUSTOM_VIBRATION_MS] = customMs.coerceIn(1, 200)
    }

    suspend fun setNotifications(mode: NotificationMode) = store.edit { it[KEY_NOTIFICATIONS] = mode.name }
    suspend fun setUploadDefaults(days: Int, downloads: Int, copy: Boolean) = store.edit {
        it[KEY_EXPIRY] = days.coerceAtLeast(0)
        it[KEY_DOWNLOADS] = downloads.coerceAtLeast(0)
        it[KEY_AUTO_COPY] = copy
    }
    suspend fun setPushEndpoint(endpoint: String, status: String) = store.edit {
        it[KEY_PUSH_ENDPOINT] = endpoint
        it[KEY_PUSH_STATUS] = status
    }
    suspend fun newFileIds(server: String, ids: Set<String>): Set<String> {
        var newIds = emptySet<String>()
        store.edit {
            if (it[KEY_SEEN_SERVER] == server && it[KEY_SEEN_IDS] != null) newIds = ids - it[KEY_SEEN_IDS].orEmpty()
            it[KEY_SEEN_SERVER] = server
            it[KEY_SEEN_IDS] = ids
        }
        return newIds
    }
    suspend fun rememberUploadLink(link: String) = store.edit { it[KEY_LAST_LINK] = link }
    suspend fun takeUploadLink(): String? {
        var link: String? = null
        store.edit { link = it[KEY_LAST_LINK]; it.remove(KEY_LAST_LINK) }
        return link
    }

    private companion object {
        val KEY_NOTIFICATIONS = stringPreferencesKey("notifications")
        val KEY_EXPIRY = intPreferencesKey("upload_expiry")
        val KEY_DOWNLOADS = intPreferencesKey("upload_downloads")
        val KEY_AUTO_COPY = booleanPreferencesKey("auto_copy_link")
        val KEY_PUSH_ENDPOINT = stringPreferencesKey("push_endpoint")
        val KEY_PUSH_STATUS = stringPreferencesKey("push_status")
        val KEY_SEEN_IDS = stringSetPreferencesKey("seen_ids")
        val KEY_SEEN_SERVER = stringPreferencesKey("seen_server")
        val KEY_LAST_LINK = stringPreferencesKey("last_upload_link")

        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_COLOR_SOURCE = stringPreferencesKey("color_source")
        val KEY_USER_SEED = stringPreferencesKey("user_seed")
        val KEY_VIBRATION = stringPreferencesKey("vibration_strength")
        val KEY_CUSTOM_VIBRATION_MS = intPreferencesKey("custom_vibration_ms")
    }
}
