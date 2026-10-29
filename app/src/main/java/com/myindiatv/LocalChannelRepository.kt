package com.myindiatv

import android.content.Context
import android.os.Handler
import android.os.Looper

internal data class Category(val name: String, val iconUrl: String, val channelsUrl: String)

/** Loads the catalog packaged in the APK. Catalog updates are made by rebuilding the app. */
internal object LocalChannelRepository {
    private const val CATALOG_ASSET = "catalog.txt"
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun readCatalog(context: Context): String =
        context.assets.open(CATALOG_ASSET).bufferedReader().use { it.readText() }

    fun loadCategories(context: Context, callback: (List<Category>) -> Unit) {
        val categories = runCatching {
            val text = readCatalog(context)
            Regex("""@@CATEGORY:([^@]+)@@\n([^\n]+)\n([^\n]+)\n@@CHANNELS@@""")
                .findAll(text).map { match ->
                    Category(match.groupValues[2], match.groupValues[3], match.groupValues[1])
                }.toList()
        }.getOrDefault(emptyList())
        mainHandler.post { callback(categories) }
    }

    fun loadChannels(context: Context, category: Category, callback: (List<Channel>) -> Unit) {
        val channels = runCatching {
            val text = readCatalog(context)
            val section = Regex("""@@CATEGORY:${Regex.escape(category.channelsUrl)}@@\n[\s\S]*?\n@@CHANNELS@@\n([\s\S]*?)\n@@END@@""")
                .find(text)?.groupValues?.getOrNull(1).orEmpty()
            parseChannelText(section)
        }.getOrDefault(emptyList())
        mainHandler.post { callback(channels) }
    }

    private fun parseChannelText(text: String): List<Channel> {
        val lines = text.lines()
        if (lines.any { it.trim().startsWith("#EXTINF:", ignoreCase = true) }) {
            val result = mutableListOf<Channel>()
            var name: String? = null
            var iconUrl = ""
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.equals("#EXTM3U", ignoreCase = true)) continue
                if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    val comma = line.indexOf(',')
                    if (comma < 0) continue
                    val attributes = line.substring(0, comma)
                    name = line.substring(comma + 1).trim()
                    iconUrl = Regex("""tvg-logo="([^"]*)"""", RegexOption.IGNORE_CASE)
                        .find(attributes)?.groupValues?.getOrNull(1).orEmpty()
                } else if (!line.startsWith("#") && !name.isNullOrBlank()) {
                    result += Channel(name = name, streamUrl = line, iconUrl = iconUrl)
                    name = null
                    iconUrl = ""
                }
            }
            return result
        }
        return lines.map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .chunked(3)
            .filter { it.size == 3 && it[0].isNotBlank() && it[2].isNotBlank() }
            .map { Channel(name = it[0], iconUrl = it[1], streamUrl = it[2]) }
    }
}
