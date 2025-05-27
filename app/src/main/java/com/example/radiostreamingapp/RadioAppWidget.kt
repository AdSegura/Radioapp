package com.example.radiostreamingapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.radiostreamingapp.utils.Logger
import android.widget.RemoteViews
//import com.example.radiostreamingapp.data.RadioConfigParser
import com.example.radiostreamingapp.data.RadioStation
import com.example.radiostreamingapp.sync.impl.IconCacheManagerImpl
import com.example.radiostreamingapp.sync.impl.RemoteConfigSyncImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import android.graphics.Bitmap

/**
 * Implementación del widget de radio para la pantalla de inicio.
 * Con soporte para modo oscuro/claro.
 */
class RadioAppWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "RadioAppWidget"

        // Acciones para el widget
        const val ACTION_PLAY_PAUSE = "com.example.radiostreamingapp.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.radiostreamingapp.ACTION_NEXT"

        // Claves para SharedPreferences
        const val PREFS_NAME = "RadioWidgetPrefs"
        const val KEY_CURRENT_STATION_ID = "currentStationId"
        const val KEY_IS_PLAYING = "isPlaying"
        const val KEY_CURRENT_STATION_INDEX = "currentStationIndex"

        //private var radioStations: List<RadioStation> = listOf()
        private var radioStations: List<RadioStation> = emptyList()
        private var currentStationIndex = 0
        private var isPlaying = false

        /**
         * Determina si el dispositivo está en modo oscuro
         */
        private fun isDarkMode(context: Context): Boolean {
            val uiMode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

        /**
         * Actualiza todos los widgets de la aplicación
         */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, RadioAppWidget::class.java)
            )

            if (appWidgetIds.isNotEmpty()) {
                // Cargar el estado más reciente
                loadState(context)

                // Actualizar cada widget
                for (appWidgetId in appWidgetIds) {
                    val views = createRemoteViews(context, appWidgetId)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }

                Logger.d(TAG, "Actualizados ${appWidgetIds.size} widgets")
            }
        }

        /**
         * Carga el estado desde SharedPreferences y las estaciones desde ConfigSync
         */
        private fun loadState(context: Context) {
            // SIEMPRE cargar estaciones desde ConfigSync (no desde caché estático)
            radioStations = try {
                val configSync = RemoteConfigSyncImpl(context)
                runBlocking {
                    configSync.getRadioStations().first()
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Error cargando estaciones desde ConfigSync", e)
                 emptyList()
            }

            // Cargar estado de reproducción desde SharedPreferences
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false)

            // Validar estación actual con las nuevas estaciones cargadas
            val stationId = prefs.getInt(KEY_CURRENT_STATION_ID, -1)
            if (stationId != -1 && radioStations.isNotEmpty()) {
                val stationIndex = radioStations.indexOfFirst { it.id == stationId }
                if (stationIndex != -1) {
                    currentStationIndex = stationIndex
                } else {
                    // La estación ya no existe, resetear a la primera
                    currentStationIndex = 0
                    Logger.w(TAG, "Estación con ID $stationId ya no existe, reseteando a índice 0")
                }
            } else {
                currentStationIndex = prefs.getInt(KEY_CURRENT_STATION_INDEX, 0)
            }

            // Validar que el índice esté dentro del rango
            if (radioStations.isNotEmpty() && currentStationIndex >= radioStations.size) {
                currentStationIndex = 0
                Logger.w(TAG, "Índice fuera de rango, reseteando a 0")
            }

            Logger.d(TAG, "Estado cargado: ${radioStations.size} estaciones, índice actual: $currentStationIndex")
        }

        /**
         * Guarda el estado en SharedPreferences
         */
        private fun saveState(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            prefs.edit().apply {
                putBoolean(KEY_IS_PLAYING, isPlaying)
                putInt(KEY_CURRENT_STATION_INDEX, currentStationIndex)

                // Guardar ID de la estación actual si está disponible
                if (radioStations.isNotEmpty() && currentStationIndex < radioStations.size) {
                    putInt(KEY_CURRENT_STATION_ID, radioStations[currentStationIndex].id)
                }

                apply()
            }
        }

        /**
         * Redimensiona un bitmap para uso en widgets
         */
        private fun resizeBitmapForWidget(originalBitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
            val width = originalBitmap.width
            val height = originalBitmap.height

            // Calcular el factor de escala manteniendo la proporción
            val scale = minOf(
                maxWidth.toFloat() / width,
                maxHeight.toFloat() / height,
                1.0f // No agrandar imágenes pequeñas
            )

            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()

            return Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
        }

        /**
         * Establece el icono de la estación en el widget
         */
        private fun setStationIcon(context: Context, views: RemoteViews, station: RadioStation, appWidgetId: Int) {
            // Primero establecer icono por defecto
            views.setImageViewResource(R.id.widget_station_icon, station.iconResource)

            // Luego intentar cargar desde caché en segundo plano
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val iconManager = IconCacheManagerImpl(context)
                    val originalBitmap = iconManager.getIconBitmap(station)

                    if (originalBitmap != null) {
                        // Redimensionar bitmap para widgets (máximo 200x200 pixels)
                        val resizedBitmap = resizeBitmapForWidget(originalBitmap, 200, 200)

                        // Actualizar el widget con el nuevo icono redimensionado
                        withContext(Dispatchers.Main) {
                            views.setImageViewBitmap(R.id.widget_station_icon, resizedBitmap)
                            val appWidgetManager = AppWidgetManager.getInstance(context)
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Error cargando icono para widget", e)
                }
            }
        }

        /**
         * Crea las vistas remotas para un widget
         */
        private fun createRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_radio_player)

            // Aplicar el fondo adecuado según el modo oscuro o claro
            if (isDarkMode(context)) {
                views.setInt(R.id.widget_root_layout, "setBackgroundResource", R.drawable.widget_background_dark)
            } else {
                views.setInt(R.id.widget_root_layout, "setBackgroundResource", R.drawable.widget_background_light)
            }

            // Verificar que hay estaciones disponibles
            if (radioStations.isEmpty()) {
                views.setTextViewText(R.id.widget_station_name, "No stations available")
                views.setTextViewText(R.id.widget_status, "Check app settings")
                return views
            }

            // Obtener la estación actual
            val station = radioStations[currentStationIndex]

            // Configurar la información de la estación
            views.setTextViewText(R.id.widget_station_name, station.name)
            views.setTextViewText(R.id.widget_status, if (isPlaying) "Playing" else "Paused")
            views.setImageViewResource(R.id.widget_station_icon, station.iconResource)

            // AQUÍ ESTÁ EL CAMBIO: Cargar icono desde caché o recurso
            setStationIcon(context, views, station, appWidgetId)

            // Configurar el botón de reproducción/pausa
            val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
            views.setImageViewResource(R.id.widget_play_button, playPauseIcon)

            // Intent para abrir la app al hacer clic en el icono o nombre
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_station_icon, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_station_name, openAppPendingIntent)

            // Intent para reproducir/pausar
            val playPauseIntent = Intent(context, RadioAppWidget::class.java).apply {
                action = ACTION_PLAY_PAUSE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val playPausePendingIntent = PendingIntent.getBroadcast(
                context, 0, playPauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_play_button, playPausePendingIntent)

            // Intent para siguiente estación
            val nextIntent = Intent(context, RadioAppWidget::class.java).apply {
                action = ACTION_NEXT
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context, 1, nextIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_next_button, nextPendingIntent)

            return views
        }
    }


    /**
     * Método llamado cuando el widget necesita actualizarse
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Logger.d(TAG, "onUpdate llamado para ${appWidgetIds.size} widgets")

        // Cargar estaciones y estado actual
        loadState(context)

        // Actualizar cada widget
        for (appWidgetId in appWidgetIds) {
            val views = createRemoteViews(context, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    /**
     * Método llamado cuando se recibe un broadcast para el widget
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        Logger.d(TAG, "onReceive: ${intent.action}")

        // Cargar estaciones y estado actual
        loadState(context)

        when (intent.action) {
            ACTION_PLAY_PAUSE -> handlePlayPauseAction(context)
            ACTION_NEXT -> handleNextAction(context)
        }
    }

    /**
     * Maneja la acción de reproducir/pausar
     */
    private fun handlePlayPauseAction(context: Context) {
        // Verificar que hay estaciones disponibles
        if (radioStations.isEmpty()) return

        // Obtener la estación actual
        val station = radioStations[currentStationIndex]

        // Cambiar el estado de reproducción
        isPlaying = !isPlaying

        // Guardar el nuevo estado
        saveState(context)

        // Enviar el comando al servicio
        val serviceIntent = Intent(context, MediaPlaybackService::class.java).apply {
            if (isPlaying) {
                action = MediaPlaybackService.ACTION_PLAY
                putExtra(MediaPlaybackService.EXTRA_STATION_ID, station.id)
                putExtra(MediaPlaybackService.EXTRA_STATION_NAME, station.name)
                putExtra(MediaPlaybackService.EXTRA_STATION_URL, station.streamUrl)
                putExtra(MediaPlaybackService.EXTRA_STATION_ICON, station.iconResource)
            } else {
                action = MediaPlaybackService.ACTION_PAUSE
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    /**
     * Maneja la acción de cambiar a la siguiente estación
     */
    private fun handleNextAction(context: Context) {
        // Verificar que hay estaciones disponibles
        if (radioStations.isEmpty()) return

        // Avanzar a la siguiente estación
        currentStationIndex = (currentStationIndex + 1) % radioStations.size
        val nextStation = radioStations[currentStationIndex]

        // Guardar el nuevo estado
        saveState(context)

        // Si está reproduciendo, iniciar la reproducción de la nueva estación
        if (isPlaying) {
            val serviceIntent = Intent(context, MediaPlaybackService::class.java).apply {
                action = MediaPlaybackService.ACTION_PLAY
                putExtra(MediaPlaybackService.EXTRA_STATION_ID, nextStation.id)
                putExtra(MediaPlaybackService.EXTRA_STATION_NAME, nextStation.name)
                putExtra(MediaPlaybackService.EXTRA_STATION_URL, nextStation.streamUrl)
                putExtra(MediaPlaybackService.EXTRA_STATION_ICON, nextStation.iconResource)
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        // Actualizar los widgets
        updateAllWidgets(context)
    }
}