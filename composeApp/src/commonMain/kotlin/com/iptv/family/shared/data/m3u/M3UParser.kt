package com.iptv.family.shared.data.m3u

import com.iptv.family.shared.domain.model.Channel
import com.iptv.family.shared.domain.model.Category
import com.iptv.family.shared.domain.model.CategoryType
import kotlinx.serialization.json.Json

class M3UParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(m3uContent: String): ParseResult {
        val lines = m3uContent.lines()
        val channels = mutableListOf<Channel>()
        val categoriesMap = mutableMapOf<String, MutableList<String>>()

        var currentExtinf: ExtinfData? = null

        for (line in lines) {
            val trimmed = line.trim()
            
            if (trimmed.startsWith("#EXTINF:")) {
                currentExtinf = parseExtinf(trimmed)
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                // URL del canal
                val url = trimmed
                currentExtinf?.let { extinf ->
                    val channel = Channel(
                        id = extinf.tvgId ?: url.hashCode().toString(),
                        name = extinf.name,
                        url = url,
                        logoUrl = extinf.tvgLogo,
                        group = extinf.groupTitle,
                        epgChannelId = extinf.tvgId,
                        categoryType = when (extinf.groupTitle?.lowercase()) {
                            null, "" -> CategoryType.LIVE
                            else -> when {
                                extinf.groupTitle!!.contains("vod", true) || extinf.groupTitle!!.contains("movie", true) || extinf.groupTitle!!.contains("pelicula", true) -> CategoryType.VOD
                                extinf.groupTitle!!.contains("series", true) || extinf.groupTitle!!.contains("serie", true) -> CategoryType.SERIES
                                else -> CategoryType.LIVE
                            }
                        }
                    )
                    channels.add(channel)
                    
                    // Agrupar por categoría
                    val categoryName = extinf.groupTitle ?: "General"
                    categoriesMap.getOrPut(categoryName) { mutableListOf() }.add(channel.id)
                    
                    currentExtinf = null
                }
            }
        }

        val categories = categoriesMap.map { (name, channelIds) ->
            Category(
                id = name.hashCode().toString(),
                name = name,
                type = when {
                    name.lowercase().contains("vod") || name.lowercase().contains("movie") || name.lowercase().contains("pelicula") -> CategoryType.VOD
                    name.lowercase().contains("series") || name.lowercase().contains("serie") -> CategoryType.SERIES
                    else -> CategoryType.LIVE
                },
                channels = channelIds
            )
        }.toList()

        return ParseResult(channels = channels, categories = categories)
    }

    private data class ExtinfData(
        val name: String,
        val tvgId: String?,
        val tvgLogo: String?,
        val groupTitle: String?
    )

    private fun parseExtinf(line: String): ExtinfData {
        // #EXTINF:-1 tvg-id="xxx" tvg-logo="xxx" group-title="xxx",Nombre del canal
        val afterExtinf = line.substringAfter("#EXTINF:")
        val parts = afterExtinf.split(",", limit = 2)
        val attributes = parts[0].trim()
        val name = if (parts.size > 1) parts[1].trim() else "Sin nombre"

        val tvgId = extractAttribute(attributes, "tvg-id")
        val tvgLogo = extractAttribute(attributes, "tvg-logo")
        val groupTitle = extractAttribute(attributes, "group-title")

        return ExtinfData(name, tvgId, tvgLogo, groupTitle)
    }

    private fun extractAttribute(attributes: String, attrName: String): String? {
        val regex = """$attrName="([^"]*)"""".toRegex()
        return regex.find(attributes)?.groupValues?.get(1)
    }

    data class ParseResult(
        val channels: List<Channel>,
        val categories: List<Category>
    )
}