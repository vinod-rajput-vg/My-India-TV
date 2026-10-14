package com.myindiatv

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

internal data class Category(
    val name: String,
    val iconUrl: String,
    val channelsUrl: String
)

internal object RemoteChannelRepository {
    private const val CATEGORY_URL = "https://raw.githubusercontent.com/vinod-rajput-vg/My-India-TV/main/Categories/categories.txt"
    private const val LIVE_CONFIG_URL = "https://my-india-tv-admin.vercel.app/api/live/config"
    private const val PREFS = "remote_catalog"
    private const val CATEGORIES_KEY = "categories_text"
    private const val LAST_SEEN_COMMIT_KEY = "last_seen_catalog_commit"
    private const val REPO_API_URL = "https://api.github.com/repos/vinod-rajput-vg/My-India-TV"
    private const val FALLBACK_WATCH_INTERVAL_MS = 60_000L
    private const val LAST_LIVE_EVENT_ID_KEY = "last_live_catalog_event_id"
    private const val TIMEOUT_MS = 10000
    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var watchRunnable: Runnable? = null
    @Volatile private var liveConnection: HttpURLConnection? = null
    @Volatile private var liveThread: Thread? = null
    @Volatile private var watchGeneration: Long = 0L

    fun loadCategories(context: Context, forceRefresh: Boolean = false, callback: (List<Category>) -> Unit) {
        executor.execute {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cachedText = prefs.getString(CATEGORIES_KEY, null)

            if (!forceRefresh && cachedText != null) {
                mainHandler.post { callback(parseCategories(cachedText)) }
                return@execute
            }

            val freshText = fetchText(if (forceRefresh) withCacheBuster(CATEGORY_URL) else CATEGORY_URL)
            if (freshText != null) {
                prefs.edit().putString(CATEGORIES_KEY, freshText).apply()
            }
            val resultText = if (freshText != null) freshText else cachedText
            mainHandler.post { callback(resultText?.let(::parseCategories).orEmpty()) }
        }
    }

