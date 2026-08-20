package com.iptv.family.shared.data.xmltv

import com.iptv.family.shared.model.EPGProgram
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parser del estándar XMLTV (guía electrónica de programas) para escritorio.
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
 */
class XmltvParser {

    private val timeFieldFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun parse(xmltvContent: String): List<EPGProgram> {
        if (xmltvContent.isBlank()) return emptyList()

        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(InputSource(StringReader(xmltvContent)))
        val root = document.documentElement

        // Mapa de nombres de canal por id (guía útil para la UI).
        // (Los EPGProgram referencian channelId; el nombre se resuelve en UI.)
        val programmes = root.filterElementNodes("programme")
        return programmes.mapNotNull { p ->
            val channelId = p.getAttribute("channel")
            if (channelId.isEmpty()) return@mapNotNull null

            val title = childText(p, "title")
            if (title.isBlank()) return@mapNotNull null

            EPGProgram(
                id = "${p.getAttribute("start")}-$channelId",
                channelId = channelId,
                title = title,
                description = childText(p, "desc"),
                startTime = parseEpoch(p.getAttribute("start")),
                endTime = parseEpoch(p.getAttribute("stop")),
                category = childText(p, "category"),
                iconUrl = childAttr(p, "icon", "src")?.takeIf { it.isNotBlank() } ?:
                    childText(p, "icon").takeIf { it.isNotBlank() } ?: null
            )
        }
    }

    private fun childText(el: Element, tag: String): String {
        val nodes = el.childNodes
        for (i in 0..nodes.length - 1) {
            val child = nodes.item(i)
            if (child is Element && child.nodeName.lowercase() == tag.lowercase()) {
                return child.textContent?.trim() ?: ""
            }
        }
        return ""
    }

    private fun childAttr(el: Element, tag: String, attr: String): String? {
        val nodes = el.childNodes
        for (i in 0..nodes.length - 1) {
            val child = nodes.item(i)
            if (child is Element && child.nodeName.lowercase() == tag.lowercase()) {
                val value = child.getAttribute(attr)
                return if (value.isNotBlank()) value else null
            }
        }
        return null
    }

    private fun Element.filterElementNodes(nodeName: String): kotlin.collections.List<Element> {
        val nodes = this.childNodes
        val out = mutableListOf<Element>()
        for (i in 0..nodes.length - 1) {
            val child = nodes.item(i)
            if (child is Element && child.nodeName.equals(nodeName)) {
                out.add(child)
            }
        }
        return out
    }

    private fun parseEpoch(rawStart: String): Long {
        if (rawStart.isBlank()) return 0L
        // Forma esperada: yyyyMMddHHmmss [ [+-]HHMM ]
        val trimmed = rawStart.trim()
        val timePart = trimmed.substring(0, minOf(trimmed.length, 14))
        val offsetPart = trimmed.substring(14).trim().takeIf { it.isNotEmpty() }
        return try {
            val local = LocalDateTime.parse(timePart, timeFieldFormatter)
            val offset = offsetPart?.toZoneOffset() ?: ZoneOffset.UTC
            local.toEpochSecond(offset) * 1000L
        } catch (e: Exception) {
            0L
        }
    }

    private fun String.toZoneOffset(): ZoneOffset {
        // acepta "+0100", "-0500", alto gélido "+00:00" (normalizar)
        val normalized = removePrefix("+").removePrefix("-").replace(":", "")
        val hours = normalized.take(2).toIntOrNull() ?: 0
        val minutes = normalized.drop(2).take(2).toIntOrNull() ?: 0
        val offset = hours * 3600 + minutes * 60
        return ZoneOffset.ofTotalSeconds(if (startsWith("-")) -offset else offset)
    }

    private fun minOf(a: Int, b: Int): Int = if (a < b) a else b
}