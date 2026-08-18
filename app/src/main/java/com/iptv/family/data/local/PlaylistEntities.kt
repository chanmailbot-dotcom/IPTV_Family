package com.iptv.family.data.local

import androidx.room.*
import com.iptv.family.domain.model.Channel
import com.iptv.family.domain.model.Category
import com.iptv.family.domain.model.EPGProgram
import com.iptv.family.domain.model.Favorite
import com.iptv.family.domain.model.PlayHistory
import com.iptv.family.domain.model.Playlist
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class Converters {
    @TypeConverter
    fun stringToList(value: String?): List<String> {
        return value?.let { Json.Default.decodeFromString(it) } ?: emptyList()
    }

    @TypeConverter
    fun listToString(value: List<String>): String {
        return Json.Default.encodeToString(value)
    }

    @TypeConverter
    fun stringToMap(value: String?): Map<String, String> {
        return value?.let { Json.Default.decodeFromString(it) } ?: emptyMap()
    }

    @TypeConverter
    fun mapToString(value: Map<String, String>): String {
        return Json.Default.encodeToString(value)
    }

    @TypeConverter
    fun stringToXtreamConfig(value: String?): Playlist.XtreamConfig? {
        return value?.let { Json.Default.decodeFromString(it) }
    }

    @TypeConverter
    fun xtreamConfigToString(value: Playlist.XtreamConfig?): String? {
        return value?.let { Json.Default.encodeToString(it) }
    }

    @TypeConverter
    fun stringToHttpOptions(value: String?): Channel.HttpOptions? {
        return value?.let { Json.Default.decodeFromString(it) }
    }

    @TypeConverter
    fun httpOptionsToString(value: Channel.HttpOptions?): String? {
        return value?.let { Json.Default.encodeToString(it) }
    }

    @TypeConverter
    fun intToPlaylistSource(value: Int): Playlist.PlaylistSource {
        return Playlist.PlaylistSource.values()[value]
    }

    @TypeConverter
    fun playlistSourceToInt(value: Playlist.PlaylistSource): Int {
        return value.ordinal
    }

    @TypeConverter
    fun intToStreamFormat(value: Int): Channel.StreamFormat {
        return Channel.StreamFormat.values()[value]
    }

    @TypeConverter
    fun streamFormatToInt(value: Channel.StreamFormat): Int {
        return value.ordinal
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val source: Int, // PlaylistSource enum ordinal
    val url: String? = null,
    val filePath: String? = null,
    val xtreamConfigJson: String? = null, // Serialized XtreamConfig
    val lastUpdated: Long = System.currentTimeMillis(),
    val channelCount: Int = 0,
    val categoryCount: Int = 0,
    val isActive: Boolean = true,
) {
    fun toPlaylist(json: Json = Json.Default): Playlist {
        val source = Playlist.PlaylistSource.values()[source]
        val xtreamConfig = xtreamConfigJson?.let { json.decodeFromString<Playlist.XtreamConfig>(it) }
        return Playlist(
            id = id,
            name = name,
            source = source,
            url = url,
            filePath = filePath,
            xtreamConfig = xtreamConfig,
            lastUpdated = lastUpdated,
            channelCount = channelCount,
            categoryCount = categoryCount,
            isActive = isActive,
        )
    }

    companion object {
        fun fromPlaylist(playlist: Playlist, json: Json = Json.Default): PlaylistEntity {
            return PlaylistEntity(
                id = playlist.id,
                name = playlist.name,
                source = playlist.source.ordinal,
                url = playlist.url,
                filePath = playlist.filePath,
                xtreamConfigJson = playlist.xtreamConfig?.let { json.encodeToString(it) },
                lastUpdated = playlist.lastUpdated,
                channelCount = playlist.channelCount,
                categoryCount = playlist.categoryCount,
                isActive = playlist.isActive,
            )
        }
    }
}

@Entity(
    tableName = "channels",
    indices = [Index("playlistId"), Index("group"), Index("isFavorite")],
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
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
    val categoriesJson: String = "[]", // Serialized List<String>
    val streamFormat: Int = 0, // StreamFormat enum ordinal
    val headersJson: String = "{}", // Serialized Map<String, String>
    val userAgent: String? = null,
    val referrer: String? = null,
    val httpOptionsJson: String? = null,
    val isFavorite: Boolean = false,
) {
    fun toChannel(json: Json = Json.Default): Channel {
        return Channel(
            id = id,
            name = name,
            url = url,
            logo = logo,
            group = group,
            tvgId = tvgId,
            tvgName = tvgName,
            tvgLogo = tvgLogo,
            tvgShift = tvgShift,
            tvgCountry = tvgCountry,
            tvgLanguage = tvgLanguage,
            tvgUrl = tvgUrl,
            isRadio = isRadio,
            isLive = isLive,
            categories = json.decodeFromString(categoriesJson),
            streamFormat = Channel.StreamFormat.values()[streamFormat],
            headers = json.decodeFromString(headersJson),
            userAgent = userAgent,
            referrer = referrer,
            httpOptions = httpOptionsJson?.let { json.decodeFromString<Channel.HttpOptions>(it) },
        ).copy(isFavorite = isFavorite)
    }

    companion object {
        fun fromChannel(channel: Channel, playlistId: String, json: Json = Json.Default): ChannelEntity {
            return ChannelEntity(
                id = channel.id,
                playlistId = playlistId,
                name = channel.name,
                url = channel.url,
                logo = channel.logo,
                group = channel.group,
                tvgId = channel.tvgId,
                tvgName = channel.tvgName,
                tvgLogo = channel.tvgLogo,
                tvgShift = channel.tvgShift,
                tvgCountry = channel.tvgCountry,
                tvgLanguage = channel.tvgLanguage,
                tvgUrl = channel.tvgUrl,
                isRadio = channel.isRadio,
                isLive = channel.isLive,
                categoriesJson = json.encodeToString(channel.categories),
                streamFormat = channel.streamFormat.ordinal,
                headersJson = json.encodeToString(channel.headers),
                userAgent = channel.userAgent,
                referrer = channel.referrer,
                httpOptionsJson = channel.httpOptions?.let { json.encodeToString(it) },
                isFavorite = channel.isFavorite,
            )
        }
    }
}

@Entity(
    tableName = "categories",
    indices = [Index("playlistId")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val name: String,
    val icon: String? = null,
    val order: Int = 0,
    val channelIdsJson: String = "[]", // Serialized List<String>
    val isLiveTv: Boolean = true,
    val isVod: Boolean = false,
    val isSeries: Boolean = false,
) {
    fun toCategory(json: Json = Json.Default): Category {
        return Category(
            id = id,
            name = name,
            icon = icon,
            order = order,
            channelIds = json.decodeFromString(channelIdsJson),
            isLiveTv = isLiveTv,
            isVod = isVod,
            isSeries = isSeries,
        )
    }

    companion object {
        fun fromCategory(category: Category, playlistId: String, json: Json = Json.Default): CategoryEntity {
            return CategoryEntity(
                id = category.id,
                playlistId = playlistId,
                name = category.name,
                icon = category.icon,
                order = category.order,
                channelIdsJson = json.encodeToString(category.channelIds),
                isLiveTv = category.isLiveTv,
                isVod = category.isVod,
                isSeries = category.isSeries,
            )
        }
    }
}

@Entity(
    tableName = "epg_programs",
    primaryKeys = ["id"],
    indices = [Index("channelId"), Index("startTime"), Index("endTime")],
)
data class EPGProgramEntity(
    @ColumnInfo(name = "id") val id: String,
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long,
    val category: String? = null,
    val icon: String? = null,
    val rating: String? = null,
) {
    fun toEPGProgram(): EPGProgram {
        return EPGProgram(
            id = id,
            channelId = channelId,
            title = title,
            description = description,
            startTime = startTime,
            endTime = endTime,
            category = category,
            icon = icon,
            rating = rating,
        )
    }

    companion object {
        fun fromEPGProgram(program: EPGProgram): EPGProgramEntity {
            return EPGProgramEntity(
                id = program.id,
                channelId = program.channelId,
                title = program.title,
                description = program.description,
                startTime = program.startTime,
                endTime = program.endTime,
                category = program.category,
                icon = program.icon,
                rating = program.rating,
            )
        }
    }
}

@Entity(
    tableName = "favorites",
    primaryKeys = ["channelId"],
    indices = [Index("addedAt")],
)
data class FavoriteEntity(
    val channelId: String,
    val addedAt: Long = System.currentTimeMillis(),
) {
    fun toFavorite(): Favorite {
        return Favorite(channelId = channelId, addedAt = addedAt)
    }

    companion object {
        fun fromFavorite(favorite: Favorite): FavoriteEntity {
            return FavoriteEntity(channelId = favorite.channelId, addedAt = favorite.addedAt)
        }
    }
}

@Entity(
    tableName = "play_history",
    indices = [Index("channelId"), Index("playedAt")],
)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val channelId: String,
    val playedAt: Long = System.currentTimeMillis(),
    val duration: Long = 0,
) {
    fun toPlayHistory(): PlayHistory {
        return PlayHistory(channelId = channelId, playedAt = playedAt, duration = duration)
    }

    companion object {
        fun fromPlayHistory(history: PlayHistory): PlayHistoryEntity {
            return PlayHistoryEntity(
                channelId = history.channelId,
                playedAt = history.playedAt,
                duration = history.duration,
            )
        }
    }
}