package com.example.radiostreamingapp.sync.ui

//import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.radiostreamingapp.sync.api.models.ImportResult
import com.example.radiostreamingapp.sync.api.models.SyncState
import com.example.radiostreamingapp.util.ConfigHelper

/**
 * Diálogo para importar configuración desde URL
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    syncState: SyncState,
    importResult: ImportResult? = null
) {
    if (!isOpen) return

    val context = LocalContext.current
    val defaultUrl = remember { ConfigHelper.loadInitialImportUrl(context) }
    var url by remember { mutableStateOf(defaultUrl) }
    var showConfirmation by remember { mutableStateOf(false) }

    // Resetear estados cuando se abre el diálogo
    LaunchedEffect(isOpen) {
        if (isOpen) {
            url = defaultUrl // Usar la URL predefinida en lugar de vacío
            showConfirmation = false
        }
    }

    // Diálogo principal de entrada de URL
    AlertDialog(
        onDismissRequest = {
            if (syncState !is SyncState.Syncing) {
                onDismiss()
            }
        },
        title = { Text("Importar configuración") },
        text = {
            Column {
                // Descripción
                Text(
                    "Introduce la URL del archivo de configuración (snippet Git o JSON).",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Advertencia
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Esta acción reemplazará todas las emisoras actuales.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Campo de URL
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = syncState !is SyncState.Syncing,
                    singleLine = true,
                    placeholder = { Text("https://ejemplo.com/emisoras.json") },
                    supportingText = if (defaultUrl.isNotEmpty() && url == defaultUrl) {
                        { Text("URL predefinida desde configuración") }
                    } else null
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Estados de sincronización
                when (syncState) {
                    is SyncState.Syncing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = syncState.message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    is SyncState.Error -> {
                        Text(
                            text = "Error: ${syncState.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is SyncState.Completed -> {
                        // Solo mostrar completado si coincide con un resultado de importación exitoso
                        if (importResult?.success == true) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Importación completada. Se encontraron ${syncState.totalStations} emisoras.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    else -> { /* SyncState.Idle - no mostrar nada */ }
                }

                // Mostrar resultados de importación detallados
                importResult?.let { result ->
                    if (syncState is SyncState.Completed || syncState is SyncState.Idle) {
                        Spacer(modifier = Modifier.height(16.dp))

                        if (result.success) {
                            Column {
                                Text(
                                    text = "Importación exitosa",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Se importaron ${result.stationCount} emisoras.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (result.iconCount > 0) {
                                    Text(
                                        text = "${result.iconCount} emisoras tienen iconos que se descargarán en segundo plano.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        } else {
                            Column {
                                Text(
                                    text = "Error en la importación",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = result.errorMessage ?: "Error desconocido",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                syncState is SyncState.Syncing -> {
                    // No mostrar botones durante la sincronización
                }
                syncState is SyncState.Completed && importResult?.success == true -> {
                    // Mostrar solo botón cerrar cuando la importación sea exitosa
                    Button(
                        onClick = onDismiss
                    ) {
                        Text("Cerrar")
                    }
                }
                syncState is SyncState.Error -> {
                    // Permitir reintentar en caso de error
                    Button(
                        onClick = {
                            if (url.isNotEmpty()) {
                                showConfirmation = true
                            }
                        },
                        enabled = url.isNotEmpty()
                    ) {
                        Text("Reintentar")
                    }
                }
                else -> {
                    // Estado normal - mostrar botón importar
                    Button(
                        onClick = {
                            if (url.isNotEmpty()) {
                                showConfirmation = true
                            }
                        },
                        enabled = url.isNotEmpty()
                    ) {
                        Text("Importar")
                    }
                }
            }
        },
        dismissButton = {
            if (syncState !is SyncState.Syncing &&
                !(syncState is SyncState.Completed && importResult?.success == true)) {
                TextButton(
                    onClick = onDismiss
                ) {
                    Text("Cancelar")
                }
            }
        }
    )

    // Diálogo de confirmación
    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text("Confirmar importación") },
            text = {
                Text("Esta acción reemplazará todas las emisoras actuales por las de la nueva configuración. ¿Deseas continuar?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmation = false
                        onImport(url)
                    }
                ) {
                    Text("Continuar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmation = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}