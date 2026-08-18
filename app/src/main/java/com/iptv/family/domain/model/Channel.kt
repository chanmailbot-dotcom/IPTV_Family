package com.iptv.family.domain.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Serializable
@Keep
data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val tvgLogo: String? = null,
    val tvgShift: String? = null,
    val tvgCountry: String? = null,
    val tvgLanguage: String? = null,
    val tvgUrl: String? = null,
    val isRadio: Boolean = false,
    val isLive: Boolean = true,
    val categories: List<String> = emptyList(),
    val streamFormat: StreamFormat = StreamFormat.AUTO,
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String? = null,
    val referrer: String? = null,
    val httpOptions: HttpOptions? = null,
) {
    @Serializable
    @Keep
    enum class StreamFormat {
        AUTO, HLS, DASH, MSS, MP4, M3U8, TS
    }

    @Serializable
    @Keep
    data class HttpOptions(
        val connectTimeout: Int = 10000,
        val readTimeout: Int = 30000,
        val followRedirects: Boolean = true,
        val allowCrossProtocolRedirects: Boolean = true,
    )

    // Helpers
    val displayName: String
        get() = tvgName?.takeIf { it.isNotBlank() } ?: name

    val displayLogo: String?
        get() = tvgLogo?.takeIf { it.isNotBlank() } ?: logo

    val isFavorite: Boolean = false // Se maneja en repository
}

@Serializable
@Keep
data class Category(
    val id: String,
    val name: String,
    val icon: String? = null,
    val order: Int = 0,
    val channelIds: List<String> = emptyList(),
    val isLiveTv: Boolean = true,
    val isVod: Boolean = false,
    val isSeries: Boolean = false,
)

@Serializable
@Keep
data class Playlist(
    val id: String,
    val name: String,
    val source: PlaylistSource,
    val url: String? = null,
    val filePath: String? = null,
    val xtreamConfig: XtreamConfig? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val channelCount: Int = 0,
    val categoryCount: Int = 0,
    val isActive: Boolean = true,
) {
    @Serializable
    @Keep
    enum class PlaylistSource {
        M3U_FILE, M3U_URL, XTREAM_CODES
    }

    @Serializable
    @Keep
    data class XtreamConfig(
        val panelUrl: String,
        val username: String,
        val password: String,
        val serverInfo: ServerInfo? = null,
    )

    @Serializable
    @Keep
    data class ServerInfo(
        val url: String,
        val port: String,
        val version: String,
        val timeZone: String,
        val timestampNow: Long,
        val timestampTimeZoneOffset: Int,
    )
}

@Serializable
@Keep
data class EPGProgram(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long,
    val category: String? = null,
    val icon: String? = null,
    val rating: String? = null,
    val isLiveNow: Boolean = false,
) {
    val durationMinutes: Int
        get() = ((endTime - startTime) / 1000 / 60).toInt()

    val isCurrentlyPlaying: Boolean
        get() = System.currentTimeMillis() >= startTime && System.currentTimeMillis() < endTime
}

@Serializable
@Keep
data class Favorite(
    val channelId: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Keep
data class PlayHistory(
    val channelId: String,
    val playedAt: Long = System.currentTimeMillis(),
    val duration: Long = 0,
)