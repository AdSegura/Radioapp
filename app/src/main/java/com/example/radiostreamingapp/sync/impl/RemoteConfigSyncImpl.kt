package com.example.radiostreamingapp.sync.impl

import android.content.Context
import com.example.radiostreamingapp.R
import com.example.radiostreamingapp.utils.Logger
import com.example.radiostreamingapp.data.RadioStation
import com.example.radiostreamingapp.sync.api.ConfigSync
import com.example.radiostreamingapp.sync.api.models.ImportResult
import com.example.radiostreamingapp.sync.api.models.RemoteConfig
import com.example.radiostreamingapp.sync.api.models.RemoteRadioStation
import com.example.radiostreamingapp.sync.api.models.SyncInfo
import com.example.radiostreamingapp.sync.api.models.SyncResult
import com.example.radiostreamingapp.sync.api.models.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación simplificada de ConfigSync que maneja únicamente JSON remoto desde GIST.
 * Mantiene el nombre original pero con lógica mucho más simple y clara.
 */
@Singleton
class RemoteConfigSyncImpl @Inject constructor(
    private val context: Context
) : ConfigSync {

    companion object {
        private const val TAG = "RemoteConfigSync"
        private const val TIMEOUT_MS = 30000
        private const val MAX_RETRIES = 3
    }

    private val preferences = SyncPreferences(context)
    private val gitSnippetParser = GitSnippetParser()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val syncState: StateFlow<SyncState> = _syncState

    override suspend fun synchronize(): SyncResult = withContext(Dispatchers.IO) {
        try {
            Logger.i(TAG, "Iniciando sincronización")

            val sourceUrl = preferences.getSourceUrl()
            if (sourceUrl.isNullOrBlank()) {
                return@withContext SyncResult(
                    success = false,
                    errorMessage = "No hay URL de origen configurada"
                )
            }

            _syncState.value = SyncState.Syncing(0.2f, "Descargando configuración...")

            // Descargar contenido desde GIST
            val content = downloadFromUrl(sourceUrl)
            if (content.isNullOrBlank()) {
                _syncState.value = SyncState.Error(
                    "No se pudo descargar la configuración",
                    1001,
                    true
                )
                return@withContext SyncResult(
                    success = false,
                    errorMessage = "Error al descargar desde $sourceUrl"
                )
            }

            _syncState.value = SyncState.Syncing(0.5f, "Procesando configuración...")

            // Parsear JSON
            val newConfig = gitSnippetParser.parseSnippet(content)
            if (newConfig == null) {
                _syncState.value = SyncState.Error(
                    "No se pudo procesar la configuración JSON",
                    1002,
                    true
                )
                return@withContext SyncResult(
                    success = false,
                    errorMessage = "Error al parsear JSON"
                )
            }

            // Validar configuración
            val validation = validateConfig(newConfig)
            if (!validation.isValid) {
                _syncState.value = SyncState.Error(
                    validation.errorMessage,
                    1003,
                    true
                )
                return@withContext SyncResult(
                    success = false,
                    errorMessage = validation.errorMessage
                )
            }

            _syncState.value = SyncState.Syncing(0.8f, "Guardando configuración...")

            // Comparar con configuración actual
            val currentConfig = preferences.getRemoteConfig()
            val changes = compareConfigs(currentConfig, newConfig)

            // Guardar nueva configuración
            preferences.saveRemoteConfig(newConfig)
            preferences.recordSuccessfulSync(
                timestamp = System.currentTimeMillis(),
                stationCount = newConfig.stations.size,
                configVersion = newConfig.version
            )

            val result = SyncResult(
                success = true,
                stationsAdded = changes.added,
                stationsUpdated = changes.updated,
                stationsRemoved = changes.removed,
                totalStations = newConfig.stations.size,
                timestamp = System.currentTimeMillis()
            )

            _syncState.value = SyncState.Completed(
                changedStations = changes.added + changes.updated + changes.removed,
                totalStations = newConfig.stations.size
            )

            Logger.i(TAG, "Sincronización exitosa: ${result.totalStations} estaciones")
            result

        } catch (e: Exception) {
            Logger.e(TAG, "Error en sincronización", e)
            _syncState.value = SyncState.Error(
                "Error: ${e.message}",
                1000,
                true
            )

            SyncResult(
                success = false,
                errorMessage = e.message ?: "Error desconocido"
            )
        }
    }

    override suspend fun importFromUrl(url: String): ImportResult = withContext(Dispatchers.IO) {
        try {
            Logger.i(TAG, "Importando desde URL: $url")

            if (!isValidUrl(url)) {
                return@withContext ImportResult(
                    success = false,
                    errorMessage = "URL inválida: $url"
                )
            }

            _syncState.value = SyncState.Syncing(0.2f, "Descargando desde URL...")

            // Descargar contenido
            val content = downloadFromUrl(url)
            if (content.isNullOrBlank()) {
                _syncState.value = SyncState.Error(
                    "No se pudo descargar desde la URL",
                    1001,
                    true
                )
                return@withContext ImportResult(
                    success = false,
                    errorMessage = "Error al descargar desde $url"
                )
            }

            _syncState.value = SyncState.Syncing(0.5f, "Procesando JSON...")

            // Parsear JSON
            val config = gitSnippetParser.parseSnippet(content)
            if (config == null) {
                _syncState.value = SyncState.Error(
                    "No se pudo procesar el JSON",
                    1002,
                    true
                )
                return@withContext ImportResult(
                    success = false,
                    errorMessage = "Error al parsear JSON"
                )
            }

            // Validar
            val validation = validateConfig(config)
            if (!validation.isValid) {
                _syncState.value = SyncState.Error(
                    validation.errorMessage,
                    1003,
                    true
                )
                return@withContext ImportResult(
                    success = false,
                    errorMessage = validation.errorMessage
                )
            }

            _syncState.value = SyncState.Syncing(0.8f, "Guardando configuración...")

            // Guardar URL y configuración
            preferences.saveSourceUrl(url)
            preferences.saveRemoteConfig(config)
            preferences.recordSuccessfulSync(
                timestamp = System.currentTimeMillis(),
                stationCount = config.stations.size,
                configVersion = config.version
            )

            _syncState.value = SyncState.Completed(
                changedStations = config.stations.size,
                totalStations = config.stations.size
            )

            Logger.i(TAG, "Importación exitosa: ${config.stations.size} estaciones")

            ImportResult(
                success = true,
                stationCount = config.stations.size,
                iconCount = config.stations.count { !it.iconUrl.isNullOrBlank() },
                sourceUrl = url,
                timestamp = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            Logger.e(TAG, "Error en importación", e)
            _syncState.value = SyncState.Error(
                "Error: ${e.message}",
                1000,
                true
            )

            ImportResult(
                success = false,
                errorMessage = e.message ?: "Error desconocido"
            )
        }
    }

    override fun getRadioStations(): Flow<List<RadioStation>> = flow {
        Logger.d(TAG, "Obteniendo estaciones de radio")

        if (!preferences.hasImportedConfig()) {
            Logger.d(TAG, "No hay configuración importada, emitiendo lista vacía")
            emit(emptyList())
            return@flow
        }

        val remoteConfig = preferences.getRemoteConfig()
        if (remoteConfig == null) {
            Logger.w(TAG, "Configuración importada pero no se pudo leer")
            emit(emptyList())
            return@flow
        }

        Logger.d(TAG, "Convirtiendo ${remoteConfig.stations.size} estaciones remotas")
        val stations = convertToRadioStationsSafely(remoteConfig.stations)
        Logger.d(TAG, "Emitiendo ${stations.size} estaciones")
        emit(stations)

    }.catch { e ->
        Logger.e(TAG, "Error al obtener estaciones", e)
        emit(emptyList())
    }

    override fun getLastSyncInfo(): SyncInfo {
        return try {
            preferences.getSyncInfo()
        } catch (e: Exception) {
            Logger.e(TAG, "Error al obtener info de sync", e)
            SyncInfo()
        }
    }

    override fun hasImportedConfig(): Boolean {
        return try {
            preferences.hasImportedConfig()
        } catch (e: Exception) {
            Logger.e(TAG, "Error al verificar config importada", e)
            false
        }
    }

    override suspend fun resetToDefaultConfig(): Boolean = withContext(Dispatchers.IO) {
        try {
            preferences.resetAllPrefs()
            _syncState.value = SyncState.Idle
            Logger.i(TAG, "Configuración reseteada")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Error al resetear", e)
            false
        }
    }

    override suspend fun updateSyncSource(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isValidUrl(url)) {
                Logger.w(TAG, "URL inválida: $url")
                return@withContext false
            }
            preferences.saveSourceUrl(url)
            Logger.i(TAG, "URL de origen actualizada: $url")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Error al actualizar URL", e)
            false
        }
    }

    override fun getSyncSourceUrl(): String? {
        return try {
            preferences.getSourceUrl()
        } catch (e: Exception) {
            Logger.e(TAG, "Error al obtener URL", e)
            null
        }
    }

    /**
     * Descarga contenido desde una URL con reintentos
     */
    private suspend fun downloadFromUrl(url: String): String? = withContext(Dispatchers.IO) {
        repeat(MAX_RETRIES) { attempt ->
            try {
                Logger.d(TAG, "Descarga intento ${attempt + 1}: $url")

                val connection = URL(url).openConnection()
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("User-Agent", "RadioStreamingApp/1.0")

                val content = connection.getInputStream().use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                }

                if (content.isNotBlank()) {
                    Logger.d(TAG, "Descarga exitosa (${content.length} caracteres)")
                    return@withContext content
                }

            } catch (e: Exception) {
                Logger.w(TAG, "Error intento ${attempt + 1}: ${e.message}")
                if (attempt < MAX_RETRIES - 1) {
                    // Esperar antes del siguiente intento (backoff exponencial)
                    val delayTime = (1000 * Math.pow(2.0, attempt.toDouble())).toLong()
                    kotlinx.coroutines.delay(delayTime)
                }
            }
        }

        Logger.e(TAG, "Falló descarga después de $MAX_RETRIES intentos")
        null
    }

    /**
     * Valida que la configuración sea correcta
     */
    private fun validateConfig(config: RemoteConfig): ValidationResult {
        return try {
            if (config.stations.isEmpty()) {
                return ValidationResult(false, "No hay estaciones de radio en la configuración")
            }

            var validStations = 0
            val errors = mutableListOf<String>()

            config.stations.forEachIndexed { index, station ->
                val stationName = station.name?.takeIf { it.isNotBlank() } ?: "Estación ${index + 1}"

                when {
                    station.name.isNullOrBlank() -> {
                        errors.add("$stationName: Falta el nombre")
                    }
                    station.streamUrl.isNullOrBlank() -> {
                        errors.add("$stationName: Falta la URL de stream")
                    }
                    !isValidStreamUrl(station.streamUrl) -> {
                        errors.add("$stationName: URL de stream inválida")
                    }
                    else -> {
                        validStations++
                    }
                }
            }

            if (validStations == 0) {
                return ValidationResult(false, "No se encontraron estaciones válidas")
            }

            Logger.d(TAG, "Validación: $validStations estaciones válidas de ${config.stations.size}")
            ValidationResult(true, "Configuración válida con $validStations estaciones")

        } catch (e: Exception) {
            Logger.e(TAG, "Error al validar", e)
            ValidationResult(false, "Error al procesar la configuración")
        }
    }

    /**
     * Convierte estaciones remotas a objetos RadioStation de forma segura
     */
    private fun convertToRadioStationsSafely(remoteStations: List<RemoteRadioStation>): List<RadioStation> {
        val result = mutableListOf<RadioStation>()

        remoteStations.forEachIndexed { index, remote ->
            try {
                Logger.d(TAG, "Procesando estación $index: ID=${remote.id}, Nombre=${remote.name}")

                // Validar datos básicos
                if (remote.name.isNullOrBlank() || remote.streamUrl.isNullOrBlank()) {
                    Logger.w(TAG, "Omitiendo estación ${index + 1}: datos incompletos")
                    return@forEachIndexed
                }

                // Determinar icono

                val iconResource = if(!remote.iconUrl.isNullOrBlank()){
                    getResourceId(remote.iconUrl) ?: R.drawable.ic_radio_default
                } else {
                    R.drawable.ic_radio_default
                }

               /* val iconResource = when {
                    remote.iconType == "RESOURCE" && !remote.iconUrl.isNullOrBlank() -> {
                        getResourceId(remote.iconUrl) ?: R.drawable.ic_radio_default
                    }
                    else -> R.drawable.ic_radio_default
                }*/

                // Ruta de caché para iconos URL
                val iconCachePath = getCacheIconPath(remote.id)

                // Crear metadata
                val metadata = buildMap<String, String> {
                    remote.iconUrl?.let { put("iconUrl", it) }
                    remote.categoryId?.let { put("categoryId", it.toString()) }
                    if (!remote.tags.isNullOrEmpty()) {
                        put("tags", remote.tags.joinToString(","))
                    }
                    remote.metadata.let { putAll(it) }
                }

                val station = RadioStation(
                    id = remote.id,
                    name = remote.name.trim(),
                    streamUrl = remote.streamUrl.trim(),
                    iconResource = iconResource,
                    iconCachePath = iconCachePath,
                    metadata = metadata
                )

                result.add(station)
                Logger.d(TAG, "Convertida estación: ${station.name}")

            } catch (e: Exception) {
                Logger.e(TAG, "Error convirtiendo estación ${index + 1}", e)
            }
        }

        Logger.d(TAG, "Conversión completada: ${result.size} estaciones")
        return result
    }

    /**
     * Compara configuraciones para detectar cambios
     */
    private fun compareConfigs(current: RemoteConfig?, new: RemoteConfig): ConfigChanges {
        return if (current == null) {
            ConfigChanges(new.stations.size, 0, 0)
        } else {
            val currentIds = current.stations.map { it.id }.toSet()
            val newIds = new.stations.map { it.id }.toSet()

            val added = (newIds - currentIds).size
            val removed = (currentIds - newIds).size
            val updated = newIds.intersect(currentIds).count { id ->
                val currentStation = current.stations.find { it.id == id }
                val newStation = new.stations.find { it.id == id }
                currentStation != newStation
            }

            ConfigChanges(added, updated, removed)
        }
    }

    /**
     * Obtiene ID de recurso drawable por nombre
     */
    private fun getResourceId(resourceName: String): Int? {
        return try {
            val name = resourceName.substringBeforeLast(".")
            val id = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (id != 0) id else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Obtiene ruta de caché para icono de estación
     */
    private fun getCacheIconPath(stationId: Int): String? {
        return try {
            val cacheDir = File(context.cacheDir, "station_icons")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val iconFile = File(cacheDir, "icon_$stationId.webp")
            if (iconFile.exists()) iconFile.absolutePath else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Valida que una URL sea correcta
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            URL(url)
            url.startsWith("http://") || url.startsWith("https://")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Valida que una URL de stream sea correcta
     */
    private fun isValidStreamUrl(streamUrl: String): Boolean {
        return try {
            if (streamUrl.isBlank()) return false
            val url = URL(streamUrl)
            url.protocol.lowercase() in listOf("http", "https", "rtmp", "rtsp", "mms")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clases auxiliares para resultados
     */
    private data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String
    )

    private data class ConfigChanges(
        val added: Int,
        val updated: Int,
        val removed: Int
    )
}