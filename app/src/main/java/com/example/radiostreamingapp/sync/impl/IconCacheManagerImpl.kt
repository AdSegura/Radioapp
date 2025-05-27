package com.example.radiostreamingapp.sync.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.example.radiostreamingapp.utils.Logger
import com.example.radiostreamingapp.R
import com.example.radiostreamingapp.data.RadioStation
import com.example.radiostreamingapp.sync.api.IconManager
import com.example.radiostreamingapp.sync.api.models.IconDownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de IconManager que maneja la descarga y caché de iconos.
 */
@Singleton
class IconCacheManagerImpl @Inject constructor(
    private val context: Context
) : IconManager {

    companion object {
        private const val TAG = "IconCacheManager"
        private const val CACHE_DIR_NAME = "station_icons"
        private const val DEFAULT_CONCURRENT_DOWNLOADS = 5
        private const val MAX_RETRY_COUNT = 3
        private const val DOWNLOAD_TIMEOUT_MS = 15000
    }

    private val cacheDir: File by lazy {
        val dir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _downloadState = MutableStateFlow<IconDownloadState>(IconDownloadState.Idle)
    override val downloadState: StateFlow<IconDownloadState> = _downloadState

    private val activeDownloads = ConcurrentHashMap<Int, AtomicBoolean>()
    private var isDownloadCancelled = AtomicBoolean(false)

    override suspend fun getIconBitmap(station: RadioStation): Bitmap? {
        // Caso 1: Intentar obtener del caché si existe
        val iconFile = File(cacheDir, "icon_${station.id}.webp")
        if (iconFile.exists() && iconFile.length() > 0) {
            return BitmapFactory.decodeFile(iconFile.absolutePath)
        }

        // Caso 2: Si tiene iconUrl en metadata, intentar descargarlo
        val iconUrl = station.metadata?.get("iconUrl")
        if (!iconUrl.isNullOrBlank()) {
            val downloadedBitmap = downloadAndCacheIcon(station, iconUrl)
            if (downloadedBitmap != null) {
                return downloadedBitmap
            }
        }

        // Caso 3: La estación tiene un iconResource local
        if (station.iconResource != 0 && station.iconResource != R.drawable.ic_radio_default) {
            try {
                val drawable = context.resources.getDrawable(station.iconResource, null)
                if (drawable != null) {
                    return drawableToBitmap(drawable)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Error loading resource bitmap for station ${station.name}", e)
            }
        }

        // Si llegamos aquí, no hay icono disponible
        return null
    }

    override fun preloadIcons(stations: List<RadioStation>): String {
        val jobId = UUID.randomUUID().toString()

        // Filtrar estaciones que tienen iconUrl en metadata
        val stationsWithIcons = stations.filter { station ->
            val iconUrl = station.metadata?.get("iconUrl")

            !iconUrl.isNullOrBlank()
        }

        if (stationsWithIcons.isEmpty()) {
            Logger.d(TAG, "No hay iconos para descargar")
            _downloadState.value = IconDownloadState.Completed(0, 0)
            return jobId
        }

        Logger.d(TAG, "Iniciando descarga de ${stationsWithIcons.size} iconos")
        _downloadState.value = IconDownloadState.Downloading(0, stationsWithIcons.size)

        scope.launch {
            downloadIconsInBatches(stationsWithIcons)
        }

        return jobId
    }

    private suspend fun downloadIconsInBatches(stations: List<RadioStation>) = withContext(Dispatchers.IO) {
        var completedCount = 0
        var successCount = 0
        var failedCount = 0

        for (station in stations) {
            if (isDownloadCancelled.get()) {
                Logger.d(TAG, "Descarga cancelada por el usuario")
                break
            }

            try {
                val iconUrl = station.metadata?.get("iconUrl")
                if (!iconUrl.isNullOrBlank()) {

                    // Actualizar estado de progreso
                    _downloadState.value = IconDownloadState.Downloading(
                        completed = completedCount,
                        total = stations.size,
                        currentStation = station.name
                    )

                    val result = downloadAndCacheIcon(station, iconUrl)
                    if (result != null) {
                        successCount++
                        Logger.d(TAG, "Icono descargado exitosamente para ${station.name}")
                    } else {
                        failedCount++
                        Logger.w(TAG, "Falló la descarga de icono para ${station.name}")
                    }
                }
            } catch (e: Exception) {
                failedCount++
                Logger.e(TAG, "Error descargando icono para ${station.name}", e)
            }

            completedCount++
        }

        // Estado final
        _downloadState.value = IconDownloadState.Completed(successCount, failedCount)
        Logger.i(TAG, "Descarga completada: $successCount exitosos, $failedCount fallidos")
    }

    /**
     * Convierte un Drawable a Bitmap
     */
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            if (drawable.bitmap != null) {
                return drawable.bitmap
            }
        }

        val bitmap = Bitmap.createBitmap(
            if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 200,
            if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 200,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    override suspend fun clearIconCache(olderThanDays: Int?): Long {
        return withContext(Dispatchers.IO) {
            var bytesRemoved = 0L

            val cutoffTime = if (olderThanDays != null) {
                System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
            } else {
                Long.MAX_VALUE
            }

            val files = cacheDir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isFile && file.lastModified() < cutoffTime) {
                        bytesRemoved += file.length()
                        file.delete()
                        Logger.d(TAG, "Eliminado archivo de caché: ${file.name}")
                    }
                }
            }

            Logger.d(TAG, "Caché limpiada: $bytesRemoved bytes eliminados")
            bytesRemoved
        }
    }

    override suspend fun getIconCacheSize(): Long {
        return withContext(Dispatchers.IO) {
            var size = 0L
            val files = cacheDir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isFile) {
                        size += file.length()
                    }
                }
            }
            size
        }
    }

    override suspend fun forceDownloadIcon(station: RadioStation): Boolean {
        val iconUrl = station.metadata?.get("iconUrl")
        if (iconUrl.isNullOrBlank()) {
            return false
        }

        val result = downloadAndCacheIcon(station, iconUrl)
        return result != null
    }

    override fun cancelDownloads() {
        isDownloadCancelled.set(true)
        activeDownloads.forEach { (_, cancellationFlag) ->
            cancellationFlag.set(true)
        }
        activeDownloads.clear()
        _downloadState.value = IconDownloadState.Idle
        Logger.d(TAG, "Descargas canceladas")
    }

    override fun getIconCachePath(station: RadioStation): String? {
        val iconFile = File(cacheDir, "icon_${station.id}.webp")
        return if (iconFile.exists()) iconFile.absolutePath else null
    }

    /**
     * Descarga y cachea un icono desde una URL
     */
    private suspend fun downloadAndCacheIcon(
        station: RadioStation,
        iconUrl: String,
        retryCount: Int = 0
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cancellationFlag = AtomicBoolean(false)
        activeDownloads[station.id] = cancellationFlag

        try {
            // Generar nombre de archivo basado en la URL
            val urlHash = hashString(iconUrl)
            val iconFile = File(cacheDir, "icon_${station.id}_$urlHash.webp")

            // Si ya existe en caché, devolverlo
            if (iconFile.exists() && iconFile.length() > 0) {
                Logger.d(TAG, "Icono encontrado en caché para ${station.name}")
                return@withContext BitmapFactory.decodeFile(iconFile.absolutePath)
            }

            Logger.d(TAG, "Descargando icono desde: $iconUrl")

            // Descargar el icono
            val url = URL(iconUrl)
            val connection = url.openConnection()
            connection.connectTimeout = DOWNLOAD_TIMEOUT_MS
            connection.readTimeout = DOWNLOAD_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "RadioStreamingApp/1.0")

            // Leer el bitmap
            val bitmap = connection.getInputStream().use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            } ?: throw Exception("No se pudo decodificar la imagen desde $iconUrl")

            // Guardar como WebP para optimizar espacio
            FileOutputStream(iconFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.WEBP, 90, fos)
                fos.flush()
            }

            Logger.d(TAG, "Icono guardado exitosamente para ${station.name}: ${iconFile.length()} bytes")
            bitmap

        } catch (e: Exception) {
            Logger.e(TAG, "Error descargando icono para ${station.name} desde $iconUrl", e)

            // Reintento con back-off exponencial
            if (retryCount < MAX_RETRY_COUNT && !cancellationFlag.get()) {
                val backoffTime = (Math.pow(2.0, retryCount.toDouble()) * 1000).toLong()
                kotlinx.coroutines.delay(backoffTime)
                downloadAndCacheIcon(station, iconUrl, retryCount + 1)
            } else {
                null
            }
        } finally {
            activeDownloads.remove(station.id)
        }
    }

    /**
     * Genera un hash de una cadena
     */
    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        val result = StringBuilder()
        for (i in 0 until 4) {
            result.append(String.format("%02x", bytes[i]))
        }
        return result.toString()
    }
}