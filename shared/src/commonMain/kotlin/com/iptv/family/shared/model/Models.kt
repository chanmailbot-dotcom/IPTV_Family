package com.iptv.family.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    /**
     * ID de la categoria a la que pertenece (casa con [Category.id]). En Xtream
     * es un numero ("142"), NO el nombre legible: para mostrarlo al usuario hay
     * que resolverlo contra la lista de [Category] (ver `Category.name`).
     */
    val group: String? = null,
    val epgChannelId: String? = null,
    val isFavorite: Boolean = false,
    val categoryType: CategoryType = CategoryType.LIVE,
    /**
     * Numero de canal que publica el proveedor (`num` en Xtream, `tvg-chno` en
     * M3U). Es el orden que el usuario espera ver -- sin esto la lista sale en
     * el orden crudo de la respuesta del panel.
     */
    val number: Int? = null
)

@Serializable
enum class CategoryType {
    LIVE, VOD, SERIES
}

@Serializable
data class Category(
    val id: String,
    val name: String,
    val type: CategoryType,
    val channels: List<String> = emptyList()
)

@Serializable
data class EPGProgram(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long,
    val category: String? = null,
    val iconUrl: String? = null
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val type: SourceType,
    val m3uUrl: String? = null,
    val epgUrl: String? = null,
    val xtreamUrl: String? = null,
    val xtreamUser: String? = null,
    val xtreamPass: String? = null,
    val isActive: Boolean = false,
    val lastUpdated: Long = 0L
)

@Serializable
enum class SourceType {
    M3U_URL, M3U_FILE, XTREAM
}

@Serializable
data class UserSettings(
    val selectedPlaylistId: String? = null,
    val isParentalLockEnabled: Boolean = false,
    val parentalPin: String? = null,
    val adultCategoryNames: List<String> = listOf("adult", "18+", "xxx"),
    val selectedTheme: ThemeType = ThemeType.DARK,
    val locale: String = "es",
    val bufferMs: Int = 15000,
    val enableHardwareDecoding: Boolean = true,
    val videoCompatibilityMode: Boolean = false,
    val enableChromecast: Boolean = false,
    val enableSubtitles: Boolean = true,
    val autoPlayNext: Boolean = true,
    val enableWebServer: Boolean = false,
    val webServerPort: Int = 8080,
    /** Token de ADMINISTRADOR: control total (cambiar canal, favoritos, volumen...). */
    val webServerToken: String? = null,
    /**
     * Token de INVITADO: solo ver lo que el administrador ha puesto. No puede
     * cambiar de canal ni tocar el reproductor; si es null, no hay acceso de
     * invitado y solo entra quien tenga el token de administrador.
     */
    val webViewerToken: String? = null
)

@Serializable
enum class ThemeType {
    LIGHT, DARK, SYSTEM
}

@Serializable
data class FavoriteChannel(
    val channelId: String,
    val playlistId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Serializable
data class PlaybackHistory(
    val channelId: String,
    val playlistId: String,
    val lastPlayedAt: Long,
    val durationSeconds: Long = 0L
)