package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.monoDataStore by preferencesDataStore(name = "mono_docs_settings")

enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class ConversionQuality(val label: String) { HIGH("High (Lossless / Max)"), BALANCED("Balanced"), COMPACT("Compact") }
enum class VideoQualityPreset(val label: String, val maxHeight: Int, val bitrate: Int) {
    ORIGINAL("Original", 0, 0),
    HD_1080("1080p HD", 1080, 6_000_000),
    HD_720("720p HD", 720, 3_000_000),
    SD_480("480p Compact", 480, 1_400_000),
}

data class MonoSettingsState(
    val darkModeEnabled: Boolean = true,
    val systemThemeEnabled: Boolean = false,
    val defaultSaveTreeUri: String? = null,
    val defaultSaveLabel: String = "Internal / Documents / MonoDocs",
    val conversionQuality: ConversionQuality = ConversionQuality.HIGH,
    val videoQuality: VideoQualityPreset = VideoQualityPreset.HD_720,
    val imageCompressionPercent: Int = 85,
    val autoDetectFileFormat: Boolean = true,
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val SYSTEM_THEME = booleanPreferencesKey("system_theme")
        val SAVE_TREE_URI = stringPreferencesKey("save_tree_uri")
        val SAVE_LABEL = stringPreferencesKey("save_label")
        val CONVERSION_QUALITY = stringPreferencesKey("conversion_quality")
        val VIDEO_QUALITY = stringPreferencesKey("video_quality")
        val IMAGE_COMPRESSION = intPreferencesKey("image_compression")
        val AUTO_DETECT = booleanPreferencesKey("auto_detect")
    }

    val settingsFlow: Flow<MonoSettingsState> = context.monoDataStore.data.map { prefs ->
        MonoSettingsState(
            darkModeEnabled = prefs[Keys.DARK_MODE] ?: true,
            systemThemeEnabled = prefs[Keys.SYSTEM_THEME] ?: false,
            defaultSaveTreeUri = prefs[Keys.SAVE_TREE_URI],
            defaultSaveLabel = prefs[Keys.SAVE_LABEL] ?: "Internal / Documents / MonoDocs",
            conversionQuality = runCatching {
                ConversionQuality.valueOf(prefs[Keys.CONVERSION_QUALITY] ?: ConversionQuality.HIGH.name)
            }.getOrDefault(ConversionQuality.HIGH),
            videoQuality = runCatching {
                VideoQualityPreset.valueOf(prefs[Keys.VIDEO_QUALITY] ?: VideoQualityPreset.HD_720.name)
            }.getOrDefault(VideoQualityPreset.HD_720),
            imageCompressionPercent = prefs[Keys.IMAGE_COMPRESSION] ?: 85,
            autoDetectFileFormat = prefs[Keys.AUTO_DETECT] ?: true,
        )
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.monoDataStore.edit {
            it[Keys.DARK_MODE] = enabled
            if (enabled) it[Keys.SYSTEM_THEME] = false
        }
    }

    suspend fun setSystemTheme(enabled: Boolean) {
        context.monoDataStore.edit { it[Keys.SYSTEM_THEME] = enabled }
    }

    suspend fun setDefaultSaveLocation(treeUri: String?, label: String) {
        context.monoDataStore.edit {
            if (treeUri == null) it.remove(Keys.SAVE_TREE_URI) else it[Keys.SAVE_TREE_URI] = treeUri
            it[Keys.SAVE_LABEL] = label
        }
    }

    suspend fun setConversionQuality(quality: ConversionQuality) {
        context.monoDataStore.edit { it[Keys.CONVERSION_QUALITY] = quality.name }
    }

    suspend fun setVideoQuality(preset: VideoQualityPreset) {
        context.monoDataStore.edit { it[Keys.VIDEO_QUALITY] = preset.name }
    }

    suspend fun setImageCompression(percent: Int) {
        context.monoDataStore.edit { it[Keys.IMAGE_COMPRESSION] = percent.coerceIn(20, 100) }
    }

    suspend fun setAutoDetect(enabled: Boolean) {
        context.monoDataStore.edit { it[Keys.AUTO_DETECT] = enabled }
    }
}
