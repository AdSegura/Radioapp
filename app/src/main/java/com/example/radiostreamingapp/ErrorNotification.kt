package com.example.radiostreamingapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.radiostreamingapp.data.RadioStation
import com.example.radiostreamingapp.PlayerError

@Composable
fun ErrorNotification(
    error: Any?,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onReset: ((Int) -> Unit)? = null
) {
    AnimatedVisibility(
        visible = error != null,
        enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
    ) {
        if (error != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = getErrorBackgroundColor(error)
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 8.dp
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = getErrorIcon(error),
                        contentDescription = "Error Icon",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = getErrorTitle(error),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = getErrorMessage(error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss Error",
                            tint = Color.White
                        )
                    }
                }

                // Only show action buttons for specific errors
                if (error is PlayerError.ConnectionError ||
                    error is PlayerError.FormatError ||
                    error is PlayerError.StreamError) {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.1f))
                            .padding(8.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Row {
                            if (onRetry != null) {
                                TextButton(
                                    onClick = onRetry
                                ) {
                                    Text(
                                        text = "Retry",
                                        color = Color.White
                                    )
                                }
                            }

                            if (onReset != null) {
                                val stationId = when (error) {
                                    is PlayerError.ConnectionError -> error.station.id
                                    is PlayerError.FormatError -> error.station.id
                                    is PlayerError.StreamError -> error.station.id
                                    else -> null
                                }

                                if (stationId != null) {
                                    TextButton(
                                        onClick = { onReset(stationId) }
                                    ) {
                                        Text(
                                            text = "Reset URL",
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getErrorIcon(error: Any?): ImageVector {
    return when (error) {
        is PlayerError.ConnectionError -> Icons.Filled.WifiOff
        is PlayerError.FormatError -> Icons.Filled.Warning
        is PlayerError.StreamError -> Icons.Filled.Error
        else -> Icons.Filled.Error
    }
}

private fun getErrorBackgroundColor(error: Any?): Color {
    return when (error) {
        is PlayerError.ConnectionError -> Color(0xFFE57373) // Light Red
        is PlayerError.FormatError -> Color(0xFFFFB74D)     // Light Orange
        is PlayerError.StreamError -> Color(0xFFFF8A65)     // Light Deep Orange
        else -> Color(0xFF9575CD)    // Light Purple
    }
}

private fun getErrorTitle(error: Any?): String {
    return when (error) {
        is PlayerError.ConnectionError -> "Connection Error"
        is PlayerError.FormatError -> "Format Error"
        is PlayerError.StreamError -> "Stream Error"
        else -> "Unknown Error"
    }
}

private fun getErrorMessage(error: Any?): String {
    return when (error) {
        is PlayerError.ConnectionError ->
            "Could not connect to \"${error.station.name}\". Check your internet connection."
        is PlayerError.FormatError ->
            "The format of \"${error.station.name}\" is not supported or the station is unavailable."
        is PlayerError.StreamError ->
            "Error playing \"${error.station.name}\": ${error.message}"
        else ->
            "An unknown error occurred while playing the station."
    }
}