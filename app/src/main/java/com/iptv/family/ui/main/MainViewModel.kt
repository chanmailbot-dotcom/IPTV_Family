package com.iptv.family.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.family.data.repository.PlaylistRepository
import com.iptv.family.domain.model.Channel
import com.iptv.family.domain.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: PlaylistRepository,
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _categories = MutableStateFlow<List<com.iptv.family.domain.model.Category>>(emptyList())
    val categories: StateFlow<List<com.iptv.family.domain.model.Category>> = _categories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadPlaylists()
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getActivePlaylists().collect { list ->
                _playlists.value = list
                if (list.isNotEmpty() && _selectedPlaylist.value == null) {
                    selectPlaylist(list[0])
                }
                _isLoading.value = false
            }
        }
    }

    fun selectPlaylist(playlist: Playlist) {
        _selectedPlaylist.value = playlist
        loadChannels(playlist.id)
        loadCategories(playlist.id)
    }

    private fun loadChannels(playlistId: String) {
        viewModelScope.launch {
            repository.getChannelsByPlaylistId(playlistId).collect { channels ->
                _channels.value = channels
            }
        }
    }

    private fun loadCategories(playlistId: String) {
        viewModelScope.launch {
            repository.getCategoriesByPlaylistId(playlistId).collect { categories ->
                _categories.value = categories
            }
        }
    }

    fun addM3UPlaylist(name: String, url: String? = null, filePath: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val playlist = repository.addM3UPlaylist(name, url, filePath)
                // Load content if URL provided
                if (url != null) {
                    // TODO: Load from URL
                }
                loadPlaylists()
            } catch (e: Exception) {
                _error.value = e.message ?: "Error añadiendo lista"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addXtreamPlaylist(name: String, panelUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val playlist = repository.addXtreamPlaylist(name, panelUrl, username, password)
                repository.loadXtreamPlaylist(playlist)
                loadPlaylists()
            } catch (e: Exception) {
                _error.value = e.message ?: "Error conectando con Xtream"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getChannels(): StateFlow<List<Channel>> = _channels.asStateFlow()
    fun getCategories(): StateFlow<List<com.iptv.family.domain.model.Category>> = _categories.asStateFlow()
}