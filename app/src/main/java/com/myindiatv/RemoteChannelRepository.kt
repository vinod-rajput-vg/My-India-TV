package com.myindiatv

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Loads category channel lists from the repository's public TXT files.
 * No Firebase, Firestore, or APK-asset fallback is used.
 */
internal object RemoteChannelRepository {
    private const val TIMEOUT_MS = 10000
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadTextFile(fileName: String, callback: (List<Channel>?) -> Unit) {
        executor.execute {
            val result = try {
                val safeName = fileName.substringAfterLast('/').takeIf {
                    it.matches(Regex("[A-Za-z0-9_-]+\\.txt"))
                } ?: throw IllegalArgumentException("Invalid channel filename")
                val endpoint = "https://raw.githubusercontent.com/vinod-rajput-vg/My-India-TV/main/app/src/main/assets/channels/$safeName"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("Accept", "text/plain")
                    instanceFollowRedirects = true
                }
                try {
                    if (connection.responseCode !in 200..299) null
                    else parseChannelText(connection.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                null
            }
            mainHandler.post { callback(result) }
        }
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

        val entries = lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        return entries.chunked(3)
            .filter { it.size == 3 && it[0].isNotBlank() && it[2].isNotBlank() }
            .map { Channel(name = it[0], iconUrl = it[1], streamUrl = it[2]) }
    }
}
