package com.iptv.family.data.local

import androidx.room.*
import com.iptv.family.domain.model.Channel
import com.iptv.family.domain.model.Category
import com.iptv.family.domain.model.EPGProgram
import com.iptv.family.domain.model.Favorite
import com.iptv.family.domain.model.Playlist
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity)

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Delete
    suspend fun delete(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM playlists WHERE isActive = 1 ORDER BY lastUpdated DESC")
    fun getActivePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists")
    fun getAll(): Flow<List<PlaylistEntity>>

    @Query("UPDATE playlists SET channelCount = :count, categoryCount = :catCount, lastUpdated = :now WHERE id = :id")
    suspend fun updateCounts(id: String, count: Int, catCount: Int, now: Long)

    @Query("UPDATE playlists SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)
}

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(channel: ChannelEntity)

    @Update
    suspend fun update(channel: ChannelEntity)

    @Delete
    suspend fun delete(channel: ChannelEntity)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylistId(playlistId: String)

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY name ASC")
    fun getByPlaylistId(playlistId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getById(id: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavorites(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE \"group\" = :groupName AND playlistId = :playlistId ORDER BY name ASC")
    fun getByGroup(playlistId: String, groupName: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE name LIKE :query ORDER BY name ASC LIMIT 50")
    fun search(query: String): Flow<List<ChannelEntity>>

    @Query("UPDATE channels SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    suspend fun countByPlaylistId(playlistId: String): Int
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylistId(playlistId: String)

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId ORDER BY \"order\" ASC")
    fun getByPlaylistId(playlistId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND isLiveTv = 1 ORDER BY \"order\" ASC")
    fun getLiveTvCategories(playlistId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND isVod = 1 ORDER BY \"order\" ASC")
    fun getVodCategories(playlistId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND isSeries = 1 ORDER BY \"order\" ASC")
    fun getSeriesCategories(playlistId: String): Flow<List<CategoryEntity>>
}

@Dao
interface EPGDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programs: List<EPGProgramEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(program: EPGProgramEntity)

    @Query("DELETE FROM epg_programs WHERE channelId = :channelId")
    suspend fun deleteByChannelId(channelId: String)

    @Query("DELETE FROM epg_programs WHERE endTime < :now")
    suspend fun deleteExpired(now: Long)

    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND startTime >= :from AND startTime <= :to ORDER BY startTime ASC")
    fun getForChannel(channelId: String, from: Long, to: Long): Flow<List<EPGProgramEntity>>

    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND startTime <= :now AND endTime > :now ORDER BY startTime ASC LIMIT 1")
    suspend fun getCurrentProgram(channelId: String, now: Long): EPGProgramEntity?

    @Query("SELECT * FROM epg_programs WHERE channelId = :channelId AND startTime > :now ORDER BY startTime ASC LIMIT 1")
    suspend fun getNextProgram(channelId: String, now: Long): EPGProgramEntity?
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Delete
    suspend fun delete(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE channelId = :channelId")
    suspend fun deleteByChannelId(channelId: String)

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE channelId = :channelId")
    suspend fun getByChannelId(channelId: String): FavoriteEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE channelId = :channelId)")
    suspend fun isFavorite(channelId: String): Boolean
}

@Dao
interface PlayHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: PlayHistoryEntity)

    @Query("DELETE FROM play_history WHERE channelId = :channelId")
    suspend fun deleteByChannelId(channelId: String)

    @Query("DELETE FROM play_history WHERE playedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT 50")
    fun getRecent(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history WHERE channelId = :channelId ORDER BY playedAt DESC LIMIT 10")
    fun getForChannel(channelId: String): Flow<List<PlayHistoryEntity>>
}