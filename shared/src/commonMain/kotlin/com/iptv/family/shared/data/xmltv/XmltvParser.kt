package com.iptv.family.shared.data.xmltv

import com.iptv.family.shared.log.AppLog
import com.iptv.family.shared.model.EPGProgram
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.StringReader
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.xml.parsers.SAXParserFactory

/**
 * Parser del estándar XMLTV (guía electrónica de programas).
 *
 * Espera un documento XMLTV como:
 *   <tv>
 *     <channel id="CNN.us"><display-name>CNN</display-name></channel>
 *     <programme start="20240101000000 +0000" stop="..." channel="CNN.us">
 *       <title lang="es">Noticias</title>
 *       <desc>...</desc>
 *       <category>Noticias</category>
 *       <icon src="http://..."/>
 *     </programme>
 *   </tv>
 *
 * Los [EPGProgram] resultantes se referencian por `channelId` (el atributo
 * `channel`), que es el mismo `tvg-id` de las listas M3U y el `stream_id` de
 * Xtream, de modo que la UI cruza canal <-> programa sin transformaciones.
 *
 * Va en STREAMING (SAX), no montando el documento entero en memoria. Una guía
 * real de un proveedor son decenas o cientos de MB de XML; con un DOM eso son
 * varios GB de objetos, y en un Fire TV con 256 MB de heap la aplicación se
 * muere antes de enseñar un solo programa. Aquí solo vive en memoria el
 * programa que se está leyendo y los que se decide conservar.
 */
class XmltvParser {

    private val timeFieldFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    /**
     * Lee la guía de [input] y devuelve los programas que caen dentro de la
     * ventana [keepFromMs]..[keepUntilMs].
     *
     * La ventana no es una optimización cosmética: una guía trae una o dos
     * semanas de programación de miles de canales, y de todo eso la aplicación
     * solo enseña lo que dan ahora y lo siguiente. Quedarse con la semana
     * entera multiplica por cien la memoria para no enseñar nada más.
     *
     * Nunca devuelve más de [maxProgrammes]: si la guía se pasa, se corta y se
     * deja constancia en el log. Media guía sirve; quedarse sin memoria, no.
     */
    fun parse(
        input: InputStream,
        keepFromMs: Long = Long.MIN_VALUE,
        keepUntilMs: Long = Long.MAX_VALUE,
        maxProgrammes: Int = MAX_PROGRAMMES,
    ): List<EPGProgram> {
        val handler = ProgrammeHandler(keepFromMs, keepUntilMs, maxProgrammes)
        try {
            newSaxParser().parse(InputSource(input), handler)
        } catch (stop: TooManyProgrammes) {
            AppLog.w(
                "EPG",
                "La guía supera los $maxProgrammes programas dentro de la ventana: se usa lo leído hasta ahora",
            )
        }
        if (handler.descartados > 0) {
            AppLog.d("EPG", "Guía: ${handler.programas.size} programas usados, ${handler.descartados} fuera de ventana")
        }
        return handler.programas
    }

    /**
     * Versión sobre texto ya cargado. Se mantiene para las pruebas y para
     * quien tenga la guía en la mano; la que usa la aplicación es la de
     * [InputStream], porque es la que no obliga a tener el XML entero en
     * memoria antes de empezar.
     */
    fun parse(xmltvContent: String): List<EPGProgram> {
        if (xmltvContent.isBlank()) return emptyList()
        val handler = ProgrammeHandler(Long.MIN_VALUE, Long.MAX_VALUE, MAX_PROGRAMMES)
        try {
            newSaxParser().parse(InputSource(StringReader(xmltvContent)), handler)
        } catch (stop: TooManyProgrammes) {
            AppLog.w("EPG", "La guía supera los $MAX_PROGRAMMES programas: se usa lo leído hasta ahora")
        }
        return handler.programas
    }

    private fun newSaxParser() = SAXParserFactory.newInstance().apply {
        // Blindaje XXE sin rechazar DOCTYPE: muchas guias XMLTV reales traen
        // <!DOCTYPE tv SYSTEM "xmltv.dtd"> y con disallow-doctype-decl no se
        // podrian parsear. Se deja el DOCTYPE presente pero inerte: no se
        // cargan DTDs externos y no se resuelven entidades externas.
        //
        // Cada propiedad va por separado y tolerando que no exista: NO todas las
        // implementaciones las conocen. La de Apache no existe en Android, y
        // pedirla tumbaba el parseo entero con SAXNotRecognizedException -- es
        // decir, alli la guia no se cargaba NUNCA. La proteccion de verdad no
        // depende de estas propiedades sino del resolvedor de entidades de mas
        // abajo, que devuelve un documento vacio y no toca la red.
        for (propiedad in FEATURES_XXE) {
            runCatching { setFeature(propiedad, false) }
                .onFailure { AppLog.d("EPG", "el parser XML de este sistema no conoce '$propiedad'") }
        }
        runCatching { isXIncludeAware = false }
        isNamespaceAware = false
    }.newSAXParser()

    /** Corta el parseo cuando ya hay demasiados programas. No es un error. */
    private class TooManyProgrammes : SAXException("demasiados programas")

    private inner class ProgrammeHandler(
        private val keepFromMs: Long,
        private val keepUntilMs: Long,
        private val maxProgrammes: Int,
    ) : DefaultHandler() {

        val programas = ArrayList<EPGProgram>()
        var descartados = 0
            private set

        private var enProgramme = false
        private var start = ""
        private var canal = ""
        private var stop = ""
        private var titulo: String? = null
        private var descripcion: String? = null
        private var categoria: String? = null
        private var icono: String? = null

        private var capturando: String? = null
        private val texto = StringBuilder()

        // Ni una peticion de red por el DTD declarado en la guia: se le
        // devuelve un documento vacio y a seguir.
        override fun resolveEntity(publicId: String?, systemId: String?): InputSource =
            InputSource(ByteArrayInputStream(ByteArray(0)))

        override fun startElement(uri: String?, localName: String?, qName: String, attrs: Attributes) {
            val nombre = qName.lowercase()
            if (!enProgramme) {
                if (nombre == "programme") {
                    enProgramme = true
                    start = attrs.getValue("start").orEmpty()
                    stop = attrs.getValue("stop").orEmpty()
                    canal = attrs.getValue("channel").orEmpty()
                    titulo = null; descripcion = null; categoria = null; icono = null
                }
                return
            }
            when (nombre) {
                "title", "desc", "category" -> {
                    capturando = nombre
                    texto.setLength(0)
                }
                // Hay guias que ponen el icono como atributo y otras como texto;
                // el atributo manda porque es lo que dice el estandar.
                "icon" -> if (icono.isNullOrBlank()) icono = attrs.getValue("src")?.takeIf { it.isNotBlank() }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (capturando != null) texto.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            if (!enProgramme) return
            val nombre = qName.lowercase()
            if (capturando == nombre) {
                val valor = texto.toString().trim()
                // Se queda el PRIMERO no vacio: las guias repiten <title> en
                // varios idiomas y coger el ultimo cambiaria el idioma segun el
                // orden en que venga cada canal.
                when (nombre) {
                    "title" -> if (titulo.isNullOrBlank()) titulo = valor
                    "desc" -> if (descripcion.isNullOrBlank()) descripcion = valor
                    "category" -> if (categoria.isNullOrBlank()) categoria = valor
                }
                capturando = null
                texto.setLength(0)
            }
            if (nombre == "programme") {
                enProgramme = false
                emitir()
            }
        }

        private fun emitir() {
            val titulo = this.titulo
            if (canal.isEmpty() || titulo.isNullOrBlank()) return

            val inicio = parseEpoch(start)
            val fin = parseEpoch(stop)
            // Fuera de ventana: ya terminó, o queda tan lejos que no se va a
            // enseñar. `fin <= 0` significa que la guia no declara final, y esos
            // no se pueden descartar por el final.
            if (fin > 0L && fin < keepFromMs) { descartados++; return }
            if (inicio > keepUntilMs) { descartados++; return }

            programas.add(
                EPGProgram(
                    id = "$start-$canal",
                    channelId = canal,
                    title = titulo,
                    description = descripcion.orEmpty(),
                    startTime = inicio,
                    endTime = fin,
                    category = categoria.orEmpty(),
                    iconUrl = icono?.takeIf { it.isNotBlank() },
                ),
            )
            if (programas.size >= maxProgrammes) throw TooManyProgrammes()
        }
    }

    private fun parseEpoch(rawStart: String): Long {
        if (rawStart.isBlank()) return 0L
        // Forma esperada: yyyyMMddHHmmss [ [+-]HHMM ]
        val trimmed = rawStart.trim()
        val timePart = trimmed.substring(0, minOf(trimmed.length, 14))
        val offsetPart = trimmed.substring(minOf(trimmed.length, 14)).trim().takeIf { it.isNotEmpty() }
        return try {
            val local = LocalDateTime.parse(timePart, timeFieldFormatter)
            val offset = offsetPart?.toZoneOffset() ?: ZoneOffset.UTC
            local.toEpochSecond(offset) * 1000L
        } catch (e: Exception) {
            0L
        }
    }

    private fun String.toZoneOffset(): ZoneOffset {
        // acepta "+0100", "-0500" y "+00:00" (normalizado)
        val normalized = removePrefix("+").removePrefix("-").replace(":", "")
        val hours = normalized.take(2).toIntOrNull() ?: 0
        val minutes = normalized.drop(2).take(2).toIntOrNull() ?: 0
        val offset = hours * 3600 + minutes * 60
        return ZoneOffset.ofTotalSeconds(if (startsWith("-")) -offset else offset)
    }

    private fun minOf(a: Int, b: Int): Int = if (a < b) a else b

    companion object {
        /**
         * Tope de programas conservados. Con la ventana por defecto son unas
         * pocas decenas de miles incluso en guias enormes; este numero es la
         * red de seguridad para una guia rota o maliciosa.
         */
        const val MAX_PROGRAMMES = 300_000

        /** Propiedades que apagan la resolucion de entidades externas, donde existan. */
        private val FEATURES_XXE = listOf(
            "http://xml.org/sax/features/external-general-entities",
            "http://xml.org/sax/features/external-parameter-entities",
            "http://apache.org/xml/features/nonvalidating/load-external-dtd",
        )
    }
}
