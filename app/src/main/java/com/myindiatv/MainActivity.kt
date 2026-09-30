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
        channelView = ChannelView(this, index)
        setContentView(channelView)
        channelView?.requestFocus()
    }

    fun showHome() {
        channelView = null
        setContentView(homeView)
        homeView.requestFocus()
        homeView.invalidate()
    }

    fun openStream(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // No compatible player/browser is installed. Do nothing.
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (channelView?.handleDpadKey(event.keyCode) == true) return true
            if (::homeView.isInitialized && channelView == null && homeView.handleDpadKey(event.keyCode)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

private class HomeView(context: Context) : View(context) {
    private val names = listOf("Entertainment", "Imfotainment", "News", "Musics", "Kids")
    private val iconIds = intArrayOf(
        R.drawable.entertainment,
        R.drawable.imfotainment,
        R.drawable.news,
        R.drawable.music,
        R.drawable.kids
    )

    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
    }

    private val body = Paint(Paint.ANTI_ALIAS_FLAG)

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isAntiAlias = true
    }

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        isAntiAlias = true
    }

    private val bitmaps = arrayOfNulls<Bitmap>(5)

    private var selected = 0
    private var settings = false
    private var lastBackPressTime = 0L

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
        for (i in iconIds.indices) {
            bitmaps[i] = BitmapFactory.decodeResource(resources, iconIds[i])
        }
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.rgb(32, 32, 32))
        drawHome(c)
        if (settings) drawSettings(c)
    }

    private fun drawHome(c: Canvas) {
        val side = dp(52f)
        val gap = dp(22f)
        val top = dp(95f)
        val cw = (width - side * 2 - gap * 4) / 5f
        val ch = cw

        repeat(5) { i ->
            val left = side + i * (cw + gap)
            val r = RectF(left, top, left + cw, top + ch)

            iconPaint.style = Paint.Style.FILL
            iconPaint.color = Color.rgb(96, 96, 96)
            c.drawRoundRect(r, dp(28f), dp(28f), iconPaint)

            drawCategoryIcon(c, i, r)

            if (i == selected) {
                val outer = RectF(
                    r.left - dp(3f),
                    r.top - dp(3f),
                    r.right + dp(3f),
                    r.bottom + dp(3f)
                )
                selectionPaint.strokeWidth = dp(3f)
                c.drawRoundRect(outer, dp(31f), dp(31f), selectionPaint)
            }

            title.textAlign = Paint.Align.CENTER
            title.textSize = dp(22f)
            c.drawText(names[i], r.centerX(), r.bottom + dp(34f), title)
        }
    }

    private fun drawCategoryIcon(c: Canvas, index: Int, r: RectF) {
        val bitmap = bitmaps[index] ?: return
        if (bitmap.isRecycled) return

        val safe = RectF(
            r.left + dp(5f),
            r.top + dp(5f),
            r.right - dp(5f),
            r.bottom - dp(5f)
        )

        val scale = minOf(
            safe.width() / bitmap.width.toFloat(),
            safe.height() / bitmap.height.toFloat()
        ) * 1.08f

        val dstW = bitmap.width * scale
        val dstH = bitmap.height * scale
        val dst = RectF(
            safe.centerX() - dstW / 2f,
            safe.centerY() - dstH / 2f,
            safe.centerX() + dstW / 2f,
            safe.centerY() + dstH / 2f
        )
        c.drawBitmap(bitmap, null, dst, iconPaint)
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

    fun handleDpadKey(k: Int): Boolean {
        if (settings) {
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE || k == KeyEvent.KEYCODE_DPAD_CENTER) {
                settings = false
                invalidate()
                requestFocus()
            }
            return true
        }

        when (k) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                selected = (selected + names.size - 1) % names.size
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                selected = (selected + 1) % names.size
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                (context as? MainActivity)?.openCategory(selected)
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                settings = true
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime <= 2000L) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
                return true
            }
        }
        return false
    }

    override fun onKeyDown(k: Int, e: KeyEvent): Boolean {
        return if (handleDpadKey(k)) true else super.onKeyDown(k, e)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}

private class ChannelView(context: Context, categoryIndex: Int) : View(context) {
    private val categoryFiles = arrayOf(
        "entertainment_channels.txt",
        "imfotainment_channels.txt",
        "news_channels.txt",
        "music_channels.txt",
        "kids_channels.txt"
    )

    private val categoryNames = arrayOf(
        "Entertainment",
        "Imfotainment",
        "News",
        "Musics",
        "Kids"
    )

    private val channels: List<Channel> = loadChannels(categoryFiles[categoryIndex])
    private var selected = 0
    private val columns = 4
    private var lastBackPressTime = 0L

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(96, 96, 96)
    }

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = dp(3f)
        isAntiAlias = true
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.rgb(32, 32, 32))

        if (channels.isEmpty()) return

        val side = dp(42f)
        val gapX = dp(18f)
        val gapY = dp(22f)
        val top = dp(48f)
        val cardW = (width - side * 2 - gapX * (columns - 1)) / columns.toFloat()
        val cardH = cardW * 0.75f

        channels.forEachIndexed { index, channel ->
            val row = index / columns
            val column = index % columns
            val left = side + column * (cardW + gapX)
            val topPos = top + row * (cardH + gapY)
            val rect = RectF(left, topPos, left + cardW, topPos + cardH)

            c.drawRoundRect(rect, dp(14f), dp(14f), cardPaint)

            if (index == selected) {
                val outer = RectF(
                    rect.left - dp(3f),
                    rect.top - dp(3f),
                    rect.right + dp(3f),
                    rect.bottom + dp(3f)
                )
                c.drawRoundRect(outer, dp(17f), dp(17f), selectionPaint)
            }

            textPaint.textSize = dp(18f)
            drawChannelName(c, channel.name, rect.centerX(), rect.centerY(), cardH)
        }
    }

    private fun drawChannelName(c: Canvas, name: String, centerX: Float, centerY: Float, cardH: Float) {
        val maxWidth = dp(240f)
        val words = name.split(" ")
        val lines = mutableListOf<String>()
        var current = ""

        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (textPaint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current

        val lineHeight = dp(22f)
        val startY = centerY - (lines.size - 1) * lineHeight / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        lines.take(3).forEachIndexed { lineIndex, line ->
            c.drawText(line, centerX, startY + lineIndex * lineHeight, textPaint)
        }
    }

    fun handleDpadKey(k: Int): Boolean {
        if (channels.isEmpty()) {
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) {
                (context as? MainActivity)?.showHome()
                return true
            }
            return true
        }

        when (k) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                selected = if (selected % columns == 0) selected else selected - 1
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                selected = if (selected % columns == columns - 1 || selected == channels.lastIndex) selected else selected + 1
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                selected = maxOf(0, selected - columns)
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                selected = minOf(channels.lastIndex, selected + columns)
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                (context as? MainActivity)?.openStream(channels[selected].streamUrl)
                return true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                (context as? MainActivity)?.showHome()
                return true
            }
        }
        return false
    }

    override fun onKeyDown(k: Int, e: KeyEvent): Boolean {
        return if (handleDpadKey(k)) true else super.onKeyDown(k, e)
    }

    private fun loadChannels(fileName: String): List<Channel> {
        return try {
            val text = resources.assets.open(fileName).bufferedReader().use { it.readText() }
            val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            val result = mutableListOf<Channel>()
            var i = 0
            while (i + 1 < lines.size) {
                val name = lines[i]
                val url = lines[i + 1]
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    result += Channel(name = name, streamUrl = url)
                    i += 2
                } else {
                    i++
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
