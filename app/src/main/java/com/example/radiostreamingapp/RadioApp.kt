package com.example.radiostreamingapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.example.radiostreamingapp.utils.Logger
/**
 * Clase Application personalizada para inicialización a nivel de aplicación
 */
@HiltAndroidApp
class RadioApp : Application() {

    companion object {
        private const val TAG = "RadioApp"
    }

    override fun onCreate() {
        super.onCreate()

        Logger.d(TAG, "Application onCreate")

        // Actualizar los widgets al iniciar la aplicación
        // para asegurar que reflejen el estado correcto
        RadioAppWidget.updateAllWidgets(this)
    }
}