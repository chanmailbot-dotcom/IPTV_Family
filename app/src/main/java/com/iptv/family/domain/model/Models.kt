package com.iptv.family.domain.model

import java.io.Serializable

/**
 * Tipo de contenido de un canal
 */
enum class ChannelType : Serializable {
    LIVE_TV,    // Televisión en vivo
    VOD,        // Video bajo demanda (peliculas/series)
    SERIES      // Series con temporadas/episodios
}

/**
 * Representa una categoría de canales (Live TV, VOD, Series, etc.)
 */
data class Category(
    val id: String,
    val name: String,
    val type: ChannelType
)

/**
 * Representa un canal o contenido de streaming
 */
data class Channel(
    val id: String,
    val name: String,
    val description: String = "",
    val logoUrl: String? = null,
    val streamUrl: String,
    val category: Category,
    val type: ChannelType,
    val epgId: String? = null,
    val duration: String? = null,        // Duración para VOD
    val year: String? = null,            // Año de lanzamiento para VOD
    val rating: String? = null,          // Calificación
    val isFavorite: Boolean = false
)

/**
 * Programa electrónico (EPG)
 */
data class EpgEntry(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String,
    val startTime: Long,       // Epoch millis
    val endTime: Long,         // Epoch millis
    val timezone: String? = null
)

/**
 * Playlist de canales
 */
data class Playlist(
    val id: Long = 0,
    val name: String,
    val url: String,
    val type: PlaylistType,
    val username: String? = null,    // Para Xtream
    val password: String? = null,    // Para Xtream
    val isActive: Boolean = true
)

enum class PlaylistType {
    M3U,
    XTREAM
}