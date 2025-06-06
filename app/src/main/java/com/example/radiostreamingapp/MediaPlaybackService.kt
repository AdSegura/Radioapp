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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.common.C
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.common.util.UnstableApi

import com.example.radiostreamingapp.sync.impl.RemoteConfigSyncImpl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

import com.example.radiostreamingapp.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

import android.media.AudioManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Servicio corregido - ExoPlayer maneja Audio Focus automáticamente
 */
@UnstableApi
class MediaPlaybackService : Service() {

    // Propiedades públicas para acceder desde ViewModel
    val _isPlaying = MutableStateFlow(false)
    val _currentStation = MutableStateFlow<RadioStation?>(null)
    val _playerError = MutableStateFlow<String?>(null)
    val _errorType = MutableStateFlow<ErrorType?>(null)
    val _isBuffering = MutableStateFlow(false)

    // StateFlows expuestos
    val isPlaying: StateFlow<Boolean> = _isPlaying
    val currentStation: StateFlow<RadioStation?> = _currentStation
    val playerError: StateFlow<String?> = _playerError
    val errorType: StateFlow<ErrorType?> = _errorType
    val isBuffering: StateFlow<Boolean> = _isBuffering

    // RECONEXIÓN AUTOMÁTICA
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var reconnectJob: Job? = null
    private var retryCount = 0
    private val maxRetries = 3 // Reducido para testing
    private val baseRetryDelayMs = 2000L

    // AUDIO BECOMING NOISY RECEIVER (solo para auriculares desconectados)
    private var audioBecomingNoisyReceiver: BroadcastReceiver? = null

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

        // Initialize ExoPlayer with optimized configuration
        player = createOptimizedExoPlayer()
        player.addListener(playerListener)

        setupAudioBecomingNoisyReceiver()

        // Create MediaSession
        mediaSession = MediaSessionCompat(this, "RadioPlaybackService").apply {
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

                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
            })

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

            isActive = true
        }

        // Create notification channel
        createNotificationChannel()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    /**
     * Crea un ExoPlayer optimizado para streaming de radio
     * IMPORTANTE: Ahora ExoPlayer maneja Audio Focus automáticamente
     */
    private fun createOptimizedExoPlayer(): ExoPlayer {
        // Configuración más conservadora de LoadControl para evitar problemas
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, 16))
            .setBufferDurationsMs(
                3000,    // minBufferMs - Buffer mínimo más conservador
                15000,   // maxBufferMs - Buffer máximo reducido
                1500,    // bufferForPlaybackMs - Inicio más rápido
                3000     // bufferForPlaybackAfterRebufferMs - Rebuffer más rápido
            )
            .setTargetBufferBytes(-1)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Configuración de DataSource para HTTP
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("RadioStreamingApp/1.0 (Android)")
            .setConnectTimeoutMs(10000) // Timeout más corto para testing
            .setReadTimeoutMs(10000)
            .setAllowCrossProtocolRedirects(true)

        return ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                ProgressiveMediaSource.Factory(httpDataSourceFactory)
            )
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // ← CLAVE: ExoPlayer maneja Audio Focus automáticamente
            )
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (_currentStation.value != null) {
            startForegroundService()
        }

        when (intent?.action) {
            ACTION_PLAY -> {
                val stationId = intent.getIntExtra(EXTRA_STATION_ID, -1)
                val stationName = intent.getStringExtra(EXTRA_STATION_NAME)
                val stationUrl = intent.getStringExtra(EXTRA_STATION_URL)
                val iconResource = intent.getIntExtra(EXTRA_STATION_ICON, R.drawable.ic_radio_default)

                if (stationId != -1 && stationName != null && stationUrl != null) {
                    val station = RadioStation(stationId, stationName, stationUrl, iconResource)
                    playStation(station)
                } else {
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
            else -> MediaButtonReceiver.handleIntent(mediaSession, intent)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        reconnectJob?.cancel()
        audioBecomingNoisyReceiver?.let { unregisterReceiver(it) }
        player.release()
        mediaSession.release()
        super.onDestroy()
    }

    fun playStation(station: RadioStation) {
        // Cancelar cualquier reintento en curso
        reconnectJob?.cancel()
        retryCount = 0

        _currentStation.value = station
        _playerError.value = null
        _errorType.value = null

        try {
            Logger.d("MediaPlaybackService", "Iniciando reproducción: ${station.name} - ${station.streamUrl}")

            // Crear MediaItem
            val mediaItem = MediaItem.Builder()
                .setUri(station.streamUrl)
                .build()

            // Resetear y configurar el player
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play() // ExoPlayer se encarga del Audio Focus automáticamente

            // Update MediaSession
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, station.name)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Radio Streaming")
                    .build()
            )

            updatePlaybackState(PlaybackStateCompat.STATE_CONNECTING)
            startForegroundService()
            updateAllUIComponents()

        } catch (e: Exception) {
            Logger.e("MediaPlaybackService", "Error al iniciar reproducción", e)
            handlePlaybackError("Error: ${e.message}", ErrorType.UNKNOWN)
        }
    }

    /**
     * Maneja errores de reproducción con reintentos automáticos
     */
    private fun handlePlaybackError(errorMessage: String, errorType: ErrorType) {
        Logger.e("MediaPlaybackService", "Error de reproducción: $errorMessage")

        _playerError.value = errorMessage
        _errorType.value = errorType

        val currentStation = _currentStation.value
        if (currentStation != null && retryCount < maxRetries) {
            retryCount++
            val delayMs = baseRetryDelayMs * retryCount

            Logger.d("MediaPlaybackService", "Reintentando en ${delayMs}ms (intento $retryCount/$maxRetries)")

            reconnectJob = serviceScope.launch {
                delay(delayMs)
                if (_currentStation.value?.id == currentStation.id) {
                    Logger.d("MediaPlaybackService", "Ejecutando reintento $retryCount para ${currentStation.name}")
                    playStation(currentStation)
                }
            }
        } else {
            Logger.e("MediaPlaybackService", "Se agotaron los reintentos para ${currentStation?.name}")
        }
    }

    // MÉTODOS SIMPLIFICADOS SIN MANEJO MANUAL DE AUDIO FOCUS
    fun pausePlayback() {
        player.pause() // ExoPlayer maneja Audio Focus automáticamente
        _isPlaying.value = false
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        updateAllUIComponents()
    }

    fun resumePlayback() {
        player.play() // ExoPlayer maneja Audio Focus automáticamente
        _isPlaying.value = true
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        startForegroundService()
        updateAllUIComponents()
    }

    fun stopPlayback() {
        reconnectJob?.cancel()
        retryCount = 0

        player.stop()
        _isPlaying.value = false
        _isBuffering.value = false
        _currentStation.value = null

        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
        updateAllUIComponents()
    }

    fun nextStation() {
        try {
            val configSync = RemoteConfigSyncImpl(this)
            val stations = runBlocking {
                configSync.getRadioStations().first()
            }

            if (stations.isEmpty()) return

            val currentIndex = _currentStation.value?.let { current ->
                stations.indexOfFirst { it.id == current.id }
            } ?: -1

            val nextIndex = if (currentIndex >= 0 && currentIndex < stations.size - 1) {
                currentIndex + 1
            } else {
                0
            }

            if (nextIndex < stations.size) {
                playStation(stations[nextIndex])
            }
        } catch (e: Exception) {
            Logger.e("MediaPlaybackService", "Error navegando a siguiente estación: ${e.message}")
        }
    }

    fun previousStation() {
        try {
            val configSync = RemoteConfigSyncImpl(this)
            val stations = runBlocking {
                configSync.getRadioStations().first()
            }

            if (stations.isEmpty()) return

            val currentIndex = _currentStation.value?.let { current ->
                stations.indexOfFirst { it.id == current.id }
            } ?: -1

            val prevIndex = if (currentIndex > 0) {
                currentIndex - 1
            } else {
                stations.size - 1
            }

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
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_PLAY
        }
        val playPendingIntent = PendingIntent.getService(
            this, 1, playIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this, 4, nextIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val previousPendingIntent = PendingIntent.getService(
            this, 5, previousIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Determinar texto y estado
        val statusText = when {
            _isBuffering.value -> "Connecting..."
            _isPlaying.value -> "Playing"
            else -> "Paused"
        }

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radio_default)
            .setContentTitle(_currentStation.value?.name ?: "Radio")
            .setContentText(statusText)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)

        builder.addAction(
            android.R.drawable.ic_media_previous,
            "Previous",
            previousPendingIntent
        )

        if (_isPlaying.value) {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                pausePendingIntent
            )
        } else {
            builder.addAction(
                android.R.drawable.ic_media_play,
                "Play",
                playPendingIntent
            )
        }

        builder.addAction(
            android.R.drawable.ic_media_next,
            "Next",
            nextPendingIntent
        )

        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            stopPendingIntent
        )

        val mediaStyle = MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

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
            Logger.d("MediaPlaybackService", "Playback state changed: $playbackState")

            when (playbackState) {
                Player.STATE_READY -> {
                    _isPlaying.value = player.isPlaying
                    _isBuffering.value = false
                    retryCount = 0 // Reset retry count on success
                    Logger.d("MediaPlaybackService", "Stream ready - playing: ${player.isPlaying}")
                    updateAllUIComponents()
                }
                Player.STATE_ENDED -> {
                    _isPlaying.value = false
                    _isBuffering.value = false
                    Logger.d("MediaPlaybackService", "Stream ended")
                    updateAllUIComponents()
                }
                Player.STATE_BUFFERING -> {
                    _isBuffering.value = true
                    Logger.d("MediaPlaybackService", "Buffering...")
                    updateAllUIComponents()
                }
                Player.STATE_IDLE -> {
                    _isBuffering.value = false
                    Logger.d("MediaPlaybackService", "Player idle")
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Logger.d("MediaPlaybackService", "IsPlaying changed: $isPlaying")
            _isPlaying.value = isPlaying
            updateAllUIComponents()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Logger.e("MediaPlaybackService", "=== PLAYER ERROR DETAILS ===")
            Logger.e("MediaPlaybackService", "Error code: ${error.errorCode}")
            Logger.e("MediaPlaybackService", "Error message: ${error.message}")
            Logger.e("MediaPlaybackService", "Error cause: ${error.cause}")

            val errorMsg = when (error.errorCode) {
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                    "Network connection failed. Retrying..."
                }
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> {
                    "Stream format error. Retrying..."
                }
                else -> {
                    "Playback error: ${error.message}"
                }
            }

            val errorType = when (error.errorCode) {
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> ErrorType.NETWORK
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> ErrorType.FORMAT
                else -> ErrorType.UNKNOWN
            }

            handlePlaybackError(errorMsg, errorType)
        }
    }

    private fun updatePlaybackState(state: Int) {
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, 0, 1.0f)
                .setActions(actions)
                .build()
        )
    }

    private fun updateAllUIComponents() {
        updateNotification()

        val prefs = applicationContext.getSharedPreferences(RadioAppWidget.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(RadioAppWidget.KEY_IS_PLAYING, _isPlaying.value)

            _currentStation.value?.let { station ->
                putInt(RadioAppWidget.KEY_CURRENT_STATION_ID, station.id)
            }

            apply()
        }

        RadioAppWidget.updateAllWidgets(applicationContext)
    }

    // Solo mantener el receiver para auriculares desconectados
    private fun setupAudioBecomingNoisyReceiver() {
        audioBecomingNoisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    Logger.d("MediaPlaybackService", "Auriculares desconectados - pausando")
                    pausePlayback()
                }
            }
        }

        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(audioBecomingNoisyReceiver, filter)
    }
}