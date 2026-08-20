package com.iptv.family.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iptv.family.domain.model.ChannelType

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val type: String,        // "M3U" or "XTREAM"
    val username: String?,   // For Xtream
    val password: String?,   // For Xtream
    val isActive: Boolean = true
) {
    fun toDomain() = com.iptv.family.domain.model.Playlist(
        id = id,
        name = name,
        url = url,
        type = if (type == "XTREAM") com.iptv.family.domain.model.PlaylistType.XTREAM else com.iptv.family.domain.model.PlaylistType.M3U,
        username = username,
        password = password,
        isActive = isActive
    )
}

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,        // "LIVE_TV", "VOD", "SERIES"
    val playlistId: Long
) {
    fun toDomain() = com.iptv.family.domain.model.Category(
        id = id,
        name = name,
        type = ChannelType.valueOf(type)
    )
}

@Entity(
    tableName = "channels",
    indices = [androidx.room.Index("categoryId"), androidx.room.Index("isFavorite")]
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val logoUrl: String?,
    val streamUrl: String,
    val categoryId: String,
    val type: String,        // "LIVE_TV", "VOD", "SERIES"
    val epgId: String?,
    val duration: String?,
    val year: String?,
    val rating: String?,
    val isFavorite: Boolean = false,
    val playlistId: Long
) {
    fun toDomain(category: com.iptv.family.domain.model.Category) = com.iptv.family.domain.model.Channel(
        id = id,
        name = name,
        description = description,
        logoUrl = logoUrl,
        streamUrl = streamUrl,
        category = category,
        type = ChannelType.valueOf(type),
        epgId = epgId,
        duration = duration,
        year = year,
        rating = rating,
        isFavorite = isFavorite
    )
}

@Entity(
    tableName = "epg_entries",
    indices = [androidx.room.Index("channelId")]
)
data class EpgEntryEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val title: String,
    val description: String,
    val startTime: Long,
    val endTime: Long,
    val timezone: String?
) {
    fun toDomain() = com.iptv.family.domain.model.EpgEntry(
        id = id,
        channelId = channelId,
        title = title,
        description = description,
        startTime = startTime,
        endTime = endTime,
        timezone = timezone
    )
}

@Entity(
    tableName = "settings",
    indices = [androidx.room.Index("key")]
)
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)