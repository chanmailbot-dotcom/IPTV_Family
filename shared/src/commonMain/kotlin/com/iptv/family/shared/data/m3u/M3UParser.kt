package com.iptv.family.shared.data.m3u

import com.iptv.family.shared.model.Category
import com.iptv.family.shared.model.CategoryType
import com.iptv.family.shared.model.Channel

/**
 * Parser de listas M3U / M3U8 con extensión Xtream.
 *
 * Soporta:
 * - Cabecera #EXTM3U
 * - Bloques #EXTINF con atributos (tvg-id, tvg-logo, group-title, tvg-chno, tvg-name)
 * - Marcas adicionales: #EXTGRP, #EXTVLCOPT
 * - Inferencia de categoría (LIVE / VOD / SERIES) por nombre de grupo
 */
class M3UParser {

    fun parse(m3uContent: String): ParseResult {
        val lines = m3uContent.lines()
        val channels = mutableListOf<Channel>()
        val categoriesMap = mutableMapOf<String, MutableList<String>>()

        var currentExtInf: ExtInfData? = null
        var extraGroup: String? = null

        for (line in lines) {
            val trimmed = line.trim()

            when {
                trimmed.startsWith("#EXTINF:") || trimmed.startsWith("#EXTINF ") -> {
                    currentExtInf = parseExtInf(trimmed)
                }

                trimmed.startsWith("#EXTGRP:") -> {
                    extraGroup = trimmed.substringAfter(":").trim().takeIf { it.isNotBlank() }
                }

                trimmed.startsWith("#EXTVLCOPT") || trimmed.startsWith("#EXTM3U") ||
                    trimmed.startsWith("#EXT-X-") || trimmed.isEmpty() -> Unit

                else -> {
                    val url = trimmed
                    val extInf = currentExtInf
                    if (extInf != null && url.isNotBlank()) {
                        val groupName = extInf.groupTitle?.takeIf { it.isNotBlank() }
                            ?: extraGroup?.takeIf { it.isNotBlank() }
                            ?: "General"

                        val channel = Channel(
                            id = extInf.tvgId ?: (extInf.tvgName ?: url.hashCode().toString()),
                            name = extInf.name,
                            url = url,
                            logoUrl = extInf.tvgLogo,
                            group = groupName,
                            epgChannelId = extInf.tvgId ?: extInf.tvgName,
                            categoryType = inferType(groupName)
                        )
                        channels.add(channel)
                        categoriesMap.getOrPut(groupName) { mutableListOf() }.add(channel.id)
                        currentExtInf = null
                    }
                    extraGroup = null
                }
            }
        }

        val categories = categoriesMap.map { (name, channelIds) ->
            Category(
                id = name.hashCode().toString(),
                name = name,
                type = inferType(name),
                channels = channelIds
            )
        }.toList()

        return ParseResult(channels = channels, categories = categories)
    }

    private fun inferType(groupName: String): CategoryType {
        val g = normalizeForInference(groupName)
        return when {
            g.contains("vod") || g.contains("movie") || g.contains("pelicula") ||
                g.contains("film") || g.contains("cine") -> CategoryType.VOD
            g.contains("series") || g.contains("serie") || g.contains("show") -> CategoryType.SERIES
            else -> CategoryType.LIVE
        }
    }

    private fun normalizeForInference(value: String): String {
        // Elimina diacríticos para inferir categoría de forma robusta
        return value.lowercase()
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u").replace("ü", "u").replace("ñ", "n")
    }

    private fun parseExtInf(line: String): ExtInfData {
        // Formato: #EXTINF:-1 tvg-id="..." tvg-logo="..." group-title="...",Nombre del canal
        val afterPrefix = line.removePrefix("#EXTINF").trimStart(':').trim()
        val parts = afterPrefix.split(",", limit = 2)
        val attributesPart = parts[0].trim()
        val name = if (parts.size > 1) parts[1].trim() else "Sin nombre"

        return ExtInfData(
            name = name,
            tvgId = extractAttribute(attributesPart, "tvg-id"),
            tvgName = extractAttribute(attributesPart, "tvg-name"),
            tvgLogo = extractAttribute(attributesPart, "tvg-logo"),
            tvgChno = extractAttribute(attributesPart, "tvg-chno"),
            groupTitle = extractAttribute(attributesPart, "group-title")
        )
    }

    private fun extractAttribute(attributes: String, attrName: String): String? {
        val regex = Regex("""$attrName\s*=\s*"([^"]*)"""")
        return regex.find(attributes)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }

    data class ExtInfData(
        val name: String,
        val tvgId: String?,
        val tvgName: String?,
        val tvgLogo: String?,
        val tvgChno: String?,
        val groupTitle: String?
    )

    data class ParseResult(
        val channels: List<Channel>,
        val categories: List<Category>
    )
}