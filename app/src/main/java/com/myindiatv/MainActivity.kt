package com.myindiatv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors


private object ChannelIconLoader {
    private val executor = Executors.newFixedThreadPool(4)
    private val cache = ConcurrentHashMap<String, Bitmap>()
    private const val CACHE_DIR = "channel_icons"

    fun load(source: String, view: View, onLoaded: (Bitmap) -> Unit) {
        val key = normalizeUrl(source)
        if (key.isEmpty()) return

        if (!key.startsWith("http://") && !key.startsWith("https://")) {
            val resourceName = key.substringAfterLast("/").substringBeforeLast(".").lowercase()
            val resourceId = view.resources.getIdentifier(resourceName, "drawable", view.context.packageName)
            if (resourceId != 0) {
                val bitmap = BitmapFactory.decodeResource(view.resources, resourceId)
                if (bitmap != null) view.post { onLoaded(bitmap) }
            }
            return
        }

        cache[key]?.let { bitmap ->
            view.post { onLoaded(bitmap) }
            return
        }

        executor.execute {
            val cachedBitmap = loadFromDisk(view.context, key)
            if (cachedBitmap != null) {
                cache[key] = cachedBitmap
                view.post { onLoaded(cachedBitmap) }
                return@execute
            }

            val bitmap = download(key)
            if (bitmap != null) {
                cache.putIfAbsent(key, bitmap)
                saveToDisk(view.context, key, bitmap)
                val cached = cache[key] ?: bitmap
                view.post { onLoaded(cached) }
            }
        }
    }

    fun invalidateChanged(context: Context, changedPaths: Set<String>, candidateUrls: Collection<String>) {
        val changedNames = changedPaths
            .filter { it.startsWith("Category Icons/") || it.startsWith("Channel Icons/") }
            .map { it.substringAfterLast('/').substringBefore('?').lowercase() }
            .toSet()
        if (changedNames.isEmpty()) return

        candidateUrls.forEach { source ->
            val url = normalizeUrl(source)
            if (url.isBlank()) return@forEach
            val fileName = Uri.parse(url).lastPathSegment.orEmpty().substringBefore('?').lowercase()
            if (fileName in changedNames) {
                cache.remove(url)
                try { cacheFile(context, url).delete() } catch (_: Exception) {}
            }
        }
    }

    private fun normalizeUrl(source: String): String {
        val value = source.trim()
        if (value.isEmpty()) return ""
        if (!value.startsWith("http://", ignoreCase = true) && !value.startsWith("https://", ignoreCase = true)) return value
        return value.replace(" ", "%20")
    }

    private fun cacheFile(context: Context, url: String): java.io.File {
        val directory = java.io.File(context.filesDir, CACHE_DIR)
        if (!directory.exists()) directory.mkdirs()
        return java.io.File(directory, hashUrl(url) + ".png")
    }

    private fun hashUrl(url: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun loadFromDisk(context: Context, url: String): Bitmap? {
        val file = cacheFile(context, url)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            java.io.FileInputStream(file).use { input -> BitmapFactory.decodeStream(input) }
        } catch (_: Exception) {
            try { file.delete() } catch (_: Exception) {}
            null
        }
    }

    private fun saveToDisk(context: Context, url: String, bitmap: Bitmap) {
        val file = cacheFile(context, url)
        if (file.exists() && file.length() > 0L) return
        val tempFile = java.io.File(file.parentFile, file.name + ".tmp")
        try {
            java.io.FileOutputStream(tempFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.flush()
            }
            if (!tempFile.renameTo(file)) tempFile.delete()
        } catch (_: Exception) {
            tempFile.delete()
        }
    }

    private fun download(url: String): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.useCaches = true
            connection.setRequestProperty("User-Agent", "My-India-TV/1.0")
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/png,image/jpeg,image/*,*/*;q=0.8")
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            BufferedInputStream(connection.inputStream).use { input -> BitmapFactory.decodeStream(input) }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
class MainActivity : Activity() {
    private lateinit var homeView: HomeView
    private var channelView: ChannelView? = null
    private var activeCategory: Category? = null
    private var categories: List<Category> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        homeView = HomeView(this, emptyList())
        setContentView(homeView)
        homeView.requestFocus()
        RemoteChannelRepository.loadCategories(this) { loaded ->
            categories = loaded
            homeView.setCategories(loaded)
            if (loaded.isEmpty()) {
                Toast.makeText(this, "No categories configured yet. Waiting for catalog updates.", Toast.LENGTH_LONG).show()
            }
            // Keep listening even when the catalog is empty, so a later admin add is visible.
            RemoteChannelRepository.startWatchingChanges(this) { fullRefresh, changedPaths ->
                handleCatalogChanges(fullRefresh, changedPaths)
            }
        }
    }