    /**
     * Listen to Firebase Realtime Database Server-Sent Events for immediate admin updates.
     * GitHub commit polling remains as a low-frequency fallback for changes made outside
     * the admin panel, or while Firebase realtime delivery is unavailable.
     */
    fun startWatchingChanges(context: Context, onChanges: (Boolean, Set<String>) -> Unit) {
        val appContext = context.applicationContext
        stopWatchingChanges()
        val generation = watchGeneration

        val fallback = object : Runnable {
            override fun run() {
                if (generation != watchGeneration) return
                executor.execute {
                    try {
                        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        val latestSha = fetchLatestCommitSha()
                        if (!latestSha.isNullOrBlank()) {
                            val previousSha = prefs.getString(LAST_SEEN_COMMIT_KEY, null)
                            if (previousSha.isNullOrBlank()) {
                                prefs.edit().putString(LAST_SEEN_COMMIT_KEY, latestSha).apply()
                                mainHandler.post {
                                    if (generation == watchGeneration) onChanges(true, emptySet())
                                }
                            } else if (latestSha != previousSha) {
                                val changedPaths = fetchChangedPaths(previousSha, latestSha)
                                if (changedPaths != null) {
                                    prefs.edit().putString(LAST_SEEN_COMMIT_KEY, latestSha).apply()
                                    if (changedPaths.isNotEmpty()) {
                                        mainHandler.post {
                                            if (generation == watchGeneration) onChanges(false, changedPaths)
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        if (generation == watchGeneration) {
                            mainHandler.postDelayed(this, FALLBACK_WATCH_INTERVAL_MS)
                        }
                    }
                }
            }
        }
        watchRunnable = fallback
        mainHandler.post(fallback)

        val thread = Thread({ watchRealtimeUpdates(appContext, generation, onChanges) }, "MyIndiaTV-live-catalog")
        thread.isDaemon = true
        liveThread = thread
        thread.start()
    }

    fun stopWatchingChanges() {
        watchGeneration += 1L
        watchRunnable?.let { mainHandler.removeCallbacks(it) }
        watchRunnable = null
        liveConnection?.disconnect()
        liveConnection = null
        liveThread?.interrupt()
        liveThread = null
    }

    private fun watchRealtimeUpdates(
        context: Context,
        generation: Long,
        onChanges: (Boolean, Set<String>) -> Unit
    ) {
        while (generation == watchGeneration && !Thread.currentThread().isInterrupted) {
            var connection: HttpURLConnection? = null
            try {
                val configText = fetchText(LIVE_CONFIG_URL) ?: throw java.io.IOException("Live config unavailable")
                val config = JSONObject(configText)
                if (!config.optBoolean("enabled", false)) throw java.io.IOException("Realtime database is not configured")
                val databaseUrl = config.optString("databaseUrl").trim().trimEnd('/')
                if (!databaseUrl.startsWith("https://")) throw java.io.IOException("Invalid realtime database URL")

                connection = (URL("$databaseUrl/catalogUpdates/latest.json").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = 0
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "text/event-stream")
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("User-Agent", "My-India-TV/1.0")
                }
                liveConnection = connection
                if (connection.responseCode !in 200..299) {
                    throw java.io.IOException("Realtime stream returned HTTP ${connection.responseCode}")
                }

                var eventName = ""
                val eventData = StringBuilder()
                connection.inputStream.bufferedReader().use { reader ->
                    while (generation == watchGeneration && !Thread.currentThread().isInterrupted) {
                        val line = reader.readLine() ?: break
                        when {
                            line.startsWith("event:") -> eventName = line.substringAfter(':').trim()
                            line.startsWith("data:") -> {
                                if (eventData.isNotEmpty()) eventData.append('\n')
                                eventData.append(line.substringAfter(':').trimStart())
                            }
                            line.isEmpty() -> {
                                if (eventName == "put" || eventName == "patch") {
                                    consumeLiveEvent(context, generation, eventData.toString(), onChanges)
                                }
                                eventName = ""
                                eventData.setLength(0)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Reconnect below; the normal GitHub fallback continues independently.
            } finally {
                if (liveConnection === connection) liveConnection = null
                connection?.disconnect()
            }

            if (generation == watchGeneration && !Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(5_000L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    private fun consumeLiveEvent(
        context: Context,
        generation: Long,
        eventJson: String,
        onChanges: (Boolean, Set<String>) -> Unit
    ) {
        if (eventJson.isBlank() || generation != watchGeneration) return
        val root = try { JSONObject(eventJson) } catch (_: Exception) { return }
        val data = root.optJSONObject("data") ?: return
        val eventId = data.optString("revision").takeIf { it.isNotBlank() } ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousId = prefs.getString(LAST_LIVE_EVENT_ID_KEY, null)
        if (eventId == previousId) return
        prefs.edit().putString(LAST_LIVE_EVENT_ID_KEY, eventId).apply()

        // The first snapshot after installing/updating the app may represent updates
        // older than this process, so do a full sync once, then use targeted paths.
        if (previousId.isNullOrBlank()) {
            mainHandler.post {
                if (generation == watchGeneration) onChanges(true, emptySet())
            }
            return
        }

        val rawPaths = data.optJSONArray("paths")
        val changedPaths = buildSet {
            if (rawPaths != null) {
                for (i in 0 until rawPaths.length()) {
                    rawPaths.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        mainHandler.post {
            if (generation == watchGeneration) {
                if (changedPaths.isEmpty()) onChanges(true, emptySet())
                else onChanges(false, changedPaths)
            }
        }
    }

    private fun withCacheBuster(url: String): String =
        url + (if (url.contains("?")) "&" else "?") + "_live=" + System.currentTimeMillis()

    private fun fetchLatestCommitSha(): String? {
        val json = fetchApiJson("$REPO_API_URL/commits/main") ?: return null
        return try { JSONObject(json).optString("sha").takeIf { it.isNotBlank() } } catch (_: Exception) { null }
    }

    private fun fetchChangedPaths(baseSha: String, headSha: String): Set<String>? {
        val json = fetchApiJson("$REPO_API_URL/compare/$baseSha...$headSha") ?: return null
        return try {
            val files = JSONObject(json).optJSONArray("files") ?: JSONArray()
            buildSet {
                for (i in 0 until files.length()) {
                    val path = files.optJSONObject(i)?.optString("filename").orEmpty()
                    if (path.isNotBlank()) add(path)
                }
            }
        } catch (_: Exception) { null }
    }

    private fun fetchApiJson(endpoint: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
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

    fun refreshAllChannels(context: Context, categories: List<Category>, callback: (Set<String>) -> Unit = {}) {
        executor.execute {
            val refreshed = mutableSetOf<String>()
            categories.forEach { category ->
                if (refreshChannelCache(context, category)) refreshed.add(category.channelsUrl)
            }
            mainHandler.post { callback(refreshed) }
        }
    }

    fun refreshChangedChannels(context: Context, categories: List<Category>, changedPaths: Set<String>, callback: (Set<String>) -> Unit = {}) {
        executor.execute {
            val refreshed = mutableSetOf<String>()
            categories.forEach { category ->
                val filename = category.channelsUrl.substringAfterLast('/').substringBefore('?')
                if (changedPaths.any { it == "Category Channels/$filename" || it.endsWith("/$filename") }) {
                    if (refreshChannelCache(context, category)) refreshed.add(category.channelsUrl)
                }
            }
            mainHandler.post { callback(refreshed) }
        }
    }

    private fun refreshChannelCache(context: Context, category: Category): Boolean {
        val freshText = fetchText(withCacheBuster(category.channelsUrl)) ?: return false
        val cacheKey = "channels_" + category.channelsUrl.hashCode().toString()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(cacheKey, freshText).apply()
        return true
    }

    fun loadChannels(context: Context, category: Category, forceRefresh: Boolean = false, callback: (List<Channel>?) -> Unit) {
        executor.execute {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cacheKey = "channels_" + category.channelsUrl.hashCode().toString()
            val cachedText = prefs.getString(cacheKey, null)

            // Use cached data normally. A GitHub change event forces only this category
            // to fetch its new source file.
            if (!forceRefresh && cachedText != null) {
                val cachedChannels = parseChannelText(cachedText)
                mainHandler.post { callback(cachedChannels) }
                return@execute
            }

            // A null response means network failure; an empty body is a valid empty file.
            val freshText = fetchText(withCacheBuster(category.channelsUrl))
            if (freshText != null) {
                prefs.edit().putString(cacheKey, freshText).apply()
            }
            val channels = freshText?.let(::parseChannelText)
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
