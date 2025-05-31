package com.example.radiostreamingapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.radiostreamingapp.data.RadioStation
import com.example.radiostreamingapp.ui.components.ThemeSelectorDialog
import com.example.radiostreamingapp.ui.theme.RadioStreamingAppTheme
import androidx.compose.material.icons.filled.Settings
import com.example.radiostreamingapp.sync.ui.SyncSettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import com.example.radiostreamingapp.ui.components.StationIcon
import com.example.radiostreamingapp.utils.Logger
import com.example.radiostreamingapp.ui.components.RadioStationGridCard

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: RadioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inicializar Logger al inicio
        Logger.initialize(this)
        Logger.d("APP_START", "arrancando la APP")
        setContent {
            RadioStreamingAppTheme {
                RadioStreamingApp(viewModel)
            }
        }
    }
}

// Main App Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioStreamingApp(viewModel: RadioViewModel) {

    val radioStations by viewModel.radioStations.collectAsState()
    val currentStation by viewModel.currentStation.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    // Estado para controlar la visibilidad del selector de tema
    val showThemeSelector = remember { mutableStateOf(false) }
    val showSyncSettings = remember { mutableStateOf(false) }

    // Mostrar el selector de tema si showThemeSelector es true
    if (showThemeSelector.value) {
        ThemeSelectorDialog(
            onDismiss = { showThemeSelector.value = false }
        )
    }

    if (showSyncSettings.value) {
        SyncSettingsScreen(
            onNavigateBack = { showSyncSettings.value = false },
            onStationsUpdated = {
                // Recargar estaciones cuando se actualicen
                viewModel.reloadStations()
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Radio Streaming", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    // Botón existente para cambiar tema
                    IconButton(onClick = { showThemeSelector.value = true }) {
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = "Cambiar tema",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    // Nuevo botón para sincronización
                    IconButton(onClick = { showSyncSettings.value = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configuración de sincronización",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (currentStation != null) {
                PlayerBar(
                    station = currentStation!!,
                    isPlaying = isPlaying,
                    onPlayPause = {
                        if (isPlaying) {
                            viewModel.pausePlayback()
                        } else {
                            viewModel.resumePlayback()
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Mostrar error de carga si es necesario
            val errorState by viewModel.errorState.collectAsState()
            ErrorNotification(
                error = errorState,
                onDismiss = { viewModel.clearError() },
                onRetry = {
                    val err = errorState
                    if (err is PlayerError.ConnectionError) {
                        viewModel.playStation(err.station)
                    } else if (err is PlayerError.FormatError) {
                        viewModel.playStation(err.station)
                    } else if (err is PlayerError.StreamError) {
                        viewModel.playStation(err.station)
                    }
                },
                onReset = { stationId ->
                    viewModel.resetUrlToDefault(stationId)
                    viewModel.clearError()
                }
            )

            // Contenido principal con grid o mensaje vacío
            if (radioStations.isEmpty()) {
                // Mensaje cuando no hay estaciones
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No hay emisoras configuradas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Importa una configuración desde el menú de ajustes",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // Grid de estaciones de radio
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(radioStations) { station ->
                        RadioStationGridCard(
                            station = station,
                            isCurrentlyPlaying = currentStation?.id == station.id && isPlaying,
                            onStationClick = { viewModel.playStation(station) },
                            onEditUrl = { newUrl ->
                                viewModel.updateStationUrl(station.id, newUrl)
                            }
                        )
                    }
                }
            }
        }
    }
}



// Edit URL Dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditUrlDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Stream URL") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Stream URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Player Bar
@Composable
fun PlayerBar(
    station: RadioStation,
    isPlaying: Boolean,
    onPlayPause: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Station Icon
            StationIcon(
                station = station,
                size = 48.dp,
                contentDescription = station.name
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Station Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = if (isPlaying) "Playing" else "Paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            // Play/Pause Button
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}