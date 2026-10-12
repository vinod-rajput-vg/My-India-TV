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

internal object RemoteChannelRepository {
    private const val CATEGORY_URL = "https://raw.githubusercontent.com/vinod-rajput-vg/My-India-TV/main/Categories/categories.txt"
    private const val PREFS = "remote_catalog"
    private const val CATEGORIES_KEY = "categories_text"
    private const val TIMEOUT_MS = 10000
    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadCategories(context: Context, callback: (List<Category>) -> Unit) {
        executor.execute {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cachedText = prefs.getString(CATEGORIES_KEY, null)
            val freshText = fetchText(CATEGORY_URL)
            val text = freshText ?: cachedText
            if (!freshText.isNullOrBlank()) prefs.edit().putString(CATEGORIES_KEY, freshText).apply()
            val result = text?.let(::parseCategories).orEmpty()
            mainHandler.post { callback(result) }
        }
    }

    fun loadChannels(context: Context, category: Category, callback: (List<Channel>?) -> Unit) {
        executor.execute {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cacheKey = "channels_" + category.channelsUrl.hashCode().toString()
            val cachedText = prefs.getString(cacheKey, null)
            val freshText = fetchText(category.channelsUrl)
            val text = freshText ?: cachedText
            if (!freshText.isNullOrBlank()) prefs.edit().putString(cacheKey, freshText).apply()
            val channels = text?.let(::parseChannelText)
            mainHandler.post { callback(channels) }
        }
    }

    private fun fetchText(endpoint: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Accept", "text/plain")
                setRequestProperty("User-Agent", "My-India-TV/1.0")
            }
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseCategories(text: String): List<Category> {
        val lines = text.lines().map { it.trim() }
        val result = mutableListOf<Category>()
        var i = 0
        while (i + 2 < lines.size) {
            val name = lines[i]
            val icon = lines[i + 1]
            val channels = lines[i + 2]
            if (name.isNotBlank() && icon.startsWith("https://") && channels.startsWith("https://")) {
                val displayName = if (name.equals("imfotainment", ignoreCase = true)) "Infotainment"
                    else name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                result += Category(displayName, icon, channels)
                i += 3
            } else {
                i++
            }
        }
        return result
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
