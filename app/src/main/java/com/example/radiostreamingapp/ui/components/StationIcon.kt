package com.example.radiostreamingapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.radiostreamingapp.data.RadioStation
import com.example.radiostreamingapp.sync.impl.IconCacheManagerImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Composable que muestra el icono de una estación de radio.
 * Maneja automáticamente iconos de recursos locales e iconos descargados.
 */
@Composable
fun StationIcon(
    station: RadioStation,
    size: Dp = 48.dp,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var iconBitmap by remember(station.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember(station.id) { mutableStateOf(false) }
    var useResourceIcon by remember(station.id) { mutableStateOf(false) }

    // Cargar icono desde caché o descargar si es necesario
    LaunchedEffect(station.id) {
        val iconManager = IconCacheManagerImpl(context)
        isLoading = true

        try {
            val bitmap = withContext(Dispatchers.IO) {
                iconManager.getIconBitmap(station)
            }

            if (bitmap != null) {
                iconBitmap = bitmap
                useResourceIcon = false
            } else {
                // Si no hay bitmap, usar icono de recurso
                useResourceIcon = true
            }
        } catch (e: Exception) {
            // En caso de error, usar icono de recurso
            useResourceIcon = true
        } finally {
            isLoading = false
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                // Mostrar indicador de carga
                CircularProgressIndicator(
                    modifier = Modifier.size(size * 0.6f),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            iconBitmap != null && !useResourceIcon -> {
                // Mostrar imagen descargada
                Image(
                    bitmap = iconBitmap!!.asImageBitmap(),
                    contentDescription = contentDescription ?: station.name,
                    modifier = Modifier.size(size * 0.9f),
                    contentScale = ContentScale.Crop
                )
            }

            useResourceIcon && station.iconResource != 0 -> {
                // Mostrar icono de recurso local
                Image(
                    painter = painterResource(id = station.iconResource),
                    contentDescription = contentDescription ?: station.name,
                    modifier = Modifier.size(size * 0.8f),
                    contentScale = ContentScale.Fit
                )
            }

            else -> {
                // Icono por defecto cuando no hay nada más
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = contentDescription ?: station.name,
                    modifier = Modifier.size(size * 0.6f),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}