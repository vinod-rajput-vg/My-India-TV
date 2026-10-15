package com.myindiatv

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal data class Category(
    val name: String,
    val iconUrl: String,
    val channelsUrl: String
)

internal object RemoteChannelRepository {
    private const val CATEGORY_URL = "https://raw.githubusercontent.com/vinod-rajput-vg/My-India-TV/main/Categories/categories.txt"
    private const val PREFS = "remote_catalog"
    private const val CATEGORIES_KEY = "categories_text"

    private fun withCacheBuster(url: String): String {
        val separator = if ("?" in url) "&" else "?"
        return "$url${separator}_cb=${System.currentTimeMillis()}"
    }
    private const val TIMEOUT_MS = 10000
    private val executor = Executors.newFixedThreadPool(10)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var watchRunnable: Runnable? = null
    @Volatile private var watchGeneration: Long = 0L
    private val watchCheckInFlight = AtomicBoolean(false)

    fun loadCategories(context: Context, forceRefresh: Boolean = false, callback: (List<Category>) -> Unit) {
        executor.execute {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cachedText = prefs.getString(CATEGORIES_KEY, null)

            if (!forceRefresh && cachedText != null) {
                mainHandler.post { callback(parseCategories(cachedText)) }
                return@execute
            }

            val freshText = fetchText(withCacheBuster(CATEGORY_URL))
            if (freshText != null) {
                val previousCategories = cachedText?.let(::parseCategories).orEmpty()
                val updatedCategories = parseCategories(freshText)
                if (cachedText != freshText) pruneRemovedCategoryCaches(context, previousCategories, updatedCategories)
                prefs.edit().putString(CATEGORIES_KEY, freshText).apply()
            }
            val resultText = if (freshText != null) freshText else cachedText
            mainHandler.post { callback(resultText?.let(::parseCategories).orEmpty()) }
        }
    }

    /** Refresh the actual TXT files on startup and when explicitly requested; no periodic polling. */
    fun startWatchingChanges(context: Context, onChanges: (Boolean, Set<String>) -> Unit) {
        val appContext = context.applicationContext
        stopWatchingChanges()
        val generation = watchGeneration
        val watcher = object : Runnable {
            override fun run() {
                if (generation != watchGeneration) return
                // The active check schedules the next run when it finishes; do not queue
                // another timer here, or foreground refreshes can create duplicate poll runs.
                if (!watchCheckInFlight.compareAndSet(false, true)) return
                executor.execute {
                    try {
                        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        val changedPaths = linkedSetOf<String>()

                        val latestCategoryText = fetchText(withCacheBuster(CATEGORY_URL))
                        if (latestCategoryText != null) {
                            val previousCategoryText = prefs.getString(CATEGORIES_KEY, null)
                            if (previousCategoryText == null) {
                                prefs.edit().putString(CATEGORIES_KEY, latestCategoryText).apply()
                            } else if (latestCategoryText != previousCategoryText) {
                                pruneRemovedCategoryCaches(appContext, parseCategories(previousCategoryText), parseCategories(latestCategoryText))
                                prefs.edit().putString(CATEGORIES_KEY, latestCategoryText).apply()
                                changedPaths.add("Categories/categories.txt")
                            }
                        }

                        val categoryText = latestCategoryText ?: prefs.getString(CATEGORIES_KEY, null)
                        val remoteCategories = categoryText?.let(::parseCategories).orEmpty()

                        // Warm category artwork immediately and channel artwork as each TXT file is fetched.
                        ChannelIconLoader.preloadAll(appContext, remoteCategories.map { it.iconUrl })

                        // Fetch category files concurrently. Sequential 10-second HTTP timeouts
                        // previously allowed one polling cycle to take over a minute.
                        val fetches = remoteCategories.map { category ->
                            category to CompletableFuture.supplyAsync({
                                fetchText(withCacheBuster(category.channelsUrl))
                            }, executor)
                        }
                        for ((category, future) in fetches) {
                            if (generation != watchGeneration) break
                            val freshText = try { future.get() } catch (_: Exception) { null } ?: continue
                            val filename = category.channelsUrl.substringAfterLast('/').substringBefore('?')
                            val cacheKey = "channels_" + category.channelsUrl.hashCode().toString()
                            val freshChannels = parseChannelText(freshText)
                            ChannelIconLoader.preloadAll(appContext, freshChannels.map { it.iconUrl })
                            val cachedText = prefs.getString(cacheKey, null)
                            if (cachedText == null) {
                                prefs.edit().putString(cacheKey, freshText).apply()
                            } else if (freshText != cachedText) {
                                ChannelIconLoader.reconcileChannels(appContext, parseChannelText(cachedText), freshChannels)
                                prefs.edit().putString(cacheKey, freshText).apply()
                                changedPaths.add("Category Channels/$filename")
                            }
                        }

                        if (changedPaths.isNotEmpty()) {
                            mainHandler.post {
                                if (generation == watchGeneration) onChanges(false, changedPaths)
                            }
                        }
                    } finally {
                        watchCheckInFlight.set(false)
                    }
                }
            }
        }
        watchRunnable = watcher
        mainHandler.post(watcher)
    }

