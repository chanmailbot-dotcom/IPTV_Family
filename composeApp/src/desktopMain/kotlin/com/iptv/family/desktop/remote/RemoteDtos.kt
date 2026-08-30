package com.iptv.family.desktop.remote

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import com.iptv.family.shared.model.CategoryType

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
    /**
     * Tipo de lo que suena ("live", "vod" o "series"). La web mostraba la
     * insignia "En vivo" sobre cualquier cosa que estuviera reproduciendose,
     * peliculas incluidas, porque no tenia forma de distinguirlo.
     */
    val kind: String = KIND_LIVE,
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
    /**
     * Tipo de contenido: "live", "vod" o "series". Permite a la web ofrecer el
     * mismo filtro que la app de escritorio (TV en directo / Peliculas / Series).
     *
     * "live" es el valor por defecto y kotlinx no serializa los valores por
     * defecto, asi que los canales de TV no ocupan nada extra en el payload.
     */
    val kind: String = KIND_LIVE,
)

const val KIND_LIVE = "live"
const val KIND_VOD = "vod"
const val KIND_SERIES = "series"

/** Traduce el tipo de categoria del dominio al codigo corto que usa la web. */
fun kindOf(type: CategoryType): String = when (type) {
    CategoryType.LIVE -> KIND_LIVE
    CategoryType.VOD -> KIND_VOD
    CategoryType.SERIES -> KIND_SERIES
}

/** Grupo con su nombre ya legible, su tipo y cuantos canales tiene. */
@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val count: Int,
    /** Mismo juego de valores que [ChannelDto.kind]. */
    val kind: String = KIND_LIVE,
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
    /** Con quien esta identificado el navegador, para poder mostrarlo y cerrar sesion. */
    val username: String? = null,
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
data class LoginRequest(val username: String = "", val password: String = "")

@Serializable
data class CreateUserRequest(
    val username: String = "",
    val password: String = "",
    /** "admin" o "viewer"; cualquier otra cosa se trata como invitado. */
    val role: String = "viewer",
)

@Serializable
data class PasswordRequest(val password: String = "")

/** Usuario tal como se muestra al administrador: sin hash ni sal, obviamente. */
@Serializable
data class UserDto(
    val username: String,
    val role: String,
    val createdAt: Long,
)

@Serializable
data class SessionDto(val username: String, val role: String)

/**
 * Respuesta del login. Es un DTO y no un `mapOf(...)` a proposito: un mapa con
 * valores de tipos distintos (Boolean + String) se infiere como Map<String, Any>
 * y kotlinx.serialization no sabe serializar `Any`, asi que /login autenticaba
 * bien pero contestaba 500 al escribir la respuesta.
 */
@Serializable
data class LoginResponseDto(
    val ok: Boolean = true,
    val username: String,
    val role: String,
    /** Clave para SSE y <video>, que no pueden mandar cabeceras propias. */
    val streamKey: String,
)

/**
 * Lo unico que se responde sin estar identificado: si hace falta crear la
 * primera cuenta de administrador y, si ya hay sesion, de quien es.
 */
@Serializable
data class AuthInfoDto(
    val needsSetup: Boolean,
    val session: SessionDto? = null,
)

@Serializable
data class VolumeRequest(val volume: Int)

@Serializable
data class MuteRequest(val muted: Boolean)
