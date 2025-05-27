package com.example.radiostreamingapp

import com.example.radiostreamingapp.data.RadioStation

/**
 * Sealed class para manejar diferentes tipos de errores del reproductor
 */
sealed class PlayerError {
    data class StreamError(val station: RadioStation, val message: String): PlayerError()
    data class ConnectionError(val station: RadioStation): PlayerError()
    data class FormatError(val station: RadioStation): PlayerError()
    data object UnknownError: PlayerError()
}