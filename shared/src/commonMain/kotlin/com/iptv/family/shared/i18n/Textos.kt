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


    // --- ajustes ---
    val aspecto: String
    val tema: String
    val temaAyuda: String
    val temaSistema: String
    val temaClaro: String
    val temaOscuro: String
    val reproduccion: String
    val bufferAyuda: String
    val modoCompatibilidad: String
    val aceleracionHardware: String
    val aceleracionAyuda: String
    val controlParental: String
    val bloquearAdultos: String
    val guardarPin: String
    val quitar: String
    val sinPinGuardado: String
    val servidorWeb: String
    val activarServidorWeb: String
    val puerto: String
    val guardarPuerto: String
    val arreglarAudioNavegador: String
    val rutaFfmpeg: String
    val informacion: String
    val motorDeVideo: String
    val libvlcDetectado: String
    val noEncontrado: String
    val datosGuardadosEn: String
    val version: String
    val usuariosDeLaWeb: String
    val anadirCuenta: String
    val crear: String
    val borrar: String
    val cambiar: String
    val contrasenaNueva: String
    val nombreYaExiste: String
    val rolAdministrador: String
    val rolInvitado: String
    val rolAdministradorCorto: String
    val rolInvitadoCorto: String
    val cambiarPin: String
    val bloquearAdultosAyuda: String
    val modoCompatibilidadAyuda: String
    val servidorWebAyuda: String
    val audioNavegadorAyuda: String
    val ffmpegNoEncontrado: String
    val direccionesWeb: String
    val sinCuentasWeb: String
    fun bufferDeRed(segundos: Int): String
    fun pinNuevo(min: Int, max: Int): String
    fun ffmpegEncontrado(ruta: String): String
    fun paraVerloFuera(puerto: Int): String
    fun cuentaCreada(nombre: String): String
    fun cuentaEliminada(nombre: String): String
    fun contrasenaCambiada(nombre: String): String
    fun contrasenaMinima(minimo: Int): String


    // --- resto de pantallas ---
    val urlVacioSeToma: String
    val elegirArchivoM3u: String
    val ayudaM3uUrl: String
    val ayudaXtream: String
    val ayudaM3uArchivo: String
    val guardarYCargar: String
    val sinListaAbierta: String
    val veAMisListas: String
    val irAMisListas: String
    val noSePudoCargarLaLista: String
    val volverAMisListas: String
    val categoriaVacia: String
    val episodiosNoCargados: String
    val serieSinEpisodios: String
    val bloqueadaPorControlParental: String
    val introduceElPin: String
    val unaConexionPorCuenta: String
    val volverALaLista: String
    val canalAnterior: String
    val canalSiguiente: String
    val enPausa: String
    val quitarSilencio: String
    val silenciar: String
    val salirPantallaCompleta: String
    val pantallaCompleta: String
    val idiomaDelAudio: String
    val sinMotorDeVideo: String
    val sinMotorDeVideoAyuda: String
    val sinGrupo: String
    val quitarDeFavoritos: String
    val anadirAFavoritos: String
    val sinFavoritosTitulo: String
    val sinFavoritosAyuda: String
    val faltaDefinirPin: String
    val pinSoloNumeros: String
    val pinNoCoincide: String
    val pinDelControlParental: String
    val repiteElPin: String
    val sinEpisodiosParaLaSerie: String
    val favoritoMantenPulsado: String
    val mantenPulsadoParaFavorito: String
    fun ningunCanalCoincide(busqueda: String): String
    fun canalesGuardados(n: Int): String
    fun categoriaBloqueadaSinPin(categoria: String): String
    fun enDirecto(grupo: String): String
    fun noSePudoLeerElArchivo(motivo: String): String

    val bloqueada: String
    val desbloquear: String
    val quitarPin: String
    val ejemploNombreLista: String
    val guiaEpgOpcional: String
    val detener: String
    val seleccionado: String
    val limpiarBusqueda: String
    val favorito: String
    fun introduceElPinPara(categoria: String): String
    fun reproducirCanal(nombre: String): String
    fun versionNumero(version: String): String

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


    override val aspecto = "Aspecto"
    override val tema = "Tema"
    override val temaAyuda = "Claro, oscuro, o sigue la configuración del sistema."
    override val temaSistema = "Sistema"
    override val temaClaro = "Claro"
    override val temaOscuro = "Oscuro"
    override val reproduccion = "Reproducción"
    override val bufferAyuda = "Súbelo si la imagen se corta a menudo; bájalo si tarda mucho en arrancar."
    override val modoCompatibilidad = "Modo compatibilidad de vídeo"
    override val aceleracionHardware = "Aceleración por hardware"
    override val aceleracionAyuda = "Descarga la decodificación en la gráfica. Desactívala si ves la imagen corrupta."
    override val controlParental = "Control parental"
    override val bloquearAdultos = "Bloquear categorías de adultos"
    override val guardarPin = "Guardar PIN"
    override val quitar = "Quitar"
    override val sinPinGuardado = "Sin PIN guardado el bloqueo no se puede abrir. Guarda uno."
    override val servidorWeb = "Servidor web"
    override val activarServidorWeb = "Activar servidor web"
    override val puerto = "Puerto"
    override val guardarPuerto = "Guardar puerto"
    override val arreglarAudioNavegador = "Arreglar el audio para el navegador"
    override val rutaFfmpeg = "Ruta a ffmpeg (opcional)"
    override val informacion = "Información"
    override val motorDeVideo = "Motor de vídeo"
    override val libvlcDetectado = "libvlc detectado"
    override val noEncontrado = "no encontrado"
    override val datosGuardadosEn = "Datos guardados en"
    override val version = "Versión"
    override val usuariosDeLaWeb = "Usuarios de la web"
    override val anadirCuenta = "Añadir cuenta"
    override val crear = "Crear"
    override val borrar = "Borrar"
    override val cambiar = "Cambiar"
    override val contrasenaNueva = "Contraseña nueva"
    override val nombreYaExiste = "Ya existe una cuenta con ese nombre."
    override val rolAdministrador = "Administrador — control total"
    override val rolInvitado = "Invitado — solo ve el canal que pongas tú"
    override val rolAdministradorCorto = "Administrador"
    override val rolInvitadoCorto = "Invitado (solo ver)"
    override val cambiarPin = "Cambiar PIN (déjalo vacío para no tocarlo)"
    override val bloquearAdultosAyuda = "Pide un PIN para abrir categorías con nombres como «adult», «18+» o «xxx»"
    override val modoCompatibilidadAyuda =
            "Actívalo solo si oyes el canal pero la imagen sale en negro. " +
                "Consume algo más de procesador."
    override val servidorWebAyuda =
        "Permite ver y cambiar de canal desde el navegador de cualquier dispositivo de tu red (o de " +
            "internet, exponiendo el puerto con algo como ngrok)."
    override val audioNavegadorAyuda =
        "Dos problemas que solo afectan a la web. Uno: muchos canales emiten en AC-3 o MP2, que " +
            "ningún navegador puede reproducir (en esta aplicación sí se oye). Dos: cuando un canal " +
            "trae varios idiomas dentro del mismo flujo, el navegador se queda con el primero — que " +
            "suele ser la audiodescripción — sin poder elegir. Con esto ffmpeg escoge la pista en " +
            "español y, solo si hace falta, la convierte a AAC. El vídeo se copia tal cual."
    override val ffmpegNoEncontrado =
        "No se encuentra ffmpeg. Instálalo (winget install Gyan.FFmpeg) o indica su ruta abajo; sin " +
            "él, esos canales seguirán sin sonido en la web."
    override val direccionesWeb = "Direcciones para abrir la web (cada persona entra con su usuario y contraseña):"
    override val sinCuentasWeb =
        "Todavía no hay ninguna cuenta. Crea la primera (será administradora) aquí o desde la " +
            "propia web, que al abrirla sin cuentas pide crearla."
    override fun bufferDeRed(segundos: Int) = "Buffer de red: $segundos s"
    override fun pinNuevo(min: Int, max: Int) = "PIN nuevo ($min-$max dígitos)"
    override fun ffmpegEncontrado(ruta: String) = "ffmpeg encontrado: $ruta"
    override fun paraVerloFuera(puerto: Int) = "Para verlo fuera de casa: ngrok http $puerto"
    override fun cuentaCreada(nombre: String) = "Cuenta '$nombre' creada."
    override fun cuentaEliminada(nombre: String) = "Cuenta '$nombre' eliminada."
    override fun contrasenaCambiada(nombre: String) = "Contraseña de '$nombre' cambiada."
    override fun contrasenaMinima(minimo: Int) =
        "La contraseña debe tener al menos $minimo caracteres. No se guarda tal cual: se " +
            "guarda su hash, así que no se puede recuperar (solo cambiar)."

    override val urlVacioSeToma = "Déjalo vacío: se toma del propio panel"
    override val elegirArchivoM3u = "Elegir archivo .m3u"
    override val ayudaM3uUrl = "La URL que te dio tu proveedor, normalmente acaba en .m3u o .m3u8."
    override val ayudaXtream = "Los tres datos del panel de tu proveedor. No añadas /player_api.php."
    override val ayudaM3uArchivo = "Un archivo .m3u que ya tengas guardado en el ordenador."
    override val guardarYCargar = "Guardar y cargar"
    override val sinListaAbierta = "No hay ninguna lista abierta"
    override val veAMisListas = "Ve a «Mis listas» y añade o selecciona una."
    override val irAMisListas = "Ir a mis listas"
    override val noSePudoCargarLaLista = "No se pudo cargar la lista"
    override val volverAMisListas = "Volver a mis listas"
    override val categoriaVacia = "Esta categoría está vacía."
    override val episodiosNoCargados = "No se pudieron cargar los episodios."
    override val serieSinEpisodios = "Esta serie no tiene episodios disponibles."
    override val bloqueadaPorControlParental = "Bloqueada por control parental"
    override val introduceElPin = "Introduce el PIN para ver esta categoría."
    override val unaConexionPorCuenta = "1 conexión por cuenta"
    override val volverALaLista = "Volver a la lista"
    override val canalAnterior = "Canal anterior"
    override val canalSiguiente = "Canal siguiente"
    override val enPausa = "En pausa"
    override val quitarSilencio = "Quitar silencio"
    override val silenciar = "Silenciar"
    override val salirPantallaCompleta = "Salir de pantalla completa"
    override val pantallaCompleta = "Pantalla completa"
    override val idiomaDelAudio = "Idioma del audio"
    override val sinMotorDeVideo = "No se encontró el motor de vídeo (libvlc)"
    override val sinMotorDeVideoAyuda =
        "El instalador de IPTV Family lo incluye. Si estás ejecutando desde el código fuente, " +
            "instala VLC en el sistema y reinicia la aplicación."
    override val sinGrupo = "Sin grupo"
    override val quitarDeFavoritos = "Quitar de favoritos"
    override val anadirAFavoritos = "Añadir a favoritos"
    override val sinFavoritosTitulo = "Todavía no hay favoritos"
    override val sinFavoritosAyuda = "Pulsa el corazón en cualquier canal para tenerlo siempre a mano aquí."
    override val faltaDefinirPin =
        "Falta definir el PIN. Sin él, las categorías de adultos quedarían bloqueadas sin forma " +
            "de abrirlas."
    override val pinSoloNumeros = "El PIN debe ser solo números: en el mando no hay letras."
    override val pinNoCoincide = "Los dos PIN no coinciden."
    override val pinDelControlParental = "PIN del control parental"
    override val repiteElPin = "Repite el PIN"
    override val sinEpisodiosParaLaSerie = "No se encontraron episodios para esta serie."
    override val favoritoMantenPulsado = "Favorito (mantén pulsado para quitar)"
    override val mantenPulsadoParaFavorito = "Mantén pulsado para marcar favorito"
    override fun ningunCanalCoincide(busqueda: String) = "Ningún canal coincide con «$busqueda»."
    override fun canalesGuardados(n: Int) = if (n == 1) "1 canal guardado" else "$n canales guardados"
    override fun categoriaBloqueadaSinPin(categoria: String) =
        "«$categoria» está bloqueada y todavía no hay ningún PIN definido."
    override fun enDirecto(grupo: String) = "En directo · $grupo"
    override fun noSePudoLeerElArchivo(motivo: String) = "No se pudo leer el archivo: $motivo"
    override val bloqueada = "Bloqueada"
    override val desbloquear = "Desbloquear"
    override val quitarPin = "Quitar PIN"
    override val ejemploNombreLista = "Ej. Casa"
    override val guiaEpgOpcional = "Guía EPG (XMLTV) — opcional"
    override val detener = "Detener"
    override val seleccionado = "Seleccionado"
    override val limpiarBusqueda = "Limpiar búsqueda"
    override val favorito = "Favorito"
    override fun introduceElPinPara(categoria: String) = "Introduce el PIN para abrir «$categoria»."
    override fun reproducirCanal(nombre: String) = "Reproducir $nombre"
    override fun versionNumero(version: String) = "Versión $version"
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


    override val aspecto = "Appearance"
    override val tema = "Theme"
    override val temaAyuda = "Light, dark, or follow the system setting."
    override val temaSistema = "System"
    override val temaClaro = "Light"
    override val temaOscuro = "Dark"
    override val reproduccion = "Playback"
    override val bufferAyuda = "Raise it if playback stutters; lower it if channels take too long to start."
    override val modoCompatibilidad = "Video compatibility mode"
    override val aceleracionHardware = "Hardware acceleration"
    override val aceleracionAyuda =
            "Lets the graphics card do the decoding. Turn it off if the " +
                "picture looks corrupted."
    override val controlParental = "Parental control"
    override val bloquearAdultos = "Block adult categories"
    override val guardarPin = "Save PIN"
    override val quitar = "Remove"
    override val sinPinGuardado = "Without a saved PIN the block cannot be opened. Save one."
    override val servidorWeb = "Web server"
    override val activarServidorWeb = "Enable web server"
    override val puerto = "Port"
    override val guardarPuerto = "Save port"
    override val arreglarAudioNavegador = "Fix audio for the browser"
    override val rutaFfmpeg = "Path to ffmpeg (optional)"
    override val informacion = "About"
    override val motorDeVideo = "Video engine"
    override val libvlcDetectado = "libvlc found"
    override val noEncontrado = "not found"
    override val datosGuardadosEn = "Data stored in"
    override val version = "Version"
    override val usuariosDeLaWeb = "Web users"
    override val anadirCuenta = "Add account"
    override val crear = "Create"
    override val borrar = "Delete"
    override val cambiar = "Change"
    override val contrasenaNueva = "New password"
    override val nombreYaExiste = "An account with that name already exists."
    override val rolAdministrador = "Administrator — full control"
    override val rolInvitado = "Guest — only sees the channel you put on"
    override val rolAdministradorCorto = "Administrator"
    override val rolInvitadoCorto = "Guest (view only)"
    override val cambiarPin = "Change PIN (leave empty to keep it)"
    override val bloquearAdultosAyuda = "Asks for a PIN to open categories named like “adult”, “18+” or “xxx”"
    override val modoCompatibilidadAyuda =
            "Turn this on only if you hear the channel but the picture stays " +
                "black. Uses a bit more CPU."
    override val servidorWebAyuda =
        "Lets you watch and change channel from the browser of any device on your network (or over " +
            "the internet, exposing the port with something like ngrok)."
    override val audioNavegadorAyuda =
        "Two problems that only affect the web. One: many channels broadcast in AC-3 or MP2, which " +
            "no browser can play (this application can). Two: when a channel carries several languages " +
            "in the same stream, the browser keeps the first one — usually the audio description — with " +
            "no way to choose. With this, ffmpeg picks the Spanish track and, only if needed, converts " +
            "it to AAC. The video is copied as is."
    override val ffmpegNoEncontrado =
        "ffmpeg not found. Install it (winget install Gyan.FFmpeg) or set its path below; without " +
            "it, those channels will stay silent on the web."
    override val direccionesWeb = "Addresses to open the web interface (each person signs in with their own account):"
    override val sinCuentasWeb =
        "No accounts yet. Create the first one (it will be an administrator) here or from the web " +
            "interface itself, which asks for it when opened with no accounts."
    override fun bufferDeRed(segundos: Int) = "Network buffer: $segundos s"
    override fun pinNuevo(min: Int, max: Int) = "New PIN ($min-$max digits)"
    override fun ffmpegEncontrado(ruta: String) = "ffmpeg found: $ruta"
    override fun paraVerloFuera(puerto: Int) = "To watch it away from home: ngrok http $puerto"
    override fun cuentaCreada(nombre: String) = "Account '$nombre' created."
    override fun cuentaEliminada(nombre: String) = "Account '$nombre' deleted."
    override fun contrasenaCambiada(nombre: String) = "Password for '$nombre' changed."
    override fun contrasenaMinima(minimo: Int) =
        "The password must be at least $minimo characters. It is not stored as such: its " +
            "hash is, so it cannot be recovered (only changed)."

    override val urlVacioSeToma = "Leave empty: taken from the panel itself"
    override val elegirArchivoM3u = "Choose .m3u file"
    override val ayudaM3uUrl = "The URL your provider gave you; it usually ends in .m3u or .m3u8."
    override val ayudaXtream = "The three details from your provider's panel. Do not add /player_api.php."
    override val ayudaM3uArchivo = "An .m3u file you already have on this computer."
    override val guardarYCargar = "Save and load"
    override val sinListaAbierta = "No playlist open"
    override val veAMisListas = "Go to “My playlists” and add or select one."
    override val irAMisListas = "Go to my playlists"
    override val noSePudoCargarLaLista = "The playlist could not be loaded"
    override val volverAMisListas = "Back to my playlists"
    override val categoriaVacia = "This category is empty."
    override val episodiosNoCargados = "The episodes could not be loaded."
    override val serieSinEpisodios = "This series has no available episodes."
    override val bloqueadaPorControlParental = "Blocked by parental control"
    override val introduceElPin = "Enter the PIN to see this category."
    override val unaConexionPorCuenta = "1 connection per account"
    override val volverALaLista = "Back to the list"
    override val canalAnterior = "Previous channel"
    override val canalSiguiente = "Next channel"
    override val enPausa = "Paused"
    override val quitarSilencio = "Unmute"
    override val silenciar = "Mute"
    override val salirPantallaCompleta = "Exit full screen"
    override val pantallaCompleta = "Full screen"
    override val idiomaDelAudio = "Audio language"
    override val sinMotorDeVideo = "Video engine not found (libvlc)"
    override val sinMotorDeVideoAyuda =
        "The IPTV Family installer includes it. If you are running from source, install VLC on " +
            "the system and restart the application."
    override val sinGrupo = "No group"
    override val quitarDeFavoritos = "Remove from favourites"
    override val anadirAFavoritos = "Add to favourites"
    override val sinFavoritosTitulo = "No favourites yet"
    override val sinFavoritosAyuda = "Press the heart on any channel to keep it handy here."
    override val faltaDefinirPin =
            "The PIN is not set yet. Without it, adult categories would be " +
                "blocked with no way to open them."
    override val pinSoloNumeros = "The PIN must be digits only: a remote has no letters."
    override val pinNoCoincide = "The two PINs do not match."
    override val pinDelControlParental = "Parental control PIN"
    override val repiteElPin = "Repeat the PIN"
    override val sinEpisodiosParaLaSerie = "No episodes found for this series."
    override val favoritoMantenPulsado = "Favourite (long press to remove)"
    override val mantenPulsadoParaFavorito = "Long press to mark as favourite"
    override fun ningunCanalCoincide(busqueda: String) = "No channel matches “$busqueda”."
    override fun canalesGuardados(n: Int) = if (n == 1) "1 saved channel" else "$n saved channels"
    override fun categoriaBloqueadaSinPin(categoria: String) =
        "“$categoria” is blocked and no PIN has been set yet."
    override fun enDirecto(grupo: String) = "Live · $grupo"
    override fun noSePudoLeerElArchivo(motivo: String) = "The file could not be read: $motivo"
    override val bloqueada = "Blocked"
    override val desbloquear = "Unlock"
    override val quitarPin = "Remove PIN"
    override val ejemploNombreLista = "E.g. Home"
    override val guiaEpgOpcional = "EPG guide (XMLTV) — optional"
    override val detener = "Stop"
    override val seleccionado = "Selected"
    override val limpiarBusqueda = "Clear search"
    override val favorito = "Favourite"
    override fun introduceElPinPara(categoria: String) = "Enter the PIN to open “$categoria”."
    override fun reproducirCanal(nombre: String) = "Play $nombre"
    override fun versionNumero(version: String) = "Version $version"
    override fun avisoListaGuardada(antiguedad: String) =
        "No connection to the provider: showing the playlist saved $antiguedad."
}
