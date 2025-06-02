package com.example.radiostreamingapp.data

/**
 * Data class representing a radio station
 */
data class RadioStation(
    val id: Int,
    val name: String,
    var streamUrl: String,
    val iconResource: Int,
    val iconCachePath: String? = null,  // Nueva propiedad para iconos cacheados
    val metadata: Map<String, String>? = null  // Metadatos adicionales (categoría, tags, etc.)
)