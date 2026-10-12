package com.myindiatv

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Public, read-only Firestore REST reader. Channel documents remain readable by TV clients;
 * writes are protected by Firestore rules and require the admin UID.
 *
 * Replace both placeholders after creating the Firebase project. These values are client
 * configuration, not service-account credentials. Never put admin passwords or private keys here.
 */
internal object RemoteChannelRepository {
    private const val FIREBASE_PROJECT_ID = "my-india-tv-b06da"
    private const val FIREBASE_WEB_API_KEY = "AIzaSyBPEmslzjCYu5yXN04CgJXYrtu8ZchpfDI"
    private const val TIMEOUT_MS = 6000

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())


    /**
     * Fetch the category's source text file from GitHub on each category open.
     * The bundled asset remains the offline fallback; no app reinstall is needed for
     * edits pushed to the main branch.
     */
    fun loadTextFile(fileName: String, callback: (List<Channel>?) -> Unit) {
        executor.execute {
            val result = try {
                val safeName = fileName.replace("..", "")
                val endpoint = "https://raw.githubusercontent.com/vinod-rajput-vg/My-India-TV/main/app/src/main/assets/channels/$safeName"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("Accept", "text/plain")
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

    fun loadCategory(categoryName: String, callback: (List<Channel>?) -> Unit) {
        if (FIREBASE_PROJECT_ID.startsWith("REPLACE_WITH") ||
            FIREBASE_WEB_API_KEY.startsWith("REPLACE_WITH")) {
            callback(null)
            return
        }

        executor.execute {
            val result = try {
                val endpoint = "https://firestore.googleapis.com/v1/projects/" +
                    "$FIREBASE_PROJECT_ID/databases/(default)/documents/channels" +
                    "?pageSize=1000&key=$FIREBASE_WEB_API_KEY"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    if (connection.responseCode !in 200..299) {
                        null
                    } else {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val root = JSONObject(response)
                        val documents = root.optJSONArray("documents")
                        val channels = mutableListOf<Channel>()
                        if (documents != null) {
                            for (i in 0 until documents.length()) {
                                val document = documents.optJSONObject(i) ?: continue
                                val fields = document.optJSONObject("fields") ?: continue
                                val name = fields.optJSONObject("name")?.optString("stringValue").orEmpty().trim()
                                val category = fields.optJSONObject("category")?.optString("stringValue").orEmpty().trim()
                                val streamUrl = fields.optJSONObject("streamUrl")?.optString("stringValue").orEmpty().trim()
                                val iconUrl = fields.optJSONObject("logoUrl")?.optString("stringValue").orEmpty().trim()
                                val enabledField = fields.optJSONObject("enabled")
                                val enabled = enabledField?.optBoolean("booleanValue", true) ?: true
                                if (enabled && name.isNotEmpty() && streamUrl.isNotEmpty() && category == categoryName) {
                                    channels += Channel(name = name, streamUrl = streamUrl, iconUrl = iconUrl)
                                }
                            }
                        }
                        channels
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (_: Exception) {
                null
            }
            mainHandler.post { callback(result) }
        }
    }
}
