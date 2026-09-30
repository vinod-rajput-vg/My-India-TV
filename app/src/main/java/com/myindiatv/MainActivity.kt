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
    private val channels: List<Channel> = when (categoryIndex) {
        0 -> listOf(
            Channel("Zee Cinema", "https://d1g8wgjurz8via.cloudfront.net/bpk-tv/NGCHD/default/NGCHD.m3u8"),
            Channel("Zee Cine Classic", "https://amg00862-amg00862c8-amgplt0173.playout.now3.amagi.tv/playlist/amg00862-amg00862c8-amgplt0173/playlist.m3u8"),
            Channel("Zee Classic", "http://107.167.16.138/zeeclassic/index.m3u8?token=test"),
            Channel("Zee Horror Nights", "https://amg00862-amg00862c7-amgplt0173.playout.now3.amagi.tv/playlist/amg00862-amg00862c7-amgplt0173/playlist.m3u8"),
            Channel("B4U Kadak", "https://streams.tangotv.in/B4UKADAK/ORIGIN/index.m3u8"),
            Channel("B4U Movies", "https://streams.tangotv.in/B4UMOVIES/ORIGIN/index.m3u8"),
            Channel("Colors Cineplex Bollywood", "http://202.70.146.135:8000/play/a058/index.m3u8"),
            Channel("Goldmines", "https://streams.tangotv.in/GOLDMINES/ORIGIN/index.m3u8"),
            Channel("Goldmines 2", "https://mumt03.tangotv.in/Dsly5z3HGOLDMINES2/index.m3u8"),
            Channel("Goldmines Bollywood", "https://mumt03.tangotv.in/Dsly5z3HGOLDMINESBOLLYWOOD/index.m3u8")
        )
        1 -> listOf(
            Channel("Discovery HD Hindi", "http://202.70.146.135:8000/play/a05z/index.m3u8"),
            Channel("Sony BBC Earth", "http://202.70.146.135:8000/play/a067/index.m3u8"),
            Channel("Animal Planet HD Hindi", "http://66.102.126.10:8000/play/a001/index.m3u8"),
            Channel("Nat Geo Wild HD", "http://202.70.146.135:8000/play/a05j/index.m3u8"),
            Channel("National Geographic HD", "http://202.70.146.135:8000/play/a05o/index.m3u8"),
            Channel("Gujarat  Wild TV", "https://newsliveindia.com:4433/wildlife/index.m3u8")
        )
        2 -> listOf(
            Channel("TV9 Bharatvarsh", "https://dyjmyiv3bp2ez.cloudfront.net/pub-iotv9hinjzgtpe/liveabr/playlist.m3u8"),
            Channel("TV9 Gujarati", "https://dyjmyiv3bp2ez.cloudfront.net/pub-iotv9guj3ki8lu/liveabr/playlist.m3u8"),
            Channel("TV9 Marathi", "https://dyjmyiv3bp2ez.cloudfront.net/pub-iotv9marlygv8h/liveabr/playlist.m3u8"),
            Channel("Zee 24 Kalak", "https://vg-zeefta.akamaized.net/ptnr-yupptv/title-zee24kalak/v1/manifest/611d79b11b77e2f571934fd80ca1413453772ac7/497f7199-758d-495d-9d2f-a5489231c428/14b7c8ec-16da-47f2-8d7e-5bbaec67b3e2/3.m3u8"),
            Channel("News Nation", "https://d3qs3d2rkhfqrt.cloudfront.net/out/v1/6cd2f649739a45ca9de1daf81cc7d0f2/index.m3u8"),
            Channel("Aaj Tak HD", "https://feeds.intoday.in/aajtak/api/aajtakhd/master.m3u8"),
            Channel("ABP News", "https://d1rc86nwwc9fag.cloudfront.net/vglive-sk-472500/abpnews/master.m3u8"),
            Channel("Times Now Navbharat HD", "https://yupprestreamliveus.akamaized.net/v1/vglive-sk-717514/main.m3u8"),
            Channel("India TV", "https://pl-indiatvnews.akamaized.net/out/v1/db79179b608641ceaa5a4d0dd0dca8da/index.m3u8"),
            Channel("Zee 24 Taas", "https://raw.githubusercontent.com/amazeyourself/adaptive-streams/refs/heads/main/streams/in/ZMCL/Zee24Taas.m3u8")
        )
        3 -> listOf(
            Channel("B4U Music", "https://cdn.pishow.tv/ott/live/415/master.m3u8")
        )
        else -> listOf(
            Channel("Nick Hindi", "http://103.185.24.134:3001/NICK/index.m3u8"),
            Channel("Sonic Hindi", "http://103.185.24.134:3001/SONIC/index.m3u8"),
            Channel("Hungama TV", "http://103.185.24.134:3001/HUNGAMA/index.m3u8"),
            Channel("Super Hungama", "http://103.185.24.134:3001/SUPER-HUNGAMA/index.m3u8"),
            Channel("Disney Channel (India) HD", "http://66.102.126.10:8000/play/a013/index.m3u8")
        )
    }

    private var selected = 0
    private var scrollRow = 0
    private val columns = 4

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
        val top = dp(42f)
        val cardW = (width - side * 2 - gapX * (columns - 1)) / columns.toFloat()
        val cardH = cardW * 0.75f
        val rowStep = cardH + gapY

        c.save()
        c.clipRect(0f, 0f, width.toFloat(), height.toFloat())

        channels.forEachIndexed { index, channel ->
            val row = index / columns
            val column = index % columns
            val left = side + column * (cardW + gapX)
            val topPos = top + (row - scrollRow) * rowStep
            val rect = RectF(left, topPos, left + cardW, topPos + cardH)

            if (rect.bottom < 0f || rect.top > height.toFloat()) return@forEachIndexed

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
            drawChannelName(c, channel.name, rect.centerX(), rect.centerY())
        }
        c.restore()
    }

    private fun drawChannelName(c: Canvas, name: String, centerX: Float, centerY: Float) {
        val maxWidth = dp(210f)
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

        val shownLines = lines.take(3)
        val lineHeight = dp(22f)
        val startY = centerY - (shownLines.size - 1) * lineHeight / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        shownLines.forEachIndexed { lineIndex, line ->
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
                if (selected % columns > 0) {
                    selected--
                    ensureSelectedVisible()
                    invalidate()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (selected % columns < columns - 1 && selected < channels.lastIndex) {
                    selected++
                    ensureSelectedVisible()
                    invalidate()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (selected >= columns) {
                    selected -= columns
                    ensureSelectedVisible()
                    invalidate()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val next = selected + columns
                if (next < channels.size) {
                    selected = next
                    ensureSelectedVisible()
                    invalidate()
                }
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

    private fun ensureSelectedVisible() {
        val rows = (channels.size + columns - 1) / columns
        val side = dp(42f)
        val gapX = dp(18f)
        val gapY = dp(22f)
        val top = dp(42f)
        val cardW = (width - side * 2 - gapX * (columns - 1)) / columns.toFloat()
        val cardH = cardW * 0.75f
        val rowStep = cardH + gapY
        val selectedRow = selected / columns
        val visibleRows = maxOf(1, ((height - top) / rowStep).toInt())

        if (selectedRow < scrollRow) {
            scrollRow = selectedRow
        } else if (selectedRow >= scrollRow + visibleRows) {
            scrollRow = selectedRow - visibleRows + 1
        }

        scrollRow = scrollRow.coerceIn(0, maxOf(0, rows - visibleRows))
    }

    override fun onKeyDown(k: Int, e: KeyEvent): Boolean {
        return if (handleDpadKey(k)) true else super.onKeyDown(k, e)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
