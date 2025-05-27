package com.example.radiostreamingapp.sync.api.models

import java.util.Date

/**
 * Estado de sincronización
 */
sealed class SyncState {
    /** No se ha iniciado ninguna sincronización */
    object Idle : SyncState()

    /** Sincronización en progreso */
    data class Syncing(
        val progress: Float, // 0.0f a 1.0f
        val message: String
    ) : SyncState()

    /** Sincronización completada */
    data class Completed(
        val changedStations: Int,
        val totalStations: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) : SyncState()

    /** Error de sincronización */
    data class Error(
        val message: String,
        val code: Int,
        val isRecoverable: Boolean
    ) : SyncState()
}

/**
 * Estado de descarga de iconos
 */
sealed class IconDownloadState {
    /** Sin descargas activas */
    object Idle : IconDownloadState()

    /** Descarga en progreso */
    data class Downloading(
        val completed: Int,
        val total: Int,
        val currentStation: String? = null
    ) : IconDownloadState()

    /** Descargas completadas */
    data class Completed(
        val successful: Int,
        val failed: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) : IconDownloadState()

    /** Error en descarga */
    data class Error(
        val message: String,
        val failedStation: String? = null
    ) : IconDownloadState()
}

/**
 * Resultado de sincronización
 */
data class SyncResult(
    val success: Boolean,
    val stationsAdded: Int = 0,
    val stationsUpdated: Int = 0,
    val stationsRemoved: Int = 0,
    val iconUpdatesNeeded: Int = 0,
    val totalStations: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    val errorCode: Int = 0
)

/**
 * Resultado de importación
 */
data class ImportResult(
    val success: Boolean,
    val stationCount: Int = 0,
    val iconCount: Int = 0,
    val sourceUrl: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    val errorCode: Int = 0
)

/**
 * Información de última sincronización
 */
data class SyncInfo(
    val lastSyncTime: Date? = null,
    val sourceUrl: String? = null,
    val hasImportedConfig: Boolean = false,
    val stationCount: Int = 0,
    val configVersion: Int = 0,
    val syncCount: Int = 0, // Número de sincronizaciones realizadas
    val cacheSize: Long = 0 // Tamaño del caché en bytes
)

/**
 * Estación remota (modelo para parseo del JSON remoto)
 */
data class RemoteRadioStation(
    val id: Int,
    val name: String,
    val streamUrl: String,
    val iconUrl: String? = null,
    //val iconType: String = "RESOURCE", // RESOURCE, URL, CACHED
    val categoryId: Int? = null,
    val tags: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Configuración remota (modelo para parseo del JSON remoto)
 */
data class RemoteConfig(
    val stations: List<RemoteRadioStation> = emptyList(),
    val version: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
    val name: String? = null,
    val description: String? = null,
    val categories: List<StationCategory> = emptyList()
)

/**
 * Categoría de estaciones
 */
data class StationCategory(
    val id: Int,
    val name: String,
    val description: String? = null,
    val iconUrl: String? = null
)