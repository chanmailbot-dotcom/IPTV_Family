package com.iptv.family.data.m3u

import com.iptv.family.domain.model.Category
import com.iptv.family.domain.model.Channel
import com.iptv.family.domain.model.ChannelType
import java.io.InputStream

/**
 * Parser para listas M3U (formato estándar IPTV)
 *
 * Formato soportado:
 * - M3U básico: #EXTM3U / #EXTINF
 * - M3U con extensión Xtream 19.1.2 (#EXTM3U x-tvg-url=...)
 */
class M3UParser {

    fun parse(input: InputStream): M3UResult {
        val reader = java.io.BufferedReader(java.io.InputStreamReader(input))
        val lines = reader.readLines()
        reader.close()

        val channels = mutableListOf<Channel>()
        val categories = mutableMapOf<String, Category>()
        val categoryIdCounter = mutableMapOf<String, Int>()

        var currentName: String? = null
        var currentLogo: String? = null
        var currentGroup: String? = null
        var currentTvgId: String? = null
        var currentDuration: String? = null
        var isHeaderProcessed = false

        for (line in lines) {
            val trimmed = line.trim()

            when {
                trimmed.startsWith("#EXTM3U") -> {
                    isHeaderProcessed = true
                    continue
                }
                trimmed.startsWith("#EXTINF") -> {
                    val info = parseExtInf(trimmed)
                    currentName = info.name
                    currentLogo = info.logo
                    currentGroup = info.group
                    currentTvgId = info.tvgId
                    currentDuration = info.duration
                }
                trimmed.startsWith("#EXTVLCOPT") || trimmed.startsWith("#EXTGRP") -> {
                    continue
                }
                trimmed.isEmpty() -> continue
                else -> {
                    if (!isHeaderProcessed) isHeaderProcessed = true

                    val streamUrl = trimmed

                    // Asignar categoría
                    val groupName = currentGroup ?: "Sin categoría"
                    if (!categories.containsKey(groupName)) {
                        val catId = "cat_${categories.size + 1}"
                        categories[groupName] = Category(
                            id = catId,
                            name = groupName,
                            type = ChannelType.LIVE_TV
                        )
                    }

                    val category = categories[groupName]!!
                    val channelId = streamUrl.hashCode().toString()

                    channels.add(Channel(
                        id = channelId,
                        name = currentName ?: "Canal $channelId",
                        logoUrl = currentLogo,
                        streamUrl = streamUrl,
                        category = category,
                        type = ChannelType.LIVE_TV,
                        epgId = currentTvgId,
                        duration = currentDuration
                    ))

                    // Reset
                    currentName = null
                    currentLogo = null
                    currentGroup = null
                    currentTvgId = null
                    currentDuration = null
                }
            }
        }

        return M3UResult(
            channels = channels,
            categories = categories.values.toList()
        )
    }

    private fun parseExtInf(line: String): ExtInfInfo {
        var name: String? = null
        var logo: String? = null
        var group: String? = null
        var tvgId: String? = null
        var duration: String? = null

        // Extraer duración (primer campo después de #EXTINF:)
        val afterPrefix = line.substringAfter("#EXTINF:", "").trimStart()

        // Buscar duration
        val durationMatch = afterPrefix.trimStart().takeWhile { it != ',' }
        duration = durationMatch.trim()

        // Extraer el nombre (después de la coma)
        name = line.substringAfter(",", "").trim()

        // Extraer atributos del nombre extendido
        val extInfPattern = Regex("""([\w-]+)="([^"]*)"""")
        val attrs = extInfPattern.findAll(line).associate {
            it.groupValues[1] to it.groupValues[2]
        }

        logo = attrs["tvg-logo"]
        group = attrs["group-title"]
        tvgId = attrs["tvg-id"]

        return ExtInfInfo(
            name = name,
            logo = logo,
            group = group,
            tvgId = tvgId,
            duration = if (duration.isNullOrEmpty()) null else duration
        )
    }

    private data class ExtInfInfo(
        val name: String?,
        val logo: String?,
        val group: String?,
        val tvgId: String?,
        val duration: String?
    )
}

data class M3UResult(
    val channels: List<Channel>,
    val categories: List<Category>
)