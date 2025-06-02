package com.example.radiostreamingapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.example.radiostreamingapp.data.RadioStation
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

import com.example.radiostreamingapp.sync.impl.RemoteConfigSyncImpl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

import com.example.radiostreamingapp.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import android.content.BroadcastReceiver
import android.content.IntentFilter

/**
 * Servicio para reproducir las emisoras de radio en segundo plano
 * y mostrar notificaciones con controles de reproducción
 */
class MediaPlaybackService : Service() {

    // Propiedades públicas para acceder desde ViewModel
    val _isPlaying = MutableStateFlow(false)
    val _currentStation = MutableStateFlow<RadioStation?>(null)
    val _playerError = MutableStateFlow<String?>(null)
    val _errorType = MutableStateFlow<ErrorType?>(null)

    // StateFlows expuestos
    val isPlaying: StateFlow<Boolean> = _isPlaying
    val currentStation: StateFlow<RadioStation?> = _currentStation
    val playerError: StateFlow<String?> = _playerError
    val errorType: StateFlow<ErrorType?> = _errorType

    // AUDIO FOCUS
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var shouldResumeOnFocusGain = false
    private var audioBecomingNoisyReceiver: BroadcastReceiver? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Recuperaste el foco de audio
                if (shouldResumeOnFocusGain && _currentStation.value != null) {
                    resumePlaybackInternal()
                }
                shouldResumeOnFocusGain = false
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Perdiste el foco permanentemente - pausa y no reanudes automáticamente
                shouldResumeOnFocusGain = false
                pausePlaybackInternal()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Pérdida temporal (ej: llamada) - pausa pero recuerda reanudar
                if (_isPlaying.value) {
                    shouldResumeOnFocusGain = true
                    pausePlaybackInternal()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Para radio streaming, es mejor pausar que hacer duck
                if (_isPlaying.value) {
                    shouldResumeOnFocusGain = true
                    pausePlaybackInternal()
                }
            }
        }
    }

    enum class ErrorType {
        NETWORK, FORMAT, UNKNOWN
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "radio_playback_channel"

        // Custom action constants for media control
        const val ACTION_PLAY = "com.example.radiostreamingapp.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.radiostreamingapp.ACTION_PAUSE"
        const val ACTION_STOP = "com.example.radiostreamingapp.ACTION_STOP"
        const val ACTION_NEXT = "com.example.radiostreamingapp.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.radiostreamingapp.ACTION_PREVIOUS"

        // Extra constants for passing station data
        const val EXTRA_STATION_ID = "com.example.radiostreamingapp.EXTRA_STATION_ID"
        const val EXTRA_STATION_NAME = "com.example.radiostreamingapp.EXTRA_STATION_NAME"
        const val EXTRA_STATION_URL = "com.example.radiostreamingapp.EXTRA_STATION_URL"
        const val EXTRA_STATION_ICON = "com.example.radiostreamingapp.EXTRA_STATION_ICON"

    }

    private val binder = MediaServiceBinder()
    private lateinit var player: ExoPlayer
    private lateinit var notificationManager: NotificationManager
    private lateinit var mediaSession: MediaSessionCompat

    inner class MediaServiceBinder : Binder() {
        fun getService(): MediaPlaybackService = this@MediaPlaybackService
    }

    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Initialize ExoPlayer
        player = ExoPlayer.Builder(this).build()
        player.addListener(playerListener)

        setupAudioBecomingNoisyReceiver()

        // Create MediaSession
        mediaSession = MediaSessionCompat(this, "RadioPlaybackService").apply {
            // Set callbacks for MediaSession
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    super.onPlay()
                    resumePlayback()
                }

                override fun onPause() {
                    super.onPause()
                    pausePlayback()
                }

                override fun onStop() {
                    super.onStop()
                    stopPlayback()
                }

                override fun onSkipToNext() {
                    super.onSkipToNext()
                    nextStation()
                }

                override fun onSkipToPrevious() {
                    super.onSkipToPrevious()
                    previousStation()
                }

                override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                    // Extract the key event from the intent
                    val keyEvent = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)

                    keyEvent?.let {
                        if (it.action == KeyEvent.ACTION_DOWN) {
                            when (it.keyCode) {
                                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                    resumePlayback()
                                    return true
                                }
                                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                    pausePlayback()
                                    return true
                                }
                                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                    if (_isPlaying.value) pausePlayback() else resumePlayback()
                                    return true
                                }
                                KeyEvent.KEYCODE_MEDIA_STOP -> {
                                    stopPlayback()
                                    return true
                                }
                                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                    nextStation()
                                    return true
                                }
                                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                    previousStation()
                                    return true
                                }
                            }
                        }
                    }

                    // Let the MediaSession handle the button if we didn't
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
            })

            // Set initial PlaybackState
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY
                                or PlaybackStateCompat.ACTION_PAUSE
                                or PlaybackStateCompat.ACTION_PLAY_PAUSE
                                or PlaybackStateCompat.ACTION_STOP
                                or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                                or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    )
                    .build()
            )

            // Set active to true to receive media button events
            isActive = true
        }

        // Create notification channel
        createNotificationChannel()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle our custom actions
        // IMPORTANTE: Iniciar foreground inmediatamente si no está iniciado
        if (_currentStation.value != null) {
            startForegroundService()
        }
        when (intent?.action) {
            ACTION_PLAY -> {
                // Verificar si se están pasando datos de estación
                val stationId = intent.getIntExtra(EXTRA_STATION_ID, -1)
                val stationName = intent.getStringExtra(EXTRA_STATION_NAME)
                val stationUrl = intent.getStringExtra(EXTRA_STATION_URL)
                val iconResource = intent.getIntExtra(EXTRA_STATION_ICON, R.drawable.ic_radio_default)

                if (stationId != -1 && stationName != null && stationUrl != null) {
                    // Crear una nueva instancia de RadioStation y reproducirla
                    val station = RadioStation(stationId, stationName, stationUrl, iconResource)
                    playStation(station)
                } else {
                    // Sin datos de estación, simplemente reanudar la reproducción
                    resumePlayback()
                }
            }
            ACTION_PAUSE -> {
                pausePlayback()
            }
            ACTION_STOP -> {
                stopPlayback()
            }
            ACTION_NEXT -> {
                nextStation()
            }
            ACTION_PREVIOUS -> {
                previousStation()
            }
            // Let MediaButtonReceiver handle standard media button intents
            else -> MediaButtonReceiver.handleIntent(mediaSession, intent)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        abandonAudioFocus()

        audioBecomingNoisyReceiver?.let { unregisterReceiver(it) }
        player.release()
        mediaSession.release()
        super.onDestroy()
    }

    fun playStation(station: RadioStation) {
        // Solicita audio focus antes de reproducir
        if (!requestAudioFocus()) {
            return // No continúa si no puede obtener audio focus
        }

        shouldResumeOnFocusGain = false // Reset flag al iniciar nueva reproducción

        // Update current station
        _currentStation.value = station

        // Reset error states
        _playerError.value = null
        _errorType.value = null

        try {
            // Prepare MediaItem from URL
            val mediaItem = MediaItem.fromUri(station.streamUrl)

            // Reset player and prepare new source
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()

            // Update MediaSession state and metadata
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, station.name)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Radio Streaming")
                    .build()
            )

            mediaSession.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                    .setActions(
                        PlaybackStateCompat.ACTION_PAUSE
                                or PlaybackStateCompat.ACTION_PLAY_PAUSE
                                or PlaybackStateCompat.ACTION_STOP
                                or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                                or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    )
                    .build()
            )

            startForegroundService()
            updateAllUIComponents()
        } catch (e: Exception) {
            _playerError.value = "Error: ${e.message}"
            _errorType.value = ErrorType.UNKNOWN
        }
    }

    fun pausePlayback() {
        shouldResumeOnFocusGain = false // Usuario pausó manualmente
        pausePlaybackInternal()
        // Libera audio focus cuando pausa manualmente
        abandonAudioFocus()
    }

    fun resumePlayback() {
        if (requestAudioFocus()) {
            resumePlaybackInternal()
        }
    }

    fun stopPlayback() {
        shouldResumeOnFocusGain = false
        abandonAudioFocus() // Libera audio focus al detener

        player.stop()
        _isPlaying.value = false
        _currentStation.value = null

        // Update MediaSession state
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_STOPPED, 0, 1.0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY
                            or PlaybackStateCompat.ACTION_PLAY_PAUSE
                            or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                            or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .build()
        )

        // Stop the service first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        // Then stop self
        stopSelf()
        updateAllUIComponents()
    }

    /**
     * Cambia a la siguiente emisora en la lista
     */
    fun nextStation() {
        try {
            val configSync = RemoteConfigSyncImpl(this)

            // Usar runBlocking para obtener las estaciones síncronamente
            val stations = runBlocking {
                configSync.getRadioStations().first()
            }

            if (stations.isEmpty()) return

            // Encontrar el índice de la estación actual
            val currentIndex = _currentStation.value?.let { current ->
                stations.indexOfFirst { it.id == current.id }
            } ?: -1

            // Calcular el índice de la siguiente estación
            val nextIndex = if (currentIndex >= 0 && currentIndex < stations.size - 1) {
                currentIndex + 1
            } else {
                0 // Volver al principio si estamos al final
            }

            // Reproducir la siguiente estación
            if (nextIndex < stations.size) {
                playStation(stations[nextIndex])
            }
        } catch (e: Exception) {
            Logger.e("MediaPlaybackService", "Error navegando a siguiente estación: ${e.message}")
        }
    }

    /**
     * Cambia a la emisora anterior en la lista
     */
    fun previousStation() {
        try {
            val configSync = RemoteConfigSyncImpl(this)

            val stations = runBlocking {
                configSync.getRadioStations().first()
            }

            if (stations.isEmpty()) return

            // Encontrar el índice de la estación actual
            val currentIndex = _currentStation.value?.let { current ->
                stations.indexOfFirst { it.id == current.id }
            } ?: -1

            // Calcular el índice de la estación anterior
            val prevIndex = if (currentIndex > 0) {
                currentIndex - 1
            } else {
                stations.size - 1 // Ir al final si estamos al principio
            }

            // Reproducir la estación anterior
            if (prevIndex >= 0 && prevIndex < stations.size) {
                playStation(stations[prevIndex])
            }
        } catch (e: Exception) {
            Logger.e("MediaPlaybackService", "Error navegando a estación anterior: ${e.message}")
        }
    }

    private fun startForegroundService() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotification(): Notification {
        // Create intent for opening main activity when notification is clicked
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create our custom action intents for direct control
        // Play action
        val playIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_PLAY
        }
        val playPendingIntent = PendingIntent.getService(
            this, 1, playIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Pause action
        val pauseIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Stop action
        val stopIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Next action
        val nextIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this, 4, nextIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Previous action
        val previousIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val previousPendingIntent = PendingIntent.getService(
            this, 5, previousIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Create notification
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radio_default)
            .setContentTitle(_currentStation.value?.name ?: "Radio")
            .setContentText(if (_isPlaying.value) "Playing" else "Paused")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true) // Make it persistent

        // Previous button
        builder.addAction(
            android.R.drawable.ic_media_previous,
            "Previous",
            previousPendingIntent
        )

        // Play/Pause action
        if (_isPlaying.value) {
            // Show pause button when playing
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                pausePendingIntent
            )
        } else {
            // Show play button when paused
            builder.addAction(
                android.R.drawable.ic_media_play,
                "Play",
                playPendingIntent
            )
        }

        // Next button
        builder.addAction(
            android.R.drawable.ic_media_next,
            "Next",
            nextPendingIntent
        )

        // Stop action
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            stopPendingIntent
        )

        // Make it media style
        val mediaStyle = MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2) // Show previous, play/pause, and next in compact view

        builder.setStyle(mediaStyle)

        return builder.build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Radio Playback"
            val descriptionText = "Radio streaming playback controls"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    _isPlaying.value = player.isPlaying
                    updateAllUIComponents()
                }
                Player.STATE_ENDED -> {
                    // Stream ended (should not happen with radio)
                    _isPlaying.value = false
                    updateAllUIComponents()
                }
                Player.STATE_BUFFERING -> {
                    // Buffering state
                }
                Player.STATE_IDLE -> {
                    // Stream not initialized
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            updateAllUIComponents()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Logger.e("TAG", "=== PLAYER ERROR DETAILS ===")
            Logger.e("TAG", "Error code: ${error.errorCode}")
            Logger.e("TAG", "Error message: ${error.message}")
            Logger.e("TAG", "Error cause: ${error.cause}")
            Logger.e("TAG", "Error type: ${error.getErrorCodeName()}")
            // Classify error
            val errorMsg = when (error.errorCode) {
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                    _errorType.value = ErrorType.NETWORK
                    "Network connection failed. Check your internet connection."
                }

                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> {
                    _errorType.value = ErrorType.FORMAT
                    "Stream format error. The station might be temporarily unavailable."
                }

                else -> {
                    _errorType.value = ErrorType.UNKNOWN
                    "Playback error: ${error.message}"
                }
            }

            _playerError.value = errorMsg

            // Try to reconnect
            player.prepare()
        }
    }

    /**
     * Método centralizado para actualizar todos los componentes de UI
     * (notificación y widgets) cuando cambia el estado de reproducción.
     */
    private fun updateAllUIComponents() {
        // 1. Actualizar la notificación
        updateNotification()

        // 2. Actualizar SharedPreferences para el widget
        val prefs = applicationContext.getSharedPreferences(RadioAppWidget.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(RadioAppWidget.KEY_IS_PLAYING, _isPlaying.value)

            _currentStation.value?.let { station ->
                putInt(RadioAppWidget.KEY_CURRENT_STATION_ID, station.id)
                // El widget ya carga las estaciones desde ConfigSync y calcula el índice
                // No necesitamos calcularlo aquí
            }

            apply()
        }

        // 3. Actualizar los widgets
        RadioAppWidget.updateAllWidgets(applicationContext)
    }

    private fun pausePlaybackInternal() {
        player.pause()
        _isPlaying.value = false

        // Update MediaSession state
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY
                            or PlaybackStateCompat.ACTION_PLAY_PAUSE
                            or PlaybackStateCompat.ACTION_STOP
                            or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                            or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .build()
        )

        // Update notification with new play/pause state
        updateAllUIComponents()
    }

    private fun resumePlaybackInternal() {
        player.play()
        _isPlaying.value = true

        // Update MediaSession state
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PAUSE
                            or PlaybackStateCompat.ACTION_PLAY_PAUSE
                            or PlaybackStateCompat.ACTION_STOP
                            or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                            or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .build()
        )

        // Ensure foreground service is running with updated notification
        startForegroundService()
        updateAllUIComponents()
    }

    private fun requestAudioFocus(): Boolean {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()

        return audioManager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            audioFocusRequest = null
        }
    }

    private fun setupAudioBecomingNoisyReceiver() {
        audioBecomingNoisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    // Auriculares desconectados - pausa la reproducción
                    pausePlayback()
                }
            }
        }

        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(audioBecomingNoisyReceiver, filter)
    }
}