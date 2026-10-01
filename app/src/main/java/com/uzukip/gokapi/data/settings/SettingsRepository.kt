package com.uzukip.gokapi.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ColorSource { DEFAULT, WALLPAPER, USER }
enum class VibrationStrength { OFF, LIGHT, DEFAULT, STRONG, CUSTOM }

data class Settings(
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

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_COLOR_SOURCE = stringPreferencesKey("color_source")
        val KEY_USER_SEED = stringPreferencesKey("user_seed")
        val KEY_VIBRATION = stringPreferencesKey("vibration_strength")
        val KEY_CUSTOM_VIBRATION_MS = intPreferencesKey("custom_vibration_ms")
    }
}
