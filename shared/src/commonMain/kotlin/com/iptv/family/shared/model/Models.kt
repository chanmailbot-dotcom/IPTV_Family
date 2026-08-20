package com.iptv.family.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val group: String? = null,
    val epgChannelId: String? = null,
    val isFavorite: Boolean = false,
    val categoryType: CategoryType = CategoryType.LIVE
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
    val enableChromecast: Boolean = false,
    val enableSubtitles: Boolean = true,
    val autoPlayNext: Boolean = true
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