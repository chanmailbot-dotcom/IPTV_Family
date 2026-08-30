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
    /**
     * Cuentas de acceso a la web (usuario + contraseña). El administrador las
     * gestiona desde Ajustes. Si esta vacia, la web pide crear la primera cuenta
     * de administrador antes de dejar entrar a nadie.
     */
    val webUsers: List<WebUser> = emptyList(),
    /**
     * Convertir a AAC el audio que el navegador no puede reproducir (AC-3, MP2...)
     * usando ffmpeg. Solo afecta a la web: el reproductor de escritorio decodifica
     * esos formatos sin problema.
     */
    /**
     * Dias que dura la sesion del navegador. Antes eran 30 fijos: demasiado para
     * un servidor que puede acabar expuesto a internet, y no configurable.
     */
    /**
     * Servir la web por HTTPS con un certificado autofirmado. Desactivado por
     * defecto: en la red local HTTP basta y el aviso del navegador molesta. Si
     * se va a exponer el puerto a internet, hay que activarlo -- sin TLS la
     * contraseña viaja legible.
     */
    val webServerHttps: Boolean = false,
    val webSessionDays: Int = 7,
    val transcodeAudioForWeb: Boolean = true,
    /** Ruta a ffmpeg si no esta en el PATH; null = buscarlo automaticamente. */
    val ffmpegPath: String? = null,
)

@Serializable
enum class ThemeType {
    LIGHT, DARK, SYSTEM
}

/**
 * Cuenta de acceso al servidor web.
 *
 * La contraseña NO se guarda: se guarda un hash PBKDF2 con su sal, para que un
 * volcado de `settings.json` (que viaja en copias de seguridad y esta en texto
 * plano en el disco) no revele las contraseñas de la familia.
 */
@Serializable
data class WebUser(
    val username: String,
    /** Hash PBKDF2-HMAC-SHA256 en Base64. */
    val passwordHash: String,
    /** Sal en Base64, distinta por usuario. */
    val salt: String,
    /** Iteraciones usadas al calcular el hash (se guarda para poder subirlas luego). */
    val iterations: Int = 120_000,
    val role: WebRole = WebRole.VIEWER,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class WebRole {
    /** Control total: cambiar canal, favoritos, reproductor y gestionar usuarios. */
    ADMIN,

    /** Solo ver lo que el administrador ha puesto. */
    VIEWER,
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