package com.example.radiostreamingapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.radiostreamingapp.ui.theme.ThemeManager

/**
 * Menú de selección de tema que se muestra como un diálogo
 */
@Composable
fun ThemeSelectorDialog(
    onDismiss: () -> Unit
) {
    val currentThemeMode by ThemeManager.themeMode.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Seleccionar tema",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                ThemeOption(
                    title = "Seguir sistema",
                    subtitle = "Cambia según el tema del dispositivo",
                    icon = Icons.Default.Settings,
                    isSelected = currentThemeMode == ThemeManager.ThemeMode.FOLLOW_SYSTEM,
                    onClick = {
                        ThemeManager.setThemeMode(ThemeManager.ThemeMode.FOLLOW_SYSTEM)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                ThemeOption(
                    title = "Tema claro",
                    subtitle = "Siempre usar el tema claro",
                    icon = Icons.Default.LightMode,
                    isSelected = currentThemeMode == ThemeManager.ThemeMode.LIGHT,
                    onClick = {
                        ThemeManager.setThemeMode(ThemeManager.ThemeMode.LIGHT)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                ThemeOption(
                    title = "Tema oscuro",
                    subtitle = "Siempre usar el tema oscuro",
                    icon = Icons.Default.DarkMode,
                    isSelected = currentThemeMode == ThemeManager.ThemeMode.DARK,
                    onClick = {
                        ThemeManager.setThemeMode(ThemeManager.ThemeMode.DARK)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

/**
 * Opción individual de tema con radio button
 */
@Composable
private fun ThemeOption(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
        }
    }
}