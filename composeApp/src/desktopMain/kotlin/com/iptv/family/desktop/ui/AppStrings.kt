package com.iptv.family.desktop.ui

/**
 * Textos de la interfaz, centralizados en una unica fuente (español).
 *
 * Hoy es el punto de entrada para la internacionalizacion: mantiene consistencia
 * entre pantallas y concentra todos los literales para traducirlos en el futuro
 * (p. ej. migrando a `stringResource` / recursos por idioma) sin tocar la logica.
 */
object AppStrings {
    const val APP_TITLE = "IPTV Family"
    const val WINDOW_TITLE_SUFFIX = "· IPTV Family"

    const val CANCEL = "Cancelar"
    const val RETRY = "Reintentar"

    object Nav {
        const val HOME = "Mis listas"
        const val CHANNELS = "Canales"
        const val FAVORITES = "Favoritos"
        const val PLAYER = "Reproduciendo"
        const val SETTINGS = "Ajustes"
    }

    object Home {
        const val TITLE = "Mis listas"
        const val SUBTITLE = "Añade tu lista M3U o tus datos de Xtream Codes y empieza a ver la tele."
        const val ADD = "Añadir lista"
        const val ADD_FIRST = "Añadir mi primera lista"
        const val VIEW_CHANNELS = "Ver canales"
        const val REFRESH = "Actualizar lista"
        const val DELETE = "Eliminar lista"
        const val DELETE_TITLE = "Eliminar lista"
        const val EMPTY_TITLE = "Todavía no hay ninguna lista"
        const val EMPTY_BODY =
            "Tu proveedor te da o una URL que acaba en .m3u, o un panel con usuario y contraseña " +
                "(Xtream Codes). Sirven las dos."
        fun deleteConfirm(name: String) = "¿Seguro que quieres eliminar «$name»? Sus favoritos se mantienen."
    }
}