    fun openCategory(index: Int) {
        val category = categories.getOrNull(index) ?: return
        val view = ChannelView(this, emptyList())
        channelView = view
        activeCategory = category
        setContentView(view)
        view.requestFocus()

        refreshCategory(category, view)
    }

    private fun refreshCategory(category: Category, view: ChannelView, forceRefresh: Boolean = false) {
        // Fetch this category's configured remote TXT; use last successful cached data offline.
        RemoteChannelRepository.loadChannels(this, category, forceRefresh) { channels ->
            if (channelView !== view || activeCategory != category) return@loadChannels
            if (channels == null) {
                Toast.makeText(this, "Unable to load channels. Check your internet connection.", Toast.LENGTH_LONG).show()
                return@loadChannels
            }
            view.replaceChannels(channels)
        }
    }

    private fun handleCatalogChanges(fullRefresh: Boolean, changedPaths: Set<String>) {
        val categoryIconsChanged = changedPaths.any { it.startsWith("Category Icons/") }
        val channelIconsChanged = changedPaths.any { it.startsWith("Channel Icons/") }
        if (categoryIconsChanged || channelIconsChanged) {
            val knownIconUrls = buildList {
                if (categoryIconsChanged) addAll(categories.map { it.iconUrl })
                if (channelIconsChanged) addAll(channelView?.currentIconUrls().orEmpty())
            }
            ChannelIconLoader.invalidateChanged(this, changedPaths, knownIconUrls)
            if (categoryIconsChanged) homeView.reloadCategoryIcons()
            if (channelIconsChanged) channelView?.reloadChannelIcons()
        }
        val categoriesChanged = fullRefresh || changedPaths.contains("Categories/categories.txt")

        fun refreshAffectedChannels(targetCategories: List<Category>, paths: Set<String>, refreshAll: Boolean) {
            val onRefreshed: (Map<String, List<Channel>>) -> Unit = { refreshedChannels ->
                val current = activeCategory
                val visibleView = channelView
                if (current != null && visibleView != null) {
                    val updatedCategory = targetCategories.firstOrNull { it.channelsUrl == current.channelsUrl }
                        ?: targetCategories.firstOrNull { it.name.equals(current.name, ignoreCase = true) }
                    if (updatedCategory != null && refreshedChannels.containsKey(updatedCategory.channelsUrl)) {
                        // Apply the complete freshly fetched category list directly to the open
                        // screen. Do not issue a second network request after receiving the event.
                        activeCategory = updatedCategory
                        visibleView.replaceChannels(refreshedChannels[updatedCategory.channelsUrl].orEmpty())
                    }
                }
            }
            if (refreshAll) {
                RemoteChannelRepository.refreshAllChannels(this, targetCategories, onRefreshed)
            } else {
                RemoteChannelRepository.refreshChangedChannels(this, targetCategories, paths, onRefreshed)
            }
        }

        if (categoriesChanged) {
            val previousCategories = categories
            RemoteChannelRepository.loadCategories(this, forceRefresh = true) { updated ->
                categories = updated
                homeView.setCategories(updated)

                val pathsToRefresh = changedPaths.toMutableSet()
                if (fullRefresh) {
                    refreshAffectedChannels(updated, pathsToRefresh, refreshAll = true)
                } else {
                    // If the category configuration points to a new channel file, refresh
                    // that file only; unchanged category channel files remain cached.
                    updated.forEach { category ->
                        val previous = previousCategories.firstOrNull {
                            it.name.equals(category.name, ignoreCase = true)
                        } ?: previousCategories.firstOrNull { it.channelsUrl == category.channelsUrl }
                        if (previous == null || previous.channelsUrl != category.channelsUrl) {
                            pathsToRefresh.add("Category Channels/" + category.channelsUrl.substringAfterLast('/').substringBefore('?'))
                        }
                    }
                    refreshAffectedChannels(updated, pathsToRefresh, refreshAll = false)
                }

                val current = activeCategory
                if (current != null) {
                    val replacement = updated.firstOrNull { it.channelsUrl == current.channelsUrl }
                        ?: updated.firstOrNull { it.name.equals(current.name, ignoreCase = true) }
                    if (replacement == null) {
                        showHome()
                    } else {
                        activeCategory = replacement
                    }
                }
            }
        } else {
            refreshAffectedChannels(categories, changedPaths, refreshAll = false)
        }
    }

    fun showHome() {
        activeCategory = null
        channelView = null
        setContentView(homeView)
        homeView.requestFocus()
        homeView.postInvalidateOnAnimation()
    }

    fun openStream(url: String) {
        val streamUrl = url.trim()
        if (streamUrl.isEmpty()) {
            Toast.makeText(this, "Stream unavailable", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.parse(streamUrl)
        if (uri.scheme.isNullOrBlank() || uri.host.isNullOrBlank()) {
            Toast.makeText(this, "Invalid stream URL", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No compatible player installed", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Unable to open stream", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        RemoteChannelRepository.stopWatchingChanges()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (channelView?.handleDpadKey(event.keyCode) == true) return true
            if (::homeView.isInitialized && channelView == null && homeView.handleDpadKey(event.keyCode)) return true
        }
        return super.dispatchKeyEvent(event)
    }
}

private class HomeView(context: Context, initialCategories: List<Category>) : View(context) {
    private var categories: List<Category> = initialCategories
    private var names: List<String> = initialCategories.map { it.name }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true; isAntiAlias = true }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; isAntiAlias = true }
    private var bitmaps = arrayOfNulls<Bitmap>(categories.size)
    private val columns = 5
    private var selected = 0
    private var lastBackPressTime = 0L

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
        loadCategoryIcons()
    }

    fun setCategories(updated: List<Category>) {
        categories = updated
        names = updated.map { it.name }
        bitmaps = arrayOfNulls(updated.size)
        selected = selected.coerceIn(0, maxOf(0, updated.lastIndex))
        loadCategoryIcons()
        postInvalidateOnAnimation()
    }

    fun reloadCategoryIcons() {
        bitmaps = arrayOfNulls(categories.size)
        loadCategoryIcons()
        postInvalidateOnAnimation()
    }

    private fun loadCategoryIcons() {
        categories.forEachIndexed { index, category ->
            ChannelIconLoader.load(category.iconUrl, this) { bitmap ->
                if (index < bitmaps.size) {
                    bitmaps[index] = bitmap
                    postInvalidateOnAnimation()
                }
            }
        }
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.rgb(32, 32, 32))
        val side = dp(38f)
        val gap = dp(16f)
        val top = dp(95f)
        val rowGap = dp(50f)
        val cw = (width - side * 2 - gap * (columns - 1)) / columns.toFloat()
        repeat(names.size) { i ->
            val row = i / columns
            val column = i % columns
            val left = side + column * (cw + gap)
            val topPos = top + row * (cw + rowGap)
            val r = RectF(left, topPos, left + cw, topPos + cw)
            iconPaint.style = Paint.Style.FILL
            iconPaint.color = Color.rgb(96, 96, 96)
            c.drawRoundRect(r, dp(24f), dp(24f), iconPaint)
            val bitmap = bitmaps[i]
            if (bitmap != null && !bitmap.isRecycled) {
                val safe = RectF(r.left + dp(5f), r.top + dp(5f), r.right - dp(5f), r.bottom - dp(5f))
                val scale = minOf(safe.width() / bitmap.width.toFloat(), safe.height() / bitmap.height.toFloat()) * 1.08f
                val w = bitmap.width * scale
                val h = bitmap.height * scale
                val dst = RectF(safe.centerX() - w / 2, safe.centerY() - h / 2, safe.centerX() + w / 2, safe.centerY() + h / 2)
                val clipPath = Path().apply { addRoundRect(r, dp(24f), dp(24f), Path.Direction.CW) }
                c.save()
                c.clipPath(clipPath)
                c.drawBitmap(bitmap, null, dst, iconPaint)
                c.restore()
            }
            if (i == selected) {
                selectionPaint.strokeWidth = dp(3f)
                c.drawRoundRect(RectF(r.left - dp(3f), r.top - dp(3f), r.right + dp(3f), r.bottom + dp(3f)), dp(27f), dp(27f), selectionPaint)
            }
            title.textAlign = Paint.Align.CENTER
            title.textSize = dp(17f)
            c.drawText(names[i], r.centerX(), r.bottom + dp(30f), title)
        }
    }

    private fun adjacentRowTarget(index: Int, itemCount: Int, columns: Int, direction: Int): Int? {
        if (index !in 0 until itemCount || columns <= 0) return null
        val currentRow = index / columns
        val currentColumn = index % columns
        val targetRow = currentRow + direction
        if (targetRow < 0) return null
        val targetStart = targetRow * columns
        if (targetStart >= itemCount) return null
        val targetEnd = minOf(itemCount - 1, targetStart + columns - 1)
        return minOf(targetStart + currentColumn, targetEnd)
    }

    fun handleDpadKey(k: Int): Boolean {
        when (k) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (selected > 0) selected--; postInvalidateOnAnimation(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (selected < names.lastIndex) selected++; postInvalidateOnAnimation(); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { adjacentRowTarget(selected, names.size, columns, -1)?.let { selected = it }; postInvalidateOnAnimation(); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { adjacentRowTarget(selected, names.size, columns, +1)?.let { selected = it }; postInvalidateOnAnimation(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { (context as? MainActivity)?.openCategory(selected); return true }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime <= 2000L) (context as? Activity)?.finish() else { lastBackPressTime = now; Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show() }
                return true
            }
        }
        return false
    }

    override fun onKeyDown(k: Int, e: KeyEvent): Boolean = if (handleDpadKey(k)) true else super.onKeyDown(k, e)
    private fun dp(v: Float) = v * resources.displayMetrics.density
}

private class ChannelView(
    context: Context,
    initialChannels: List<Channel>
) : View(context) {
    private var channels: List<Channel> = initialChannels
    private var bitmaps: Array<Bitmap?> = arrayOfNulls(initialChannels.size)


    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true; isAntiAlias = true }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; isAntiAlias = true }

    private val columns = 5
    private var selected = 0
    private var scrollOffset = 0f
    private var lastTouchY = 0f
    private var touchDragging = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
        loadIcons()
    }

    fun replaceChannels(updated: List<Channel>) {
        val orderChanged = channels.map { it.name.trim() } != updated.map { it.name.trim() }
        channels = updated
        bitmaps = arrayOfNulls(updated.size)
        if (orderChanged) {
            selected = 0
            scrollOffset = 0f
        } else {
            selected = selected.coerceIn(0, maxOf(0, updated.lastIndex))
        }
        loadIcons()
        postInvalidateOnAnimation()
    }

    fun currentIconUrls(): List<String> = channels.map { it.iconUrl }

    fun reloadChannelIcons() {
        bitmaps = arrayOfNulls(channels.size)
        loadIcons()
        postInvalidateOnAnimation()
    }

    private fun loadIcons() {
        for (i in channels.indices) {
            val index = i
            ChannelIconLoader.load(channels[index].iconUrl, this) { bitmap ->
                if (index < bitmaps.size) {
                    bitmaps[index] = bitmap
                    postInvalidateOnAnimation()
                }
            }
        }
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.rgb(32, 32, 32))

        val side = dp(38f)
        val gap = dp(16f)
        val top = dp(70f)
        val rowGap = dp(50f)
        val cw = (width - side * 2 - gap * (columns - 1)) / columns.toFloat()
        val cardHeight = cw * 288f / 376f
        val rowHeight = cardHeight + rowGap
        val rowCount = (channels.size + columns - 1) / columns
        val contentBottom = top + rowCount * rowHeight - rowGap + dp(26f)
        val maxScroll = maxOf(0f, contentBottom - height + dp(12f))
        scrollOffset = scrollOffset.coerceIn(0f, maxScroll)

        c.save()
        c.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        c.translate(0f, -scrollOffset)

        if (rowCount > 0) {
            val firstRow = maxOf(0, ((scrollOffset - top) / rowHeight).toInt() - 1)
            val lastRow = minOf(rowCount - 1, ((scrollOffset + height - top) / rowHeight).toInt() + 1)

            for (row in firstRow..lastRow) {
                val start = row * columns
                val end = minOf(channels.size, start + columns)
                for (i in start until end) {
                    val column = i % columns
                    val left = side + column * (cw + gap)
                    val topPos = top + row * rowHeight
                    val r = RectF(left, topPos, left + cw, topPos + cardHeight)

                    iconPaint.style = Paint.Style.FILL
                    iconPaint.color = Color.rgb(96, 96, 96)
                    c.drawRoundRect(r, dp(24f), dp(24f), iconPaint)

                    val bitmap = bitmaps[i]
                    if (bitmap != null && !bitmap.isRecycled) {
                        val safe = RectF(r.left + dp(5f), r.top + dp(5f), r.right - dp(5f), r.bottom - dp(5f))
                        val scale = minOf(
                            safe.width() / bitmap.width.toFloat(),
                            safe.height() / bitmap.height.toFloat()
                        )
                        val targetWidth = bitmap.width * scale
                        val targetHeight = bitmap.height * scale
                        val dst = RectF(
                            safe.centerX() - targetWidth / 2,
                            safe.centerY() - targetHeight / 2,
                            safe.centerX() + targetWidth / 2,
                            safe.centerY() + targetHeight / 2
                        )
                        c.save()
                        c.clipPath(Path().apply { addRoundRect(r, dp(24f), dp(24f), Path.Direction.CW) })
                        c.drawBitmap(bitmap, null, dst, iconPaint)
                        c.restore()
                    }

                    if (i == selected) {
                        selectionPaint.strokeWidth = dp(3f)
                        c.drawRoundRect(RectF(r.left - dp(3f), r.top - dp(3f), r.right + dp(3f), r.bottom + dp(3f)), dp(27f), dp(27f), selectionPaint)
                    }

                    title.textAlign = Paint.Align.CENTER
                    title.textSize = dp(15f)
                    c.drawText(channels[i].name, r.centerX(), r.bottom + dp(26f), title)
                }
            }
        }
        c.restore()
    }

    private fun adjacentRowTarget(index: Int, itemCount: Int, columns: Int, direction: Int): Int? {
        if (index !in 0 until itemCount || columns <= 0) return null
        val currentRow = index / columns
        val currentColumn = index % columns
        val targetRow = currentRow + direction
        if (targetRow < 0) return null
        val targetStart = targetRow * columns
        if (targetStart >= itemCount) return null
        val targetEnd = minOf(itemCount - 1, targetStart + columns - 1)
        return minOf(targetStart + currentColumn, targetEnd)
    }

    private fun ensureSelectedVisible() {
        val side = dp(38f)
        val gap = dp(16f)
        val top = dp(70f)
        val rowGap = dp(50f)
        val cw = (width - side * 2 - gap * (columns - 1)) / columns.toFloat()
        val cardHeight = cw * 288f / 376f
        val rowHeight = cardHeight + rowGap
        val row = selected / columns
        val itemTop = top + row * rowHeight
        val itemBottom = itemTop + cardHeight + dp(26f)
        val visibleTop = scrollOffset
        val visibleBottom = scrollOffset + height

        if (itemTop < visibleTop) {
            scrollOffset = itemTop
        } else if (itemBottom > visibleBottom) {
            scrollOffset += itemBottom - visibleBottom
        }

        val rowCount = (channels.size + columns - 1) / columns
        val contentBottom = top + rowCount * rowHeight - rowGap + dp(26f)
        val maxScroll = maxOf(0f, contentBottom - height + dp(12f))
        scrollOffset = scrollOffset.coerceIn(0f, maxScroll)
    }

    private fun moveSelection(newIndex: Int) {
        if (newIndex in channels.indices) {
            selected = newIndex
            ensureSelectedVisible()
            postInvalidateOnAnimation()
        }
    }

    private fun scrollByPixels(delta: Float) {
        if (delta == 0f) return
        scrollOffset += delta
        val side = dp(38f)
        val gap = dp(16f)
        val top = dp(70f)
        val rowGap = dp(50f)
        val cw = (width - side * 2 - gap * (columns - 1)) / columns.toFloat()
        val cardHeight = cw * 288f / 376f
        val rowHeight = cardHeight + rowGap
        val rowCount = (channels.size + columns - 1) / columns
        val contentBottom = top + rowCount * rowHeight - rowGap + dp(26f)
        val maxScroll = maxOf(0f, contentBottom - height + dp(12f))
        scrollOffset = scrollOffset.coerceIn(0f, maxScroll)
        postInvalidateOnAnimation()
    }

    fun handleDpadKey(k: Int): Boolean {
        when (k) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (selected > 0) moveSelection(selected - 1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (selected < channels.lastIndex) moveSelection(selected + 1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                adjacentRowTarget(selected, channels.size, columns, -1)?.let { moveSelection(it) }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                adjacentRowTarget(selected, channels.size, columns, +1)?.let { moveSelection(it) }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (channels.isNotEmpty()) (context as? MainActivity)?.openStream(channels[selected].streamUrl)
                return true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                (context as? MainActivity)?.showHome()
                return true
            }
        }
        return false
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                touchDragging = false
                return true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                val dy = lastTouchY - event.y
                if (!touchDragging && kotlin.math.abs(dy) > dp(4f)) touchDragging = true
                if (touchDragging) scrollByPixels(dy)
                lastTouchY = event.y
                return true
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                touchDragging = false
                return true
            }
        }
        return true
    }

    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_SCROLL) {
            scrollByPixels(-event.getAxisValue(android.view.MotionEvent.AXIS_VSCROLL) * dp(40f))
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}

