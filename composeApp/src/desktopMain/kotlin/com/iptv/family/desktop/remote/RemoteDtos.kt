package com.iptv.family.desktop.remote

import com.iptv.family.shared.model.Category
import com.iptv.family.shared.model.Channel
import kotlinx.serialization.Serializable

/** DTOs de transporte HTTP del servidor remoto: no son modelo de dominio, viven aqui y no en `shared`. */

@Serializable
data class NowPlayingDto(
    val channelId: String? = null,
    val channelName: String? = null,
    val logoUrl: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val error: String? = null,
)

@Serializable
data class StateDto(
    val playlistName: String? = null,
    val categories: List<Category> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val favoriteChannelIds: List<String> = emptyList(),
    val nowPlaying: NowPlayingDto = NowPlayingDto(),
)

@Serializable
data class FavoriteRequest(val favorite: Boolean)

@Serializable
data class LoginRequest(val token: String)
