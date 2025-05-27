package com.example.radiostreamingapp.sync.api

import android.graphics.Bitmap
import com.example.radiostreamingapp.data.RadioStation
import com.example.radiostreamingapp.sync.api.models.IconDownloadState
import kotlinx.coroutines.flow.StateFlow

/**
 * Interfaz para la gestión de iconos de emisoras.
 * Se encarga de descargar, cachear y proporcionar iconos para las estaciones.
 */
interface IconManager {
    /**
     * Obtiene el bitmap para una estación (desde caché o descargando)
     * @param station La estación para la que se requiere el icono
     * @return El bitmap del icono o null si no se puede obtener
     */
    suspend fun getIconBitmap(station: RadioStation): Bitmap?

    /**
     * Precarga iconos en segundo plano
     * @param stations Lista de estaciones cuyos iconos se precargarán
     * @return ID del trabajo de precarga (para seguimiento)
     */
    fun preloadIcons(stations: List<RadioStation>): String

    /**
     * Estado actual de la descarga de iconos
     */
    val downloadState: StateFlow<IconDownloadState>

    /**
     * Limpia el caché de iconos
     * @param olderThanDays Opcional. Si se especifica, solo limpia iconos más antiguos que los días indicados
     * @return Espacio liberado en bytes
     */
    suspend fun clearIconCache(olderThanDays: Int? = null): Long

    /**
     * Obtiene el tamaño actual del caché de iconos
     * @return Tamaño en bytes
     */
    suspend fun getIconCacheSize(): Long

    /**
     * Descarga inmediatamente un icono específico y lo almacena en caché
     * @param station La estación cuyo icono se descargará
     * @return true si se descargó correctamente, false en caso contrario
     */
    suspend fun forceDownloadIcon(station: RadioStation): Boolean

    /**
     * Cancela todas las descargas en progreso
     */
    fun cancelDownloads()

    /**
     * Obtiene la ruta del archivo cacheado para un icono específico
     * @param station La estación
     * @return Ruta del archivo o null si no está cacheado
     */
    fun getIconCachePath(station: RadioStation): String?
}