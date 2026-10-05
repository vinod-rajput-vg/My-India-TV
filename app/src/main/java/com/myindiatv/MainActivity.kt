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
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

private object RemoteLogoCache {
    private val cache = object : android.util.LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun request(url: String, onLoaded: (Bitmap?) -> Unit) {
        if (url.isBlank()) return
        synchronized(cache) { cache.get(url) }?.let { onLoaded(it); return }
        if (!loading.add(url)) return
        executor.execute {
            val bitmap = download(url)
            if (bitmap != null) synchronized(cache) { cache.put(url, bitmap) }
            loading.remove(url)
            mainHandler.post { onLoaded(bitmap) }
        }
    }

    private fun download(url: String): Bitmap? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "My-India-TV")
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        homeView = HomeView(this)
        setContentView(homeView)
        homeView.requestFocus()
    }

    fun openCategory(index: Int) {
        val category = categories.getOrNull(index) ?: return
        val view = ChannelView(this, loadChannels(this, category.fileName))
        channelView = view
        setContentView(view)
        view.requestFocus()
    }

    fun showHome() {
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (channelView?.handleDpadKey(event.keyCode) == true) return true
            if (::homeView.isInitialized && channelView == null && homeView.handleDpadKey(event.keyCode)) return true
        }
        return super.dispatchKeyEvent(event)
    }
}

private class HomeView(context: Context) : View(context) {
    private val names = categories.map { it.name }
    private val iconIds = categories.map { it.iconResId }.toIntArray()
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true; isAntiAlias = true }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; isAntiAlias = true }
    private val bitmaps = arrayOfNulls<Bitmap>(names.size)
    private val columns = 5
    private var selected = 0
    private var settings = false
    private var lastBackPressTime = 0L

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
        for (i in iconIds.indices) {
            val resId = iconIds[i]
            if (resId != 0) bitmaps[i] = BitmapFactory.decodeResource(resources, resId)
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
        if (settings) drawSettings(c)
    }

    private fun drawSettings(c: Canvas) {
        iconPaint.style = Paint.Style.FILL
        iconPaint.color = Color.argb(238, 0, 0, 0)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), iconPaint)
        val b = RectF(width * .25f, height * .18f, width * .75f, height * .82f)
        iconPaint.color = Color.rgb(20, 20, 20)
        c.drawRoundRect(b, dp(28f), dp(28f), iconPaint)
        title.textAlign = Paint.Align.LEFT
        title.textSize = dp(30f)
        c.drawText("Settings", b.left + dp(30f), b.top + dp(55f), title)
        title.textSize = dp(21f)
        c.drawText("Default Player", b.left + dp(30f), b.top + dp(110f), title)
        body.color = Color.LTGRAY
        body.textAlign = Paint.Align.LEFT
        body.textSize = dp(18f)
        c.drawText("Built-in / Web Player", b.left + dp(30f), b.top + dp(155f), body)
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
        if (settings) {
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE || k == KeyEvent.KEYCODE_DPAD_CENTER) {
                settings = false
                postInvalidateOnAnimation()
                requestFocus()
            }
            return true
        }
        when (k) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (selected > 0) selected--; postInvalidateOnAnimation(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (selected < names.lastIndex) selected++; postInvalidateOnAnimation(); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { adjacentRowTarget(selected, names.size, columns, -1)?.let { selected = it }; postInvalidateOnAnimation(); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { adjacentRowTarget(selected, names.size, columns, +1)?.let { selected = it }; postInvalidateOnAnimation(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { (context as? MainActivity)?.openCategory(selected); return true }
            KeyEvent.KEYCODE_MENU -> { settings = true; postInvalidateOnAnimation(); return true }
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
    private val channels: List<Channel>
) : View(context) {


    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true; isAntiAlias = true }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; isAntiAlias = true }
    private val bitmaps: Array<Bitmap?> = arrayOfNulls(channels.size)
    private val columns = 5
    private var selected = 0
    private var scrollOffset = 0f
    private var lastTouchY = 0f
    private var touchDragging = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
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
                    if (bitmap == null && channels[i].iconUrl.isNotBlank()) {
                        RemoteLogoCache.request(channels[i].iconUrl) {
                            bitmaps[i] = it
                            postInvalidateOnAnimation()
                        }
                    }
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

