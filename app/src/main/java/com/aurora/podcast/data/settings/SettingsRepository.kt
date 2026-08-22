package com.aurora.podcast.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "podcast_settings")

data class AppSettings(
    val keepEpisodes: Int = 10,          // 保留最近 N 期（默认 10）
    val wifiOnly: Boolean = true,        // 仅 Wi-Fi 下载
    val lastCleanupMillis: Long = 0L     // 上次清理时间
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val KEEP_EPISODES = intPreferencesKey("keep_episodes")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val LAST_CLEANUP = longPreferencesKey("last_cleanup_millis")
        val LAST_PLAYED_GUID = stringPreferencesKey("last_played_guid")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            keepEpisodes = prefs[Keys.KEEP_EPISODES] ?: 10,
            wifiOnly = prefs[Keys.WIFI_ONLY] ?: true,
            lastCleanupMillis = prefs[Keys.LAST_CLEANUP] ?: 0L
        )
    }

    suspend fun current(): AppSettings = context.settingsDataStore.data.first().let { prefs ->
        AppSettings(
            keepEpisodes = prefs[Keys.KEEP_EPISODES] ?: 10,
            wifiOnly = prefs[Keys.WIFI_ONLY] ?: true,
            lastCleanupMillis = prefs[Keys.LAST_CLEANUP] ?: 0L
        )
    }

    suspend fun setKeepEpisodes(value: Int) {
        context.settingsDataStore.edit { it[Keys.KEEP_EPISODES] = value.coerceIn(1, 100) }
    }

    suspend fun setWifiOnly(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.WIFI_ONLY] = value }
    }

    suspend fun setLastCleanup(millis: Long) {
        context.settingsDataStore.edit { it[Keys.LAST_CLEANUP] = millis }
    }

    suspend fun setLastPlayedGuid(guid: String?) {
        context.settingsDataStore.edit { prefs ->
            if (guid == null) prefs.remove(Keys.LAST_PLAYED_GUID) else prefs[Keys.LAST_PLAYED_GUID] = guid
        }
    }

    suspend fun lastPlayedGuid(): String? =
        context.settingsDataStore.data.first()[Keys.LAST_PLAYED_GUID]
}