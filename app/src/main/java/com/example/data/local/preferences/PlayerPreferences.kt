package com.example.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ResizeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.playerDataStore: DataStore<Preferences> by preferencesDataStore(name = "player_settings")

data class PlayerSettings(
    val defaultPlaybackSpeed: Float = 1.0f,
    val resumePlaybackEnabled: Boolean = true,
    val seekIntervalSeconds: Int = 10,
    val defaultResizeMode: ResizeMode = ResizeMode.FIT,
    val gestureControlsEnabled: Boolean = true,
    val preferHardwareDecoder: Boolean = true,
    val defaultSubtitleLanguage: String = "",
    val subtitleTextSizeSp: Float = 18f,
    val themeMode: String = "SYSTEM" // "SYSTEM", "DARK", "LIGHT"
)

class PlayerPreferences(private val context: Context) {

    private object Keys {
        val SPEED = floatPreferencesKey("default_speed")
        val RESUME = booleanPreferencesKey("resume_enabled")
        val SEEK_INTERVAL = intPreferencesKey("seek_interval")
        val RESIZE_MODE = stringPreferencesKey("default_resize_mode")
        val GESTURES = booleanPreferencesKey("gestures_enabled")
        val HARDWARE_DECODER = booleanPreferencesKey("prefer_hardware_decoder")
        val SUBTITLE_LANG = stringPreferencesKey("default_sub_lang")
        val SUBTITLE_SIZE = floatPreferencesKey("subtitle_size")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val settingsFlow: Flow<PlayerSettings> = context.playerDataStore.data.map { prefs ->
        PlayerSettings(
            defaultPlaybackSpeed = prefs[Keys.SPEED] ?: 1.0f,
            resumePlaybackEnabled = prefs[Keys.RESUME] ?: true,
            seekIntervalSeconds = prefs[Keys.SEEK_INTERVAL] ?: 10,
            defaultResizeMode = try {
                ResizeMode.valueOf(prefs[Keys.RESIZE_MODE] ?: ResizeMode.FIT.name)
            } catch (_: Exception) {
                ResizeMode.FIT
            },
            gestureControlsEnabled = prefs[Keys.GESTURES] ?: true,
            preferHardwareDecoder = prefs[Keys.HARDWARE_DECODER] ?: true,
            defaultSubtitleLanguage = prefs[Keys.SUBTITLE_LANG] ?: "",
            subtitleTextSizeSp = prefs[Keys.SUBTITLE_SIZE] ?: 18f,
            themeMode = prefs[Keys.THEME_MODE] ?: "SYSTEM"
        )
    }

    suspend fun setSpeed(speed: Float) {
        context.playerDataStore.edit { it[Keys.SPEED] = speed }
    }

    suspend fun setResumeEnabled(enabled: Boolean) {
        context.playerDataStore.edit { it[Keys.RESUME] = enabled }
    }

    suspend fun setSeekInterval(seconds: Int) {
        context.playerDataStore.edit { it[Keys.SEEK_INTERVAL] = seconds }
    }

    suspend fun setResizeMode(mode: ResizeMode) {
        context.playerDataStore.edit { it[Keys.RESIZE_MODE] = mode.name }
    }

    suspend fun setGesturesEnabled(enabled: Boolean) {
        context.playerDataStore.edit { it[Keys.GESTURES] = enabled }
    }

    suspend fun setHardwareDecoder(prefer: Boolean) {
        context.playerDataStore.edit { it[Keys.HARDWARE_DECODER] = prefer }
    }

    suspend fun setSubtitleLanguage(lang: String) {
        context.playerDataStore.edit { it[Keys.SUBTITLE_LANG] = lang }
    }

    suspend fun setSubtitleSize(sizeSp: Float) {
        context.playerDataStore.edit { it[Keys.SUBTITLE_SIZE] = sizeSp }
    }

    suspend fun setThemeMode(theme: String) {
        context.playerDataStore.edit { it[Keys.THEME_MODE] = theme }
    }
}
