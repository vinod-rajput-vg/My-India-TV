package com.myindiatv

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

internal data class Category(
    val name: String,
    val iconUrl: String,
    val channelsUrl: String
)

/**
 * Loads Categories/categories.txt and each category's channel file URL.
 * Catalog text files stay in the repository instead of being bundled in the APK.
 */
internal object RemoteChannelRepository {
    private const val CATEGORIES_URL =
        "https://raw.githubusercontent.com/vinod-rajput-vg/My-India-TV/main/Categories/categories.txt"

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadCategories(context: Context, callback: (List<Category>) -> Unit) {
        executor.execute {
            val categories = runCatching {
                val lines = fetchText(CATEGORIES_URL).lines()
                val result = mutableListOf<Category>()
                var index = 0

                while (index < lines.size) {
                    val name = lines[index].trim()
                    if (name.isEmpty()) {
                        index++
                        continue
                    }

                    val iconUrl = lines.getOrNull(index + 1)?.trim().orEmpty()
                    val channelsUrl = lines.getOrNull(index + 2)?.trim().orEmpty()
                    if (iconUrl.startsWith("https://") &&
                        (channelsUrl.startsWith("https://") || channelsUrl.startsWith("http://"))
                    ) {
                        result += Category(name, iconUrl, channelsUrl)
                        index += 3
                    } else {
                        index++
                    }
                }
                result
            }.getOrDefault(emptyList())

            mainHandler.post { callback(categories) }
        }
    }

    fun loadChannels(context: Context, category: Category, callback: (List<Channel>) -> Unit) {
        executor.execute {
            val channels = runCatching {
                parseChannelText(fetchText(category.channelsUrl))
            }.getOrDefault(emptyList())

            mainHandler.post { callback(channels) }
        }
    }

    private fun fetchText(source: String): String {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(source).openConnection() as HttpURLConnection
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "My-India-TV/1.0")
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode} loading catalog")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection?.disconnect()
        }
    }

    /** Each repository record has channel name, icon URL, and stream URL on separate lines. */
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

        val records = lines.map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        return records
            .chunked(3)
            .filter { it.size == 3 && it[0].isNotBlank() && it[2].isNotBlank() }
            .map { Channel(name = it[0], iconUrl = it[1], streamUrl = it[2]) }
    }
}
