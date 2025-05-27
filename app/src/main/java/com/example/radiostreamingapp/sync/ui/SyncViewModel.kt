package com.example.radiostreamingapp.sync.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.radiostreamingapp.sync.api.ConfigSync
import com.example.radiostreamingapp.sync.api.IconManager
import com.example.radiostreamingapp.sync.api.models.IconDownloadState
import com.example.radiostreamingapp.sync.api.models.ImportResult
import com.example.radiostreamingapp.sync.api.models.SyncInfo
import com.example.radiostreamingapp.sync.api.models.SyncResult
import com.example.radiostreamingapp.sync.api.models.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para gestionar la sincronización de emisoras.
 */
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val configSync: ConfigSync,
    private val iconManager: IconManager
) : ViewModel() {

    val syncState: StateFlow<SyncState> = configSync.syncState
    val downloadState: StateFlow<IconDownloadState> = iconManager.downloadState

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult

    private val _syncResult = MutableStateFlow<SyncResult?>(null)
    val syncResult: StateFlow<SyncResult?> = _syncResult

    private val _syncInfo = MutableStateFlow<SyncInfo?>(null)
    val syncInfo: StateFlow<SyncInfo?> = _syncInfo

    private val _iconCacheSize = MutableStateFlow<Long>(0)
    val iconCacheSize: StateFlow<Long> = _iconCacheSize

    init {
        // Cargar información de sincronización
        refreshSyncInfo()

        // Iniciar observación de estados
        viewModelScope.launch {
            syncState.collect {
                if (it is SyncState.Completed) {
                    refreshSyncInfo()
                }
            }
        }

        // Cargar tamaño de caché inicial
        refreshIconCacheSize()
    }

    fun clearImportResult() {
        _importResult.value = null
    }

    /**
     * Importa una configuración desde URL
     */
    // Método modificado (con parámetro onSuccess)
    fun importFromUrl(url: String, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            val result = configSync.importFromUrl(url)
            _importResult.value = result

            if (result.success) {
                refreshSyncInfo()

                // Precargar iconos si hay
                configSync.getRadioStations().first().let { stations ->
                    iconManager.preloadIcons(stations)
                }

                // Llamar callback de éxito
                onSuccess?.invoke()
            }
        }
    }

    /**
     * Sincroniza con la URL configurada
     */
    fun synchronize(onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            val result = configSync.synchronize()
            _syncResult.value = result

            if (result.success) {
                refreshSyncInfo()

                // Precargar iconos actualizados
                configSync.getRadioStations().first().let { stations ->
                    iconManager.preloadIcons(stations)
                }

                // Llamar callback de éxito
                onSuccess?.invoke()
            }
        }
    }

    /**
     * Actualiza la URL de origen
     */
    fun updateSourceUrl(url: String) {
        viewModelScope.launch {
            configSync.updateSyncSource(url)
            refreshSyncInfo()
        }
    }

    /**
     * Reinicia a la configuración por defecto
     */
    fun resetToDefaultConfig() {
        viewModelScope.launch {
            configSync.resetToDefaultConfig()
            _importResult.value = null
            _syncResult.value = null
            refreshSyncInfo()
        }
    }

    /**
     * Limpia la caché de iconos
     */
    fun clearIconCache(olderThanDays: Int? = null) {
        viewModelScope.launch {
            val bytesRemoved = iconManager.clearIconCache(olderThanDays)
            refreshIconCacheSize()
        }
    }

    /**
     * Cancela descargas en progreso
     */
    fun cancelDownloads() {
        iconManager.cancelDownloads()
    }

    /**
     * Actualiza la información de sincronización
     */
    fun refreshSyncInfo() {
        _syncInfo.value = configSync.getLastSyncInfo()
    }

    /**
     * Actualiza el tamaño de la caché de iconos
     */
    fun refreshIconCacheSize() {
        viewModelScope.launch {
            _iconCacheSize.value = iconManager.getIconCacheSize()
        }
    }
}