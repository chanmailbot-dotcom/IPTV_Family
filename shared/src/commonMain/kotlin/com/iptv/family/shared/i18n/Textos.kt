package com.iptv.family.shared.i18n

import java.util.Locale

/**
 * Textos de la aplicacion, por idioma.
 *
 * Es una INTERFAZ y no un mapa de claves a proposito: al añadir un texto, el
 * compilador obliga a escribirlo en todos los idiomas. Con un mapa se puede
 * olvidar una traduccion y el fallo solo aparece cuando alguien con ese idioma
 * llega a esa pantalla y ve una clave en crudo.
 *
 * El castellano es el idioma original del proyecto; el ingles existe para poder
 * publicarlo. Los nombres de las propiedades van en castellano igual que el
 * resto del codigo.
 */
interface Textos {

    // --- generales ---
    val cancelar: String
    val reintentar: String
    val cerrar: String
    val eliminar: String
    val guardar: String

    // --- navegacion ---
    val misListas: String
    val canales: String
    val tvEnDirecto: String
    val peliculas: String
    val series: String
    val favoritos: String
    val ajustes: String
    val reproduciendo: String

    // --- mis listas ---
    val listasSubtitulo: String
    val añadirLista: String
    val añadirPrimeraLista: String
    val verCanales: String
    val actualizarLista: String
    val eliminarLista: String
    val sinListasTitulo: String
    val sinListasCuerpo: String
    fun confirmarBorrado(nombre: String): String
    // --- dialogo de añadir lista ---
    val nombreDeLaLista: String
    val urlDeLaLista: String
    val urlDelPanel: String
    val usuario: String
    val contrasena: String
    val mostrarContrasena: String
    val ocultarContrasena: String
    /** Como se llama cada clase de lista donde el usuario la ve. */
    val listaM3uPorUrl: String
    val listaM3uArchivo: String
    val listaXtream: String

    // --- canales ---
    val categorias: String
    val todas: String
    /** Filtro de tipo: todo (directos, peliculas y series juntos). */
    val todo: String
    val buscarCanal: String
    val elegirCategoria: String
    val cargandoCanales: String
    val sinElementosEnCategoria: String
    val sinFavoritos: String
    fun cuentaCanales(n: Int): String

    // --- reproductor ---
    val audio: String
    val subtitulos: String
    val subtitulosDesactivados: String
    val pistaDeAudio: String
    val reproducir: String
    val pausar: String
    val volver: String
    fun pistaSinNombre(n: Int): String

    // --- sin conexion ---
    /** Cuanto hace de un instante, en palabras: «hace 3 horas», «ayer». */
    fun antiguedad(minutos: Long): String
    fun avisoListaGuardada(antiguedad: String): String

    companion object {
        /**
         * Idioma en uso. Se resuelve una vez, con el del sistema; cualquier
         * idioma que no sea castellano cae en ingles, que es el unico otro que
         * hay traducido.
         */
        var actual: Textos = if (Locale.getDefault().language == "es") TextosEs else TextosEn

        /** Fija el idioma a mano (ajustes de la aplicacion, o pruebas). */
        fun usar(codigo: String) {
            actual = if (codigo == "es") TextosEs else TextosEn
        }
    }
}

/** Atajo: `T.cancelar`. */
val T: Textos get() = Textos.actual

object TextosEs : Textos {
    override val cancelar = "Cancelar"
    override val reintentar = "Reintentar"
    override val cerrar = "Cerrar"
    override val eliminar = "Eliminar"
    override val guardar = "Guardar"

    override val misListas = "Mis listas"
    override val canales = "Canales"
    override val tvEnDirecto = "TV en directo"
    override val peliculas = "Películas"
    override val series = "Series"
    override val favoritos = "Favoritos"
    override val ajustes = "Ajustes"
    override val reproduciendo = "Reproduciendo"

    override val listasSubtitulo =
        "Añade tu lista M3U o tus datos de Xtream Codes y empieza a ver la tele."
    override val añadirLista = "Añadir lista"
    override val añadirPrimeraLista = "Añadir mi primera lista"
    override val verCanales = "Ver canales"
    override val actualizarLista = "Actualizar lista"
    override val eliminarLista = "Eliminar lista"
    override val sinListasTitulo = "Todavía no hay ninguna lista"
    override val sinListasCuerpo =
        "Tu proveedor te da o una URL que acaba en .m3u, o un panel con usuario y contraseña " +
            "(Xtream Codes). Sirven las dos."
    override fun confirmarBorrado(nombre: String) =
        "¿Seguro que quieres eliminar «$nombre»? Sus favoritos se mantienen."
    override val nombreDeLaLista = "Nombre de la lista"
    override val urlDeLaLista = "URL de la lista"
    override val urlDelPanel = "URL del panel"
    override val usuario = "Usuario"
    override val contrasena = "Contraseña"
    override val mostrarContrasena = "Mostrar contraseña"
    override val ocultarContrasena = "Ocultar contraseña"
    override val listaM3uPorUrl = "Lista M3U por URL"
    override val listaM3uArchivo = "Archivo M3U local"
    override val listaXtream = "Xtream Codes"

    override val categorias = "Categorías"
    override val todas = "Todas"
    override val todo = "Todo"
    override val buscarCanal = "Buscar canal…"
    override val elegirCategoria = "Elegir categoría"
    override val cargandoCanales = "Cargando canales…"
    override val sinElementosEnCategoria = "No hay elementos en esta categoría."
    override val sinFavoritos =
        "Aún no tienes canales favoritos. Márcalos con la estrella (mantén pulsado)."
    override fun cuentaCanales(n: Int) = if (n == 1) "1 canal" else "$n canales"

    override val audio = "Audio"
    override val subtitulos = "Subtítulos"
    override val subtitulosDesactivados = "Desactivados"
    override val pistaDeAudio = "Pista de audio"
    override val reproducir = "Reproducir"
    override val pausar = "Pausar"
    override val volver = "Volver"
    override fun pistaSinNombre(n: Int) = "Pista $n"

    override fun antiguedad(minutos: Long) = when {
        minutos < 2 -> "hace un momento"
        minutos < 60 -> "hace $minutos minutos"
        minutos < 120 -> "hace una hora"
        minutos < 24 * 60 -> "hace ${minutos / 60} horas"
        minutos < 48 * 60 -> "ayer"
        else -> "hace ${minutos / (24 * 60)} días"
    }

    override fun avisoListaGuardada(antiguedad: String) =
        "Sin conexión con el proveedor: se muestra la lista guardada $antiguedad."
}

object TextosEn : Textos {
    override val cancelar = "Cancel"
    override val reintentar = "Retry"
    override val cerrar = "Close"
    override val eliminar = "Delete"
    override val guardar = "Save"

    override val misListas = "My playlists"
    override val canales = "Channels"
    override val tvEnDirecto = "Live TV"
    override val peliculas = "Movies"
    override val series = "Series"
    override val favoritos = "Favourites"
    override val ajustes = "Settings"
    override val reproduciendo = "Now playing"

    override val listasSubtitulo =
        "Add your M3U playlist or your Xtream Codes details and start watching."
    override val añadirLista = "Add playlist"
    override val añadirPrimeraLista = "Add my first playlist"
    override val verCanales = "View channels"
    override val actualizarLista = "Refresh playlist"
    override val eliminarLista = "Delete playlist"
    override val sinListasTitulo = "No playlists yet"
    override val sinListasCuerpo =
        "Your provider gives you either a URL ending in .m3u, or a panel with a username and " +
            "password (Xtream Codes). Either works."
    override fun confirmarBorrado(nombre: String) =
        "Delete “$nombre”? Its favourites are kept."
    override val nombreDeLaLista = "Playlist name"
    override val urlDeLaLista = "Playlist URL"
    override val urlDelPanel = "Panel URL"
    override val usuario = "Username"
    override val contrasena = "Password"
    override val mostrarContrasena = "Show password"
    override val ocultarContrasena = "Hide password"
    override val listaM3uPorUrl = "M3U playlist by URL"
    override val listaM3uArchivo = "Local M3U file"
    override val listaXtream = "Xtream Codes"

    override val categorias = "Categories"
    override val todas = "All"
    override val todo = "All"
    override val buscarCanal = "Search channel…"
    override val elegirCategoria = "Choose category"
    override val cargandoCanales = "Loading channels…"
    override val sinElementosEnCategoria = "Nothing in this category."
    override val sinFavoritos = "No favourite channels yet. Mark them with the star (long press)."
    override fun cuentaCanales(n: Int) = if (n == 1) "1 channel" else "$n channels"

    override val audio = "Audio"
    override val subtitulos = "Subtitles"
    override val subtitulosDesactivados = "Off"
    override val pistaDeAudio = "Audio track"
    override val reproducir = "Play"
    override val pausar = "Pause"
    override val volver = "Back"
    override fun pistaSinNombre(n: Int) = "Track $n"

    override fun antiguedad(minutos: Long) = when {
        minutos < 2 -> "a moment ago"
        minutos < 60 -> "$minutos minutes ago"
        minutos < 120 -> "an hour ago"
        minutos < 24 * 60 -> "${minutos / 60} hours ago"
        minutos < 48 * 60 -> "yesterday"
        else -> "${minutos / (24 * 60)} days ago"
    }

    override fun avisoListaGuardada(antiguedad: String) =
        "No connection to the provider: showing the playlist saved $antiguedad."
}
