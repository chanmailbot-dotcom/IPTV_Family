package com.iptv.family.shared.data.audio

/**
 * Elige que pista de audio poner cuando el canal trae varias.
 *
 * Por que hace falta: en television española es habitual que la PRIMERA pista de
 * audio no sea la normal. Un caso real de la lista del usuario:
 *
 *   pista 1: aac, idioma "qad"   <- audiodescripcion (un narrador describiendo la escena)
 *   pista 2: aac, idioma "spa"   <- el audio normal en español
 *
 * Coger "la primera" -- que es lo que hacen VLC y ffmpeg por defecto -- pone la
 * audiodescripcion. De ahi que haya que puntuar las pistas en vez de quedarse
 * con la primera.
 *
 * `qad` esta en el rango ISO 639-3 reservado para uso local (qaa-qtz), que los
 * emisores españoles usan precisamente para la audiodescripcion.
 */
object AudioTrackPreference {

    /** Codigos de idioma que consideramos español, en cualquier variante. */
    private val SPANISH = setOf(
        "es", "spa", "esp", "spanish", "espanol", "español",
        "cas", "cast", "castellano", "es-es", "spa-es", "lat", "es-419",
    )

    /**
     * Marcas de que una pista es audiodescripcion (o comentario), no el audio
     * principal. Se mira tanto el codigo de idioma como el titulo/descripcion,
     * porque cada emisor lo etiqueta a su manera.
     */
    private val DESCRIPTIVE = listOf(
        "qad", "audiodesc", "audio desc", "descripc", "描述",
        "visually impaired", "vision", "comentario", "commentary", "narrat",
    )

    /** Marcas de version original (subtitulada), que no queremos por defecto. */
    private val ORIGINAL_VERSION = listOf("vos", "original", " ov", "subtitul")

    /**
     * Puntuacion de una pista: mas alta, mas deseable. Se usa para ordenar, no
     * como valor absoluto.
     */
    fun score(language: String?, title: String? = null): Int {
        val lang = language?.trim()?.lowercase().orEmpty()
        val text = (title?.trim()?.lowercase().orEmpty() + " " + lang).trim()

        // La audiodescripcion va al final: es español, pero no es lo que quiere
        // oir alguien que no la ha pedido.
        if (DESCRIPTIVE.any { it in text }) return -100

        var points = 0
        if (lang in SPANISH || SPANISH.any { it.length > 3 && it in text }) points += 100
        // Entre dos pistas españolas, la que no sea "version original subtitulada".
        if (ORIGINAL_VERSION.any { it in text }) points -= 30
        // Una pista sin idioma marcado es mejor candidata que una en otro idioma:
        // en la practica suele ser el audio principal del canal.
        if (points == 0 && (lang.isEmpty() || lang == "und" || lang == "mul")) points += 20
        return points
    }

    /**
     * La pista preferida de [tracks], o null si la lista esta vacia.
     *
     * En caso de empate gana la que venga antes, que es el orden en que la
     * declara el emisor.
     */
    fun <T> preferred(
        tracks: List<T>,
        language: (T) -> String?,
        title: (T) -> String? = { null },
    ): T? = tracks.withIndex()
        .maxWithOrNull(
            compareBy({ score(language(it.value), title(it.value)) }, { -it.index })
        )
        ?.value

    /** true si merece la pena cambiar de la pista actual a la preferida. */
    fun <T> shouldSwitch(
        tracks: List<T>,
        currentIndex: Int,
        language: (T) -> String?,
        title: (T) -> String? = { null },
    ): Boolean {
        if (tracks.size < 2) return false
        val current = tracks.getOrNull(currentIndex) ?: return true
        val best = preferred(tracks, language, title) ?: return false
        return score(language(best), title(best)) > score(language(current), title(current))
    }

    /**
     * Nombre legible para mostrar en un selector ("Español", "Audiodescripcion",
     * "Ingles"...). Se queda con el codigo crudo si no lo reconoce, que es mas
     * util que inventarse una etiqueta.
     */
    fun displayName(language: String?, title: String? = null): String {
        val lang = language?.trim()?.lowercase().orEmpty()
        val text = (title?.trim()?.lowercase().orEmpty() + " " + lang).trim()
        return when {
            DESCRIPTIVE.any { it in text } -> "Audiodescripción"
            lang in SPANISH -> "Español"
            lang.startsWith("en") || lang == "eng" -> "Inglés"
            lang.startsWith("fr") || lang == "fra" || lang == "fre" -> "Francés"
            lang.startsWith("de") || lang == "deu" || lang == "ger" -> "Alemán"
            lang.startsWith("pt") || lang == "por" -> "Portugués"
            lang.startsWith("it") || lang == "ita" -> "Italiano"
            lang.startsWith("ca") || lang == "cat" -> "Catalán"
            lang.startsWith("eu") || lang == "eus" || lang == "baq" -> "Euskera"
            lang.startsWith("gl") || lang == "glg" -> "Gallego"
            lang.startsWith("ar") || lang == "ara" -> "Árabe"
            !title.isNullOrBlank() -> title.trim()
            lang.isNotEmpty() && lang != "und" -> lang
            else -> "Original"
        }
    }
}
