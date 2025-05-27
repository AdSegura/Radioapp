package com.example.radiostreamingapp.sync.impl

import com.example.radiostreamingapp.sync.api.models.RemoteConfig
import com.example.radiostreamingapp.sync.api.models.RemoteRadioStation
import com.example.radiostreamingapp.utils.Logger
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.regex.Pattern

/**
 * Parser simplificado para extraer JSON de configuración desde GIST.
 * Mantiene el nombre original pero con lógica simplificada.
 */
class GitSnippetParser {

    private val gson = Gson()

    /**
     * Parsea un snippet de GIST a un objeto RemoteConfig
     * @param textContent El contenido del snippet en formato texto
     * @return El objeto RemoteConfig parseado o null si hay error
     */
    fun parseSnippet(textContent: String): RemoteConfig? {
        Logger.d("GitSnippetParser", "Iniciando parseo del snippet")

        // Extraer el JSON del texto (puede venir con metadata del GIST)
        val jsonContent = extractJsonFromText(textContent)
        if (jsonContent.isNullOrBlank()) {
            Logger.e("GitSnippetParser", "No se pudo extraer JSON válido del contenido")
            return null
        }

        // Parsear el JSON directamente
        return try {
            Logger.d("GitSnippetParser", "Parseando JSON extraído")
            val rawConfig = gson.fromJson(jsonContent, RawRadioConfig::class.java)
            val remoteConfig = convertToRemoteConfig(rawConfig)

            Logger.d("GitSnippetParser", "JSON parseado exitosamente con ${remoteConfig.stations.size} estaciones")
            remoteConfig.stations.forEachIndexed { index, station ->
                Logger.d("GitSnippetParser", "Estación ${index + 1}: ${station.name} - ${station.streamUrl}")
            }

            remoteConfig
        } catch (e: JsonSyntaxException) {
            Logger.e("GitSnippetParser", "Error de sintaxis JSON: ${e.message}")
            null
        } catch (e: Exception) {
            Logger.e("GitSnippetParser", "Error general al parsear JSON: ${e.message}")
            null
        }
    }

    /**
     * Convierte del formato JSON raw al RemoteConfig
     */
    private fun convertToRemoteConfig(rawConfig: RawRadioConfig): RemoteConfig {
        Logger.d("GitSnippetParser", "Convirtiendo configuración raw")

        val remoteStations = rawConfig.radioStations.mapIndexed { index, raw ->
            RemoteRadioStation(
                id = index + 1, // ID basado en posición
                name = raw.name,
                streamUrl = raw.streamUrl,
                iconUrl = raw.iconUrl,
                //iconType = raw.iconType ?: "URL",
                categoryId = raw.categoryId,
                tags = raw.tags ?: emptyList(),
                metadata = raw.metadata ?: emptyMap()
            )
        }

        return RemoteConfig(
            stations = remoteStations,
            version = rawConfig.version ?: 1,
            updatedAt = rawConfig.updatedAt ?: System.currentTimeMillis(),
            name = "Configuración Importada",
            description = "Configuración cargada desde GIST"
        )
    }

    /**
     * Extrae contenido JSON de un texto que puede contener metadata adicional
     */
    private fun extractJsonFromText(text: String): String? {
        Logger.d("GitSnippetParser", "Extrayendo JSON del texto (${text.length} caracteres)")

        // Si ya parece JSON limpio, devolverlo directamente
        val trimmedText = text.trim()
        if (looksLikeCleanJson(trimmedText)) {
            Logger.d("GitSnippetParser", "El texto ya parece JSON limpio")
            return trimmedText
        }

        // Buscar patrones de inicio de JSON
        val jsonObjectPattern = Pattern.compile("\\{\\s*\"")
        val matcher = jsonObjectPattern.matcher(text)

        if (matcher.find()) {
            val startIndex = matcher.start()
            Logger.d("GitSnippetParser", "Encontrado inicio de JSON en posición $startIndex")

            // Buscar el cierre del objeto JSON contando las llaves
            val closeIndex = findJsonEnd(text, startIndex, '{', '}')

            if (closeIndex > startIndex) {
                val extractedJson = text.substring(startIndex, closeIndex)
                Logger.d("GitSnippetParser", "JSON extraído (${extractedJson.length} caracteres)")
                return extractedJson
            }
        }

        Logger.w("GitSnippetParser", "No se pudo extraer JSON válido del texto")
        return null
    }

    /**
     * Encuentra el final de un bloque JSON contando caracteres de apertura y cierre
     */
    private fun findJsonEnd(text: String, startIndex: Int, openChar: Char, closeChar: Char): Int {
        var openCount = 0
        var inString = false
        var escapeNext = false

        for (i in startIndex until text.length) {
            val char = text[i]

            when {
                escapeNext -> {
                    escapeNext = false
                }
                char == '\\' && inString -> {
                    escapeNext = true
                }
                char == '"' && !escapeNext -> {
                    inString = !inString
                }
                !inString -> {
                    when (char) {
                        openChar -> openCount++
                        closeChar -> {
                            openCount--
                            if (openCount == 0) {
                                return i + 1
                            }
                        }
                    }
                }
            }
        }

        return -1
    }

    /**
     * Verifica si el texto parece JSON limpio
     */
    private fun looksLikeCleanJson(text: String): Boolean {
        return text.startsWith("{") && text.endsWith("}")
    }

    /**
     * Verifica si el texto contiene algún patrón JSON
     */
    fun looksLikeJson(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.contains("{") && trimmed.contains("}")
    }

    /**
     * Clase para parsear el JSON raw directamente
     */
    private data class RawRadioConfig(
        val radioStations: List<RawRadioStation>,
        val version: Int? = null,
        val updatedAt: Long? = null
    )

    private data class RawRadioStation(
        val name: String,
        val streamUrl: String,
        val iconUrl: String? = null,
        //val iconType: String? = null,
        val categoryId: Int? = null,
        val tags: List<String>? = null,
        val metadata: Map<String, String>? = null
    )
}