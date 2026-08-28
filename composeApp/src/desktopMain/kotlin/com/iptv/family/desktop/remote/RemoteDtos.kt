package com.iptv.family.desktop.remote

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** DTOs de transporte HTTP del servidor remoto: no son modelo de dominio, viven aqui y no en `shared`. */

@Serializable
data class NowPlayingDto(
    val channelId: String? = null,
    val channelName: String? = null,
    val channelNumber: Int? = null,
    val logoUrl: String? = null,
    /** Nombre legible del grupo, ya resuelto (en Xtream `Channel.group` es un id numerico). */
    val group: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val error: String? = null,
    val volume: Int = 0,
    val isMuted: Boolean = false,
    val epgNow: String? = null,
    val epgNext: String? = null,
    val epgEndsAt: Long? = null,
)

/**
 * Canal tal como lo ve el navegador.
 *
 * Deliberadamente SIN `url`: la URL real del panel lleva el usuario y la
 * contraseña de Xtream incrustados en la ruta
 * (`/live/usuario/clave/123.m3u8`). El navegador no la necesita para nada --
 * reproduce siempre a traves de `/stream/current.m3u8`, que resuelve la URL en
 * el escritorio -- y mandarla filtraria las credenciales del panel a cualquiera
 * que abra la web (incluidos los invitados). De paso, quitarla recorta el JSON
 * de estado a menos de la mitad con listas de decenas de miles de canales.
 */
@Serializable
data class ChannelDto(
    val id: String,
    val name: String,
    val number: Int? = null,
    val logoUrl: String? = null,
    /** ID del grupo; el nombre legible se resuelve contra [StateDto.groups]. */
    val group: String? = null,
)

/** Grupo con su nombre ya legible y cuantos canales tiene. */
@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val count: Int,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class StateDto(
    val playlistName: String? = null,
    /**
     * "admin" o "viewer": la web se adapta (un invitado no ve controles ni lista).
     *
     * @EncodeDefault porque kotlinx.serialization omite los campos que valen su
     * valor por defecto: sin esto, la respuesta a un invitado no llevaba `role`
     * y el cliente tenia que deducir el rol de su ausencia.
     */
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val role: String = "viewer",
    /**
     * Grupos con nombre legible. Antes se enviaban objetos `Category` completos,
     * que incluyen la lista de IDs de todos sus canales -- con la categoria
     * "Todas" eso duplicaba los 40.000 ids en cada respuesta.
     */
    val groups: List<GroupDto> = emptyList(),
    /** Vacia para los invitados: solo ven el canal que ha puesto el administrador. */
    val channels: List<ChannelDto> = emptyList(),
    val favoriteChannelIds: List<String> = emptyList(),
    val nowPlaying: NowPlayingDto = NowPlayingDto(),
)

@Serializable
data class FavoriteRequest(val favorite: Boolean)

@Serializable
data class LoginRequest(val token: String)

@Serializable
data class VolumeRequest(val volume: Int)

@Serializable
data class MuteRequest(val muted: Boolean)
