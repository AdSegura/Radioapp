package com.example.radiostreamingapp.sync.api

import com.example.radiostreamingapp.data.RadioStation
import com.example.radiostreamingapp.sync.api.models.ImportResult
import com.example.radiostreamingapp.sync.api.models.SyncInfo
import com.example.radiostreamingapp.sync.api.models.SyncResult
import com.example.radiostreamingapp.sync.api.models.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interfaz principal para la sincronización de configuración.
 * Actúa como punto de entrada para la funcionalidad de importación y sincronización
 * de datos de emisoras de radio desde una fuente remota.
 */
interface ConfigSync {
    /**
     * Solicita una sincronización desde la URL almacenada
     * @return Resultado de la sincronización
     */
    suspend fun synchronize(): SyncResult

    /**
     * Importa una nueva configuración desde URL (primera vez)
     * @param url URL del snippet o archivo de configuración
     * @return Resultado de la importación
     */
    suspend fun importFromUrl(url: String): ImportResult

    /**
     * Devuelve un Flow con las estaciones actualmente sincronizadas
     * @return Flow de lista de estaciones
     */
    fun getRadioStations(): Flow<List<RadioStation>>

    /**
     * Estado actual del proceso de sincronización
     */
    val syncState: StateFlow<SyncState>

    /**
     * Devuelve información sobre la última sincronización
     * @return Información de sincronización
     */
    fun getLastSyncInfo(): SyncInfo

    /**
     * Verifica si existe una configuración importada
     * @return true si existe una configuración importada, false en caso contrario
     */
    fun hasImportedConfig(): Boolean

    /**
     * Borra la configuración importada y vuelve a la configuración predeterminada
     */
    suspend fun resetToDefaultConfig(): Boolean

    /**
     * Actualiza la URL de origen para sincronización
     * @param url Nueva URL de origen
     */
    suspend fun updateSyncSource(url: String): Boolean

    /**
     * Obtiene la URL de origen actual
     * @return URL de origen actual o null si no hay
     */
    fun getSyncSourceUrl(): String?
}