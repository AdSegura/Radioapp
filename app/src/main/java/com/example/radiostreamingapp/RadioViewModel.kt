package com.example.radiostreamingapp

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.radiostreamingapp.data.RadioStation
import com.example.radiostreamingapp.sync.impl.IconCacheManagerImpl
import com.example.radiostreamingapp.sync.impl.RemoteConfigSyncImpl
import com.example.radiostreamingapp.utils.Logger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


// RadioViewModel actualizado sin la referencia a 'widget'
@UnstableApi
class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val gson = Gson()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering

    private val _radioStations = MutableStateFlow<List<RadioStation>>(emptyList())
    val radioStations: StateFlow<List<RadioStation>> = _radioStations

    private val _currentStation = MutableStateFlow<RadioStation?>(null)
    val currentStation: StateFlow<RadioStation?> = _currentStation

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorState = MutableStateFlow<PlayerError?>(null)
    val errorState: StateFlow<PlayerError?> = _errorState



    // Método para limpiar errores
    fun clearError() {
        _errorState.value = null
    }

    // Media service connection
    private var mediaServiceBinder: MediaPlaybackService.MediaServiceBinder? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        @OptIn(UnstableApi::class)
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaPlaybackService.MediaServiceBinder
            mediaServiceBinder = binder
            serviceBound = true

            // Synchronize state with service
            viewModelScope.launch {
                binder.getService().currentStation.collect { station ->
                    _currentStation.value = station
                }
            }

            viewModelScope.launch {
                binder.getService().isPlaying.collect { playing ->
                    _isPlaying.value = playing
                }
            }

            viewModelScope.launch {
                binder.getService().isBuffering.collect { buffering ->
                    _isBuffering.value = buffering
                }
            }

            viewModelScope.launch {
                binder.getService().isPlaying.collect { playing ->
                    Logger.d("RadioViewModel", "Service isPlaying changed: $playing")
                    _isPlaying.value = playing
                }
            }

            viewModelScope.launch {
                binder.getService().isBuffering.collect { buffering ->
                    Logger.d("RadioViewModel", "Service isBuffering changed: $buffering")
                    _isBuffering.value = buffering
                }
            }

            // Listen for errors
            viewModelScope.launch {
                binder.getService().playerError.collect { errorMessage ->
                    if (errorMessage != null) {
                        val station = _currentStation.value
                        val errorType = binder.getService().errorType.value

                        if (station != null) {
                            _errorState.value = when (errorType) {
                                MediaPlaybackService.ErrorType.NETWORK ->
                                    PlayerError.ConnectionError(station)
                                MediaPlaybackService.ErrorType.FORMAT ->
                                    PlayerError.FormatError(station)
                                else ->
                                    PlayerError.StreamError(station, errorMessage)
                            }
                        } else {
                            _errorState.value = PlayerError.UnknownError
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mediaServiceBinder = null
            serviceBound = false
        }
    }

    init {
        loadRadioStations()
        bindMediaService()
    }

    override fun onCleared() {
        unbindMediaService()
        super.onCleared()
    }

    private fun bindMediaService() {
        getApplication<Application>().applicationContext.let { context ->
            val intent = Intent(context, MediaPlaybackService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun unbindMediaService() {
        if (serviceBound) {
            getApplication<Application>().applicationContext.unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun loadRadioStations() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                val context = getApplication<Application>().applicationContext
                val configSync = RemoteConfigSyncImpl(context)
                val iconManager = IconCacheManagerImpl(context)

                val stations = configSync.getRadioStations().first()

                if (stations.isNotEmpty()) {
                    // Aplicar actualizaciones de URL guardadas
                    val sharedPrefs = context.getSharedPreferences("RadioPrefs", Context.MODE_PRIVATE)
                    val savedUpdates = loadSavedUrlUpdates(sharedPrefs)

                    val finalStations = stations.map { station ->
                        val savedUrl = savedUpdates[station.id]
                        if (savedUrl != null) {
                            station.copy(streamUrl = savedUrl)
                        } else {
                            station
                        }
                    }

                    _radioStations.value = finalStations

                    // Precargar iconos
                    iconManager.preloadIcons(finalStations)
                } else {
                    // Sin configuración importada, mostrar mensaje al usuario
                    _radioStations.value = emptyList()
                }

            } catch (e: Exception) {
                Logger.e("TAG", "Error cargando configuración remota", e)
                _radioStations.value = emptyList()
            } finally {
                _isLoading.value = false
                updateWidgets()
            }
        }
    }

    /**
     * Fuerza la recarga de estaciones (usado después de sincronización)
     */
    fun reloadStations() {
        viewModelScope.launch {
            _isLoading.value = true

            val context = getApplication<Application>().applicationContext
            val configSync = RemoteConfigSyncImpl(context)
            val iconManager = IconCacheManagerImpl(context)

            configSync.getRadioStations().first().let { stations ->
                // Aplicar actualizaciones de URL guardadas
                val sharedPrefs = context.getSharedPreferences("RadioPrefs", Context.MODE_PRIVATE)
                val savedUpdates = loadSavedUrlUpdates(sharedPrefs)

                val finalStations = stations.map { station ->
                    val savedUrl = savedUpdates[station.id]
                    if (savedUrl != null) {
                        station.copy(streamUrl = savedUrl)
                    } else {
                        station
                    }
                }

                _radioStations.value = finalStations
                _isLoading.value = false

                // Precargar iconos en segundo plano
                iconManager.preloadIcons(finalStations)

                // Solo llamar al método original updateWidgets()
                updateWidgets()
            }
        }
    }


    private fun loadSavedUrlUpdates(prefs: SharedPreferences): Map<Int, String> {
        val json = prefs.getString("url_updates", "{}")
        return try {
            val type = object : TypeToken<Map<Int, String>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveUrlUpdate(stationId: Int, newUrl: String) {
        val context = getApplication<Application>().applicationContext
        val sharedPrefs = context.getSharedPreferences("RadioPrefs", Context.MODE_PRIVATE)

        val updates = loadSavedUrlUpdates(sharedPrefs).toMutableMap()
        updates[stationId] = newUrl

        val json = gson.toJson(updates)
        sharedPrefs.edit().putString("url_updates", json).apply()
    }

    /**
     * Actualiza los widgets cuando cambia el estado
     */
    /**
     * Actualiza los widgets cuando cambia el estado desde la app
     */
    private fun updateWidgets() {
        // Obtener el contexto de la aplicación
        val context = getApplication<Application>().applicationContext

        // Actualizar las SharedPreferences
        val prefs = context.getSharedPreferences(RadioAppWidget.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(RadioAppWidget.KEY_IS_PLAYING, _isPlaying.value)

            _currentStation.value?.let { station ->
                putInt(RadioAppWidget.KEY_CURRENT_STATION_ID, station.id)

                // Buscar el índice de la estación actual
                val index = _radioStations.value.indexOfFirst { it.id == station.id }
                if (index != -1) {
                    putInt(RadioAppWidget.KEY_CURRENT_STATION_INDEX, index)
                }
            }

            apply()
        }

        // Actualizar los widgets de la aplicación
        RadioAppWidget.updateAllWidgets(context)
    }

    fun playStation(station: RadioStation) {
        if (serviceBound && mediaServiceBinder != null) {
            mediaServiceBinder?.getService()?.playStation(station)

            // Start service if not running
            val context = getApplication<Application>().applicationContext
            val intent = Intent(context, MediaPlaybackService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            updateWidgets()
        }
    }

    fun pausePlayback() {
        if (serviceBound && mediaServiceBinder != null) {
            mediaServiceBinder?.getService()?.pausePlayback()
            updateWidgets()
        }
    }

    fun resumePlayback() {
        if (serviceBound && mediaServiceBinder != null) {
            mediaServiceBinder?.getService()?.resumePlayback()
            updateWidgets()
        }
    }

    private fun pausePlaybackInternal() {
        if (serviceBound && mediaServiceBinder != null) {
            mediaServiceBinder?.getService()?.pausePlayback()
            updateWidgets()
        }
    }

    private fun resumePlaybackInternal() {
        if (serviceBound && mediaServiceBinder != null) {
            mediaServiceBinder?.getService()?.resumePlayback()
            updateWidgets()
        }
    }

    @Suppress("UNUSED")
    fun stopPlayback() {
        if (serviceBound && mediaServiceBinder != null) {
            mediaServiceBinder?.getService()?.stopPlayback()
            updateWidgets()
        }
    }

    fun updateStationUrl(stationId: Int, newUrl: String) {
        viewModelScope.launch {
            // Update the in-memory list
            _radioStations.value = _radioStations.value.map { station ->
                if (station.id == stationId) {
                    station.copy(streamUrl = newUrl)
                } else {
                    station
                }
            }

            // Save the update to SharedPreferences
            saveUrlUpdate(stationId, newUrl)

            // If this is the current station, update it
            _currentStation.value?.let { current ->
                if (current.id == stationId) {
                    _currentStation.value = current.copy(streamUrl = newUrl)
                }
            }

            // Actualizar los widgets al cambiar la URL
            updateWidgets()
        }
    }

    fun resetUrlToDefault(stationId: Int) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val sharedPrefs = context.getSharedPreferences("RadioPrefs", Context.MODE_PRIVATE)

            // Remove the saved update
            val updates = loadSavedUrlUpdates(sharedPrefs).toMutableMap()
            updates.remove(stationId)

            val json = gson.toJson(updates)
            sharedPrefs.edit().putString("url_updates", json).apply()

            // Reload stations to apply the reset
            loadRadioStations()
        }
    }

}