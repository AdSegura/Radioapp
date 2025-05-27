package com.example.radiostreamingapp

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Servicio para mantener actualizado el widget cuando el estado del reproductor cambia
 */
class WidgetUpdateService : LifecycleService() {

    companion object {
        private const val TAG = "WidgetUpdateService"
        const val ACTION_START_MONITORING = "com.example.radiostreamingapp.ACTION_START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.example.radiostreamingapp.ACTION_STOP_MONITORING"

        fun startService(context: Context) {
            val intent = Intent(context, WidgetUpdateService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WidgetUpdateService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }
    }

    private var mediaService: MediaPlaybackService? = null
    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            val binder = service as MediaPlaybackService.MediaServiceBinder
            mediaService = binder.getService()
            startMonitoring()
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            mediaService = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        bindToMediaService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START_MONITORING -> {
                if (mediaService == null) {
                    bindToMediaService()
                } else {
                    startMonitoring()
                }
            }
            ACTION_STOP_MONITORING -> {
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun bindToMediaService() {
        val intent = Intent(this, MediaPlaybackService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun startMonitoring() {
        val service = mediaService ?: return

        // Observar cambios en el estado de reproducción
        lifecycleScope.launch {
            service.isPlaying.collectLatest { isPlaying ->
                RadioAppWidget.updateAllWidgets(applicationContext)
            }
        }

        // Observar cambios en la estación actual
        lifecycleScope.launch {
            service.currentStation.collectLatest { station ->
                RadioAppWidget.updateAllWidgets(applicationContext)
            }
        }
    }

    override fun onDestroy() {
        unbindService(serviceConnection)
        super.onDestroy()
    }
}