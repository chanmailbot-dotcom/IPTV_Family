package com.iptv.family.data.repository

import com.iptv.family.data.local.AppDatabase
import com.iptv.family.data.local.PlaylistEntities.*
import com.iptv.family.data.m3u.M3UParser
import com.iptv.family.data.xtream.XtreamApiClient
import com.iptv.family.data.xtream.XtreamApiClientFactory
import com.iptv.family.domain.model.Channel
import com.iptv.family.domain.model.Category
import com.iptv.family.domain.model.EPGProgram
import com.iptv.family.domain.model.Favorite
import com.iptv.family.domain.model.PlayHistory
import com.iptv.family.domain.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val database: AppDatabase,
    private val m3uParser: M3UParser,
    private val xtreamApiFactory: XtreamApiClientFactory,
) {

    // Playlist operations
    suspend fun getActivePlaylists(): Flow<List<Playlist>> {
        return database.playlistDao().getActivePlaylists()
            .map { it.map { it.toPlaylist() } }
    }

    suspend fun getAllPlaylists(): Flow<List<Playlist>> {
        return database.playlistDao().getAll()
            .map { it.map { it.toPlaylist() } }
    }

    suspend fun getPlaylistById(id: String): Playlist? {
        return database.playlistDao().getById(id)?.toPlaylist()
    }

    suspend fun addM3UPlaylist(name: String, url: String? = null, filePath: String? = null): Playlist {
        val playlist = Playlist(
            id = "pl_${System.currentTimeMillis()}",
            name = name,
            source = if (url != null) Playlist.PlaylistSource.M3U_URL else Playlist.PlaylistSource.M3U_FILE,
            url = url,
            filePath = filePath,
        )
        database.playlistDao().insert(PlaylistEntity.fromPlaylist(playlist))
        return playlist
    }

    suspend fun addXtreamPlaylist(
        name: String,
        panelUrl: String,
        username: String,
        password: String,
    ): Playlist {
        val playlist = Playlist(
            id = "pl_${System.currentTimeMillis()}",
            name = name,
            source = Playlist.PlaylistSource.XTREAM_CODES,
            xtreamConfig = Playlist.XtreamConfig(
                panelUrl = panelUrl,
                username = username,
                password = password,
            ),
        )
        database.playlistDao().insert(PlaylistEntity.fromPlaylist(playlist))
        return playlist
    }

    suspend fun updatePlaylist(playlist: Playlist) {
        database.playlistDao().update(PlaylistEntity.fromPlaylist(playlist))
    }

    suspend fun deletePlaylist(id: String) {
        database.playlistDao().deleteById(id)
        database.channelDao().deleteByPlaylistId(id)
        database.categoryDao().deleteByPlaylistId(id)
    }

    suspend fun setPlaylistActive(id: String, active: Boolean) {
        database.playlistDao().setActive(id, active)
    }

    // Channel operations
    fun getChannelsByPlaylistId(playlistId: String): Flow<List<Channel>> {
        return database.channelDao().getByPlaylistId(playlistId)
            .map { it.map { it.toChannel() } }
    }

    fun getFavoriteChannels(): Flow<List<Channel>> {
        return database.channelDao().getFavorites()
            .map { it.map { it.toChannel() } }
    }

    fun getChannelsByGroup(playlistId: String, groupName: String): Flow<List<Channel>> {
        return database.channelDao().getByGroup(playlistId, groupName)
            .map { it.map { it.toChannel() } }
    }

    fun searchChannels(query: String): Flow<List<Channel>> {
        return database.channelDao().search("%$query%")
            .map { it.map { it.toChannel() } }
    }

    suspend fun getChannelById(id: String): Channel? {
        return database.channelDao().getById(id)?.toChannel()
    }

    suspend fun setChannelFavorite(channelId: String, favorite: Boolean) {
        database.channelDao().setFavorite(channelId, favorite)
    }

    suspend fun loadM3UPlaylist(playlistId: String, inputStream: java.io.InputStream) {
        val result = withContext(Dispatchers.IO) {
            m3uParser.parse(inputStream)
        }

        // Save channels
        val channelEntities = result.channels.map { ChannelEntity.fromChannel(it, playlistId) }
        database.channelDao().insertAll(channelEntities)

        // Save categories
        val categoryEntities = result.categories.map { CategoryEntity.fromCategory(it, playlistId) }
        database.categoryDao().insertAll(categoryEntities)

        // Update playlist counts
        database.playlistDao().updateCounts(
            playlistId,
            result.channels.size,
            result.categories.size,
            System.currentTimeMillis(),
        )
    }

    suspend fun loadXtreamPlaylist(playlist: Playlist) {
        val xtreamConfig = playlist.xtreamConfig ?: return
        val client = xtreamApiFactory.create(
            xtreamConfig.panelUrl,
            xtreamConfig.username,
            xtreamConfig.password,
        )
        val result = client.loadAllContent()

        when (result) {
            is XtreamApiClient.XtreamLoadResult.Success -> {
                val (channels, categories) = client.toDomainModels(
                    playlist.id,
                    playlist.name,
                    result,
                )

                val channelEntities = channels.map { ChannelEntity.fromChannel(it, playlist.id) }
                database.channelDao().insertAll(channelEntities)

                val categoryEntities = categories.map { CategoryEntity.fromCategory(it, playlist.id) }
                database.categoryDao().insertAll(categoryEntities)

                val updatedPlaylist = playlist.copy(
                    channelCount = channels.size,
                    categoryCount = categories.size,
                    lastUpdated = System.currentTimeMillis(),
                )
                database.playlistDao().update(PlaylistEntity.fromPlaylist(updatedPlaylist))
            }
            is XtreamApiClient.XtreamLoadResult.Failure -> {
                throw Exception(result.message)
            }
        }
    }

    // Category operations
    fun getCategoriesByPlaylistId(playlistId: String): Flow<List<Category>> {
        return database.categoryDao().getByPlaylistId(playlistId)
            .map { it.map { it.toCategory() } }
    }

    fun getLiveTvCategories(playlistId: String): Flow<List<Category>> {
        return database.categoryDao().getLiveTvCategories(playlistId)
            .map { it.map { it.toCategory() } }
    }

    fun getVodCategories(playlistId: String): Flow<List<Category>> {
        return database.categoryDao().getVodCategories(playlistId)
            .map { it.map { it.toCategory() } }
    }

    fun getSeriesCategories(playlistId: String): Flow<List<Category>> {
        return database.categoryDao().getSeriesCategories(playlistId)
            .map { it.map { it.toCategory() } }
    }

    // EPG operations
    suspend fun saveEPGPrograms(programs: List<EPGProgram>) {
        val entities = programs.map { EPGProgramEntity.fromEPGProgram(it) }
        database.epgDao().insertAll(entities)
    }

    fun getEPGForChannel(channelId: String, from: Long, to: Long): Flow<List<EPGProgram>> {
        return database.epgDao().getForChannel(channelId, from, to)
            .map { it.map { it.toEPGProgram() } }
    }

    suspend fun getCurrentProgram(channelId: String): EPGProgram? {
        return database.epgDao().getCurrentProgram(channelId, System.currentTimeMillis())?.toEPGProgram()
    }

    suspend fun getNextProgram(channelId: String): EPGProgram? {
        return database.epgDao().getNextProgram(channelId, System.currentTimeMillis())?.toEPGProgram()
    }

    suspend fun cleanupExpiredEPG() {
        database.epgDao().deleteExpired(System.currentTimeMillis())
    }

    // Favorite operations
    fun getFavorites(): Flow<List<Favorite>> {
        return database.favoriteDao().getAll()
            .map { it.map { it.toFavorite() } }
    }

    suspend fun isFavorite(channelId: String): Boolean {
        return database.favoriteDao().isFavorite(channelId)
    }

    suspend fun addFavorite(channelId: String) {
        database.favoriteDao().insert(FavoriteEntity.fromFavorite(Favorite(channelId)))
        database.channelDao().setFavorite(channelId, true)
    }

    suspend fun removeFavorite(channelId: String) {
        database.favoriteDao().deleteByChannelId(channelId)
        database.channelDao().setFavorite(channelId, false)
    }

    // Play history operations
    fun getRecentHistory(): Flow<List<PlayHistory>> {
        return database.playHistoryDao().getRecent()
            .map { it.map { it.toPlayHistory() } }
    }

    fun getHistoryForChannel(channelId: String): Flow<List<PlayHistory>> {
        return database.playHistoryDao().getForChannel(channelId)
            .map { it.map { it.toPlayHistory() } }
    }

    suspend fun addToHistory(channelId: String, duration: Long = 0) {
        database.playHistoryDao().insert(PlayHistoryEntity.fromPlayHistory(PlayHistory(channelId, duration = duration)))
    }

    suspend fun cleanupOldHistory() {
        val cutoff = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000) // 30 days
        database.playHistoryDao().deleteOlderThan(cutoff)
    }
}