package com.iptv.family.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists WHERE isActive = 1")
    fun getActivePlaylists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun getPlaylist(id: Long): PlaylistEntity?

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND type = :type")
    fun getCategories(playlistId: Long, type: String): List<CategoryEntity>

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND categoryId = :categoryId ORDER BY name ASC")
    suspend fun getChannels(playlistId: Long, categoryId: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getChannel(id: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE isFavorite = 1 AND playlistId = :playlistId ORDER BY name ASC")
    suspend fun getFavorites(playlistId: Long): List<ChannelEntity>

    @Insert
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("UPDATE channels SET isFavorite = :isFav WHERE id = :channelId AND playlistId = :playlistId")
    suspend fun setFavorite(channelId: String, playlistId: Long, isFav: Boolean)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)
}

@Dao
interface EpgDao {
    @Query("SELECT * FROM epg_entries WHERE channelId = :channelId AND startTime >= :now ORDER BY startTime ASC LIMIT 50")
    suspend fun getEpgForChannel(channelId: String, now: Long): List<EpgEntryEntity>

    @Insert
    suspend fun insertAll(entries: List<EpgEntryEntity>)

    @Query("DELETE FROM epg_entries WHERE channelId = :channelId")
    suspend fun deleteByChannel(channelId: String)
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert
    suspend fun insert(setting: SettingEntity)

    @Update
    suspend fun update(setting: SettingEntity)
}