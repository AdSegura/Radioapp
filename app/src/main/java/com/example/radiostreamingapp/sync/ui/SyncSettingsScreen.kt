package com.example.radiostreamingapp.sync.ui

import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.radiostreamingapp.sync.api.models.IconDownloadState
import com.example.radiostreamingapp.sync.api.models.SyncState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.ExperimentalMaterial3Api

/**
 * Pantalla de ajustes de sincronización
 */
@Composable
fun SyncSettingsScreen(
    viewModel: SyncViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onStationsUpdated: (() -> Unit)? = null
) {
    // Estados
    val syncState by viewModel.syncState.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val syncInfo by viewModel.syncInfo.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val syncResult by viewModel.syncResult.collectAsState()
    val iconCacheSize by viewModel.iconCacheSize.collectAsState()

    // Estados locales
    var showImportDialog by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    // Efecto para actualizar información periódicamente
    LaunchedEffect(key1 = Unit) {
        viewModel.refreshSyncInfo()
        viewModel.refreshIconCacheSize()
    }

    // Diálogo de importación
    // CORRECTO - el callback se pasa al ViewModel
    if (showImportDialog) {
        ImportDialog(
            isOpen = true,
            onDismiss = { showImportDialog = false },
            onImport = { url ->
                viewModel.importFromUrl(url) {
                    // Este callback se ejecuta cuando la importación termina exitosamente
                    onStationsUpdated?.invoke()
                }
            },
            syncState = syncState,
            importResult = importResult
        )
    }
    @OptIn(ExperimentalMaterial3Api::class)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración de Emisoras") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sección de información actual
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Información de Configuración",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Divider()

                    if (syncInfo?.hasImportedConfig == true) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = " Usando configuración importada",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Text(
                            text = "Número de emisoras: ${syncInfo?.stationCount ?: 0}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = "Versión de configuración: ${syncInfo?.configVersion ?: 1}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = "URL de origen: ${syncInfo?.sourceUrl ?: "No disponible"}",
                            style = MaterialTheme.typography.bodySmall
                        )

                        syncInfo?.lastSyncTime?.let { lastSync ->
                            Text(
                                text = "Última sincronización: ${formatDate(lastSync)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Text(
                            text = "Sincronizaciones realizadas: ${syncInfo?.syncCount ?: 0}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = " Usando configuración predeterminada",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Text(
                            text = "No hay configuración importada.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Estado de sincronización
            when (syncState) {
                is SyncState.Syncing -> {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = (syncState as SyncState.Syncing).message,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = (syncState as SyncState.Syncing).progress,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                is SyncState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = " Error de sincronización",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = (syncState as SyncState.Error).message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                else -> {
                    // No mostrar nada para Idle o Completed
                }
            }

            // Estado de descarga de iconos
            when (val state = downloadState) {
                is IconDownloadState.Downloading -> {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Descargando iconos (${state.completed}/${state.total})",
                                style = MaterialTheme.typography.titleSmall
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = state.completed.toFloat() / state.total,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            state.currentStation?.let {
                                Text(
                                    text = "Procesando: $it",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = { viewModel.cancelDownloads() }
                            ) {
                                Text("Cancelar descargas")
                            }
                        }
                    }
                }
                is IconDownloadState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = " Error descargando iconos",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            state.failedStation?.let {
                                Text(
                                    text = "Emisora: $it",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                else -> {
                    // No mostrar nada para Idle o Completed
                }
            }

            // Caché de iconos
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Caché de Iconos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Divider()

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Tamaño actual: ${formatBytes(iconCacheSize)}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.clearIconCache() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null
                            )
                            Text(" Limpiar caché")
                        }

                        OutlinedButton(
                            onClick = { viewModel.refreshIconCacheSize() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Actualizar")
                        }
                    }
                }
            }

            // Acciones principales
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Acciones",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Divider()

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.clearImportResult() // Limpiar estado anterior
                            showImportDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null
                        )
                        Text(" Importar configuración")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.synchronize{onStationsUpdated?.invoke()} },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = syncInfo?.hasImportedConfig == true && syncState !is SyncState.Syncing
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null
                        )
                        Text(" Sincronizar ahora")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (syncInfo?.hasImportedConfig == true) {
                        OutlinedButton(
                            onClick = { showResetConfirmation = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = " Restablecer configuración",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    // Diálogo de confirmación para restablecer
    if (showResetConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("¿Restablecer configuración?") },
            text = {
                Text("Esta acción eliminará la configuración importada y volverá a la configuración predeterminada de la aplicación. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetToDefaultConfig()
                        showResetConfirmation = false
                    }
                ) {
                    Text("Restablecer")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirmation = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/**
 * Formatea una fecha
 */
private fun formatDate(date: Date): String {
    val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return format.format(date)
}

/**
 * Formatea bytes a una representación legible
 */
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.2f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.2f MB", mb)
}