    fun stopWatchingChanges() {
        watchGeneration += 1L
        watchRunnable?.let { mainHandler.removeCallbacks(it) }
        watchRunnable = null
    }

    /** Run the existing watcher immediately when the activity returns to the foreground. */
    fun checkForChangesNow() {
        mainHandler.post {
            val watcher = watchRunnable ?: return@post
            mainHandler.removeCallbacks(watcher)
            mainHandler.post(watcher)
        }
    }

    /**
     * Fetch and cache the full TXT file for each requested category.
     * Returning the parsed fresh list lets the active screen update from the same response,
     * instead of making a second request or accidentally reading an older cache entry.
     */
    fun refreshAllChannels(
        context: Context,
        categories: List<Category>,
        callback: (Map<String, List<Channel>>) -> Unit = {}
    ) {
        executor.execute {
            val refreshed = linkedMapOf<String, List<Channel>>()
            categories.forEach { category ->
                refreshChannelCache(context, category)?.let { refreshed[category.channelsUrl] = it }
            }
            mainHandler.post { callback(refreshed) }
        }
    }

    fun refreshChangedChannels(
        context: Context,
        categories: List<Category>,
        changedPaths: Set<String>,
        callback: (Map<String, List<Channel>>) -> Unit = {}
    ) {
        executor.execute {
            val refreshed = linkedMapOf<String, List<Channel>>()
            categories.forEach { category ->
                val filename = category.channelsUrl.substringAfterLast('/').substringBefore('?')
                if (changedPaths.any {
                    it.equals("Category Channels/$filename", ignoreCase = true) ||
                        it.substringAfterLast('/').equals(filename, ignoreCase = true)
                }) {
                    refreshChannelCache(context, category)?.let { refreshed[category.channelsUrl] = it }
                }
            }
            mainHandler.post { callback(refreshed) }
        }
    }

    private fun refreshChannelCache(context: Context, category: Category): List<Channel>? {
        val freshText = fetchText(withCacheBuster(category.channelsUrl)) ?: return null
        val cacheKey = "channels_" + category.channelsUrl.hashCode().toString()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousText = prefs.getString(cacheKey, null)
        val freshChannels = parseChannelText(freshText)
        if (previousText != null && previousText != freshText) {
            ChannelIconLoader.reconcileChannels(context, parseChannelText(previousText), freshChannels)
        }
        prefs.edit().putString(cacheKey, freshText).apply()
        return freshChannels
    }

    private fun pruneRemovedCategoryCaches(context: Context, previous: List<Category>, updated: List<Category>) {
        val updatedUrls = updated.map { it.channelsUrl }.toSet()
        val removed = previous.filter { it.channelsUrl !in updatedUrls }
        if (removed.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            removed.forEach { category ->
                val cacheKey = "channels_" + category.channelsUrl.hashCode().toString()
                val cachedChannels = prefs.getString(cacheKey, null)?.let(::parseChannelText).orEmpty()
                ChannelIconLoader.reconcileChannels(context, cachedChannels, emptyList())
                remove(cacheKey)
            }
        }.apply()
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
                if (cachedText != null && cachedText != freshText) {
                    ChannelIconLoader.reconcileChannels(context, parseChannelText(cachedText), parseChannelText(freshText))
                }
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
