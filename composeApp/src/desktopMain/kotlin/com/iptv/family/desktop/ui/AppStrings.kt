package com.iptv.family.desktop.ui

import com.iptv.family.shared.i18n.T

/**
 * Textos de la interfaz de escritorio.
 *
 * Es una fachada sobre el catalogo de `shared` ([com.iptv.family.shared.i18n.Textos]),
 * que es donde estan las traducciones. Antes esto eran `const val` en castellano:
 * servia para no repetir literales entre pantallas, pero una constante se resuelve
 * al compilar, asi que traducir era imposible por definicion.
 *
 * Se mantiene el nombre y la forma para no tocar los sesenta sitios que ya lo
 * usan; lo unico que cambia es de donde sale el texto.
 */
object AppStrings {
    const val APP_TITLE = "IPTV Family"
    const val WINDOW_TITLE_SUFFIX = "· IPTV Family"

    val CANCEL: String get() = T.cancelar
    val RETRY: String get() = T.reintentar

    object Nav {
        val HOME: String get() = T.misListas
        val CHANNELS: String get() = T.canales
        val FAVORITES: String get() = T.favoritos
        val PLAYER: String get() = T.reproduciendo
        val SETTINGS: String get() = T.ajustes
    }

    object Home {
        val TITLE: String get() = T.misListas
        val SUBTITLE: String get() = T.listasSubtitulo
        val ADD: String get() = T.añadirLista
        val ADD_FIRST: String get() = T.añadirPrimeraLista
        val VIEW_CHANNELS: String get() = T.verCanales
        val REFRESH: String get() = T.actualizarLista
        val DELETE: String get() = T.eliminar
        val DELETE_TITLE: String get() = T.eliminarLista
        val EMPTY_TITLE: String get() = T.sinListasTitulo
        val EMPTY_BODY: String get() = T.sinListasCuerpo
        fun deleteConfirm(name: String) = T.confirmarBorrado(name)
    }
}
