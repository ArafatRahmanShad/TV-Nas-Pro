package com.example.downloader

import android.net.Uri
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class ParsedMagnet(
    val rawUri: String,
    val infoHash: String,
    val displayName: String,
    val trackers: List<String>,
    val webSeeds: List<String>
)

object MagnetParser {
    fun isMagnetLink(uriStr: String): Boolean {
        return uriStr.trim().startsWith("magnet:", ignoreCase = true)
    }

    fun parse(uriStr: String): ParsedMagnet {
        val trimmed = uriStr.trim()
        var infoHash = ""
        var displayName = ""
        val trackers = mutableListOf<String>()
        val webSeeds = mutableListOf<String>()

        try {
            val queryString = if (trimmed.contains("?")) trimmed.substringAfter("?") else trimmed.removePrefix("magnet:")
            val queryParams = queryString.split("&")

            for (param in queryParams) {
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0]
                    val value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
                    when (key.lowercase()) {
                        "xt" -> {
                            if (value.startsWith("urn:btih:", ignoreCase = true)) {
                                infoHash = value.substringAfter("urn:btih:")
                            }
                        }
                        "dn" -> displayName = value
                        "tr" -> trackers.add(value)
                        "ws" -> webSeeds.add(value)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (displayName.isEmpty()) {
            displayName = if (infoHash.isNotEmpty()) "Torrent_${infoHash.take(8)}.torrent" else "Torrent_Download"
        }

        return ParsedMagnet(
            rawUri = trimmed,
            infoHash = infoHash,
            displayName = displayName,
            trackers = trackers,
            webSeeds = webSeeds
        )
    }
}
