package com.example.radiostreamingapp.utils

import android.util.Log
import com.example.radiostreamingapp.util.ConfigHelper

object Logger {

    // Guardamos el valor una sola vez al inicializar
    private var logsEnabled: Boolean = false

    fun initialize(context: android.content.Context) {
        // Leemos la configuración UNA VEZ y guardamos solo el boolean
        logsEnabled = ConfigHelper.isLogsEnabled(context)
        // No guardamos referencia al Context - evita memory leak
    }

    fun d(tag: String, message: String) {
        if (logsEnabled) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (logsEnabled) {
            Log.i(tag, message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (logsEnabled) {
            Log.w(tag, message, throwable)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable) // Errores siempre - sin cambios
    }
}