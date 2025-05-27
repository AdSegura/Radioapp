package com.example.radiostreamingapp.util

import android.content.Context
import org.json.JSONObject

object ConfigHelper {
    fun loadInitialImportUrl(context: Context): String {
        return try {
            val jsonString = context.assets.open("app_config.json")
                .bufferedReader()
                .use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            jsonObject.getString("initial_import_url")
        } catch (e: Exception) {
            // URL vacía si falla la lectura
            ""
        }
    }

    fun isLogsEnabled(context: Context): Boolean {
        return try {
            val jsonString = context.assets.open("app_config.json")
                .bufferedReader()
                .use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            jsonObject.getBoolean("enable_logs")
        } catch (e: Exception) {
            // Logs deshabilitados por defecto si falla la lectura
            false
        }
    }
}