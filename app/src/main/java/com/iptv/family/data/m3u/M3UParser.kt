package com.iptv.family.data.m3u

import com.iptv.family.domain.model.Channel
import com.iptv.family.domain.model.Category
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.regex.Pattern

class M3UParser {

    companion object {
        private const val EXTINF_PATTERN = "#EXTINF:"
        private const val EXTGRP_PATTERN = "group-title="
        private const val TVG_ID_PATTERN = "tvg-id="
        private const val TVG_NAME_PATTERN = "tvg-name="
        private const val TVG_LOGO_PATTERN = "tvg-logo="
        private const val TVG_SHIFT_PATTERN = "tvg-shift="
        private const val TVG_COUNTRY_PATTERN = "tvg-country="
        private const val TVG_LANGUAGE_PATTERN = "tvg-language="
        private const val TVG_URL_PATTERN = "tvg-url="
        private const val RADIO_PATTERN = "radio="
        private const val LIVE_PATTERN = "live="

        fun parse(inputStream: InputStream): M3UParseResult {
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val channels = mutableListOf<Channel>()
            val categoriesMap = mutableMapOf<String, MutableList<String>>()
            var currentExtInf: ExtInfData? = null
            var lineNumber = 0

            reader.use {
                it.forEachLine { line ->
                    lineNumber++
                    val trimmed = line.trim()

                    if (trimmed.startsWith(EXTINF_PATTERN)) {
                        currentExtInf = parseExtInf(trimmed)
                    } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        // This is a URL line
                        val url = trimmed
                        if (currentExtInf != null) {
                            val channel = buildChannel(currentExtInf, url)
                            channels.add(channel)

                            // Group channels by category
                            currentExtInf.group?.let { groupName ->
                                categoriesMap.getOrPut(groupName) { mutableListOf() }.add(channel.id)
                            }
                        }
                        currentExtInf = null
                    }
                }
            }

            // Build categories from map
            val categories = categoriesMap.entries.mapIndexed { index, entry ->
                Category(
                    id = "cat_${entry.key.hashCode()}",
                    name = entry.key,
                    order = index,
                    channelIds = entry.value,
                    isLiveTv = !entry.key.lowercase().contains("vod") && !entry.key.lowercase().contains("movie") && !entry.key.lowercase().contains("series"),
                    isVod = entry.key.lowercase().contains("vod") || entry.key.lowercase().contains("movie"),
                    isSeries = entry.key.lowercase().contains("series"),
                )
            }.sortedBy { it.order }.toList()

            return M3UParseResult(channels, categories)
        }

        private fun parseExtInf(line: String): ExtInfData {
            val content = line.substring(EXTINF_PATTERN.length).trim()

            // Parse duration and name (format: duration,name)
            val commaIndex = content.indexOf(',')
            val (durationPart, namePart) = if (commaIndex >= 0) {
                content.substring(0, commaIndex) to content.substring(commaIndex + 1)
            } else {
                "0" to content
            }

            val attributes = mutableMapOf<String, String>()

            // Parse all attributes using regex
            val attrPattern = Pattern.compile("""(\w+(?:-\w+)*)="([^"]*)"""")
            val matcher = attrPattern.matcher(content)
            while (matcher.find()) {
                attributes[matcher.group(1).lowercase()] = matcher.group(2)
            }

            return ExtInfData(
                duration = durationPart.toIntOrNull() ?: 0,
                name = namePart.trim(),
                group = attributes["group-title"],
                tvgId = attributes["tvg-id"],
                tvgName = attributes["tvg-name"],
                tvgLogo = attributes["tvg-logo"],
                tvgShift = attributes["tvg-shift"],
                tvgCountry = attributes["tvg-country"],
                tvgLanguage = attributes["tvg-language"],
                tvgUrl = attributes["tvg-url"],
                isRadio = attributes["radio"]?.toBoolean() ?: false,
                isLive = attributes["live"]?.toBoolean() ?: true,
            )
        }

        private fun buildChannel(extInf: ExtInfData, url: String): Channel {
            return Channel(
                id = extInf.tvgId ?: "ch_${url.hashCode()}",
                name = extInf.name,
                url = url,
                logo = extInf.tvgLogo,
                group = extInf.group,
                tvgId = extInf.tvgId,
                tvgName = extInf.tvgName,
                tvgLogo = extInf.tvgLogo,
                tvgShift = extInf.tvgShift,
                tvgCountry = extInf.tvgCountry,
                tvgLanguage = extInf.tvgLanguage,
                tvgUrl = extInf.tvgUrl,
                isRadio = extInf.isRadio,
                isLive = extInf.isLive,
            )
        }

        private data class ExtInfData(
            val duration: Int,
            val name: String,
            val group: String?,
            val tvgId: String?,
            val tvgName: String?,
            val tvgLogo: String?,
            val tvgShift: String?,
            val tvgCountry: String?,
            val tvgLanguage: String?,
            val tvgUrl: String?,
            val isRadio: Boolean,
            val isLive: Boolean,
        )
    }

    data class M3UParseResult(
        val channels: List<Channel>,
        val categories: List<Category>,
    )
}