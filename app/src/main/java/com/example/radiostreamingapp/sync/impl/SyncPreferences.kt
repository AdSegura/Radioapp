package com.example.radiostreamingapp.sync.impl

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.radiostreamingapp.sync.api.models.RemoteConfig
import com.example.radiostreamingapp.sync.api.models.SyncInfo
import com.google.gson.Gson
import java.util.Date

/**
 * Gestiona las preferencias relacionadas con la sincronización de la configuración.
 * Almacena URL de origen, estado de sincronización, hashes para comparación, etc.
 */
class SyncPreferences(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "radio_sync_prefs"
        private const val KEY_SOURCE_URL = "source_url"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_HAS_IMPORTED_CONFIG = "has_imported_config"
        private const val KEY_CONFIG_VERSION = "config_version"
        private const val KEY_SYNC_COUNT = "sync_count"
        private const val KEY_CACHED_CONFIG = "cached_config"
        private const val KEY_STATION_COUNT = "station_count"
        private const val KEY_STATION_HASH_PREFIX = "station_hash_" // + stationId
        private const val KEY_ICON_HASH_PREFIX = "icon_hash_" // + stationId
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val gson = Gson()

    /**
     * Guarda la URL de origen para sincronización
     */
    fun saveSourceUrl(url: String) {
        prefs.edit {
            putString(KEY_SOURCE_URL, url)
        }
    }

    /**
     * Obtiene la URL de origen para sincronización
     */
    fun getSourceUrl(): String? {
        return prefs.getString(KEY_SOURCE_URL, null)
    }

    /**
     * Registra una sincronización exitosa
     */
    fun recordSuccessfulSync(timestamp: Long, stationCount: Int, configVersion: Int) {
        prefs.edit {
            putLong(KEY_LAST_SYNC_TIME, timestamp)
            putBoolean(KEY_HAS_IMPORTED_CONFIG, true)
            putInt(KEY_STATION_COUNT, stationCount)
            putInt(KEY_CONFIG_VERSION, configVersion)
            putInt(KEY_SYNC_COUNT, getSyncCount() + 1)
        }
    }

    /**
     * Obtiene información sobre la última sincronización
     */
    fun getSyncInfo(): SyncInfo {
        val lastSyncTime = prefs.getLong(KEY_LAST_SYNC_TIME, 0)

        return SyncInfo(
            lastSyncTime = if (lastSyncTime > 0) Date(lastSyncTime) else null,
            sourceUrl = getSourceUrl(),
            hasImportedConfig = prefs.getBoolean(KEY_HAS_IMPORTED_CONFIG, false),
            stationCount = prefs.getInt(KEY_STATION_COUNT, 0),
            configVersion = prefs.getInt(KEY_CONFIG_VERSION, 0),
            syncCount = getSyncCount(),
            cacheSize = 0 // Se actualizará dinámicamente
        )
    }

    /**
     * Verifica si existe una configuración importada
     */
    fun hasImportedConfig(): Boolean {
        return prefs.getBoolean(KEY_HAS_IMPORTED_CONFIG, false)
    }

    /**
     * Obtiene el número de sincronizaciones realizadas
     */
    fun getSyncCount(): Int {
        return prefs.getInt(KEY_SYNC_COUNT, 0)
    }

    /**
     * Guarda la configuración remota cacheada
     */
    fun saveRemoteConfig(config: RemoteConfig) {
        val json = gson.toJson(config)
        prefs.edit {
            putString(KEY_CACHED_CONFIG, json)
        }
    }

    /**
     * Obtiene la configuración remota cacheada
     */
    fun getRemoteConfig(): RemoteConfig? {
        val json = prefs.getString(KEY_CACHED_CONFIG, null) ?: return null
        return try {
            gson.fromJson(json, RemoteConfig::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Guarda el hash de contenido de una estación para comparación futura
     */
    fun saveStationHash(stationId: Int, hash: String) {
        prefs.edit {
            putString("${KEY_STATION_HASH_PREFIX}$stationId", hash)
        }
    }

    /**
     * Obtiene el hash de contenido de una estación
     */
    fun getStationHash(stationId: Int): String? {
        return prefs.getString("${KEY_STATION_HASH_PREFIX}$stationId", null)
    }

    /**
     * Guarda el hash del icono de una estación para comparación futura
     */
    fun saveIconHash(stationId: Int, hash: String) {
        prefs.edit {
            putString("${KEY_ICON_HASH_PREFIX}$stationId", hash)
        }
    }

    /**
     * Obtiene el hash del icono de una estación
     */
    fun getIconHash(stationId: Int): String? {
        return prefs.getString("${KEY_ICON_HASH_PREFIX}$stationId", null)
    }

    /**
     * Reinicia todas las preferencias de sincronización
     */
    fun resetAllPrefs() {
        prefs.edit { clear() }
    }
}