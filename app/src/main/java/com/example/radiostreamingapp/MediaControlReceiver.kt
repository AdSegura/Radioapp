package com.example.radiostreamingapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.radiostreamingapp.utils.Logger

/**
 * BroadcastReceiver para recibir acciones de control de reproducción
 * desde las notificaciones
 */
class MediaControlReceiver : BroadcastReceiver() {
    @OptIn(UnstableApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        Logger.d("MediaControlReceiver", "Broadcast received: ${intent.action}, extras: ${intent.extras}")

        // Forward the intent to the service
        val serviceIntent = Intent(context, MediaPlaybackService::class.java).apply {
            action = intent.action
            putExtras(intent.extras ?: return)
        }

        // Start the service to handle the action
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        Logger.d("MediaControlReceiver", "Forwarded to service: $serviceIntent")
    }
}