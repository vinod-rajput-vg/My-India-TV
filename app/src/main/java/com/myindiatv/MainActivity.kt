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
        homeView.postInvalidateOnAnimation()
    }

    fun openStream(url: String) {
        if (url.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No compatible player installed", Toast.LENGTH_SHORT).show()
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
    private val names = listOf("Entertainment", "Imfotainment", "News", "Musics", "Kids", "Movies")
    private val iconIds = intArrayOf(R.drawable.entertainment, R.drawable.imfotainment, R.drawable.news, R.drawable.music, R.drawable.kids, R.drawable.movies)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true; isAntiAlias = true }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; isAntiAlias = true }
    private val bitmaps = arrayOfNulls<Bitmap>(6)
    private val columns = 5
    private var selected = 0
    private var settings = false
    private var lastBackPressTime = 0L

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
        for (i in iconIds.indices) bitmaps[i] = BitmapFactory.decodeResource(resources, iconIds[i])
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
            KeyEvent.KEYCODE_DPAD_LEFT -> { selected = (selected + names.size - 1) % names.size; postInvalidateOnAnimation(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { selected = (selected + 1) % names.size; postInvalidateOnAnimation(); return true }
            KeyEvent.KEYCODE_DPAD_UP -> {
                adjacentRowTarget(selected, names.size, columns, -1)?.let { selected = it }
                postInvalidateOnAnimation()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                adjacentRowTarget(selected, names.size, columns, +1)?.let { selected = it }
                postInvalidateOnAnimation()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { (context as? MainActivity)?.openCategory(selected); return true }
            KeyEvent.KEYCODE_MENU -> { settings = true; postInvalidateOnAnimation(); return true }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime <= 2000L) (context as? Activity)?.finish() else {
                    lastBackPressTime = now
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
                return true
            }
        }
        return false
    }

    override fun onKeyDown(k: Int, e: KeyEvent): Boolean = if (handleDpadKey(k)) true else super.onKeyDown(k, e)
    private fun dp(v: Float) = v * resources.displayMetrics.density
}

private class ChannelView(context: Context, categoryIndex: Int) : View(context) {
    private val channels: List<Channel> = when (categoryIndex) {
        0 -> listOf(
            Channel("Star Gold 2 HD", "http://202.70.146.135:8000/play/a04q/index.m3u8", iconResId = iconId("star_gold_2_hd")),
            Channel("Zee Cinema", "https://d1g8wgjurz8via.cloudfront.net/bpk-tv/NGCHD/default/NGCHD.m3u8", iconResId = iconId("zeecinema")),
            Channel("Zee Cine Classic", "https://amg00862-amg00862c8-amgplt0173.playout.now3.amagi.tv/playlist/amg00862-amg00862c8-amgplt0173/playlist.m3u8", iconResId = iconId("zeecineclassic")),
            Channel("Zee Horror Nights", "https://amg00862-amg00862c7-amgplt0173.playout.now3.amagi.tv/playlist/amg00862-amg00862c7-amgplt0173/playlist.m3u8", iconResId = iconId("zeehorrornights")),
            Channel("Colors Cineplex Bollywood", "http://202.70.146.135:8000/play/a058/index.m3u8", iconResId = iconId("colorscineplexbollywood")),
            Channel("Shemaroo Bollywood", "https://prod-runn.cdn.runn.tv/shemaroo/stream/smrbol/playlist.m3u8", iconResId = iconId("shemaroo_bollywood")),
            Channel("B4U Kadak", "https://streams.tangotv.in/B4UKADAK/ORIGIN/index.m3u8", iconResId = iconId("b4u_kadak")),
            Channel("B4U Movies", "https://streams.tangotv.in/B4UMOVIES/ORIGIN/index.m3u8", iconResId = iconId("b4u_movies")),
            Channel("Goldmines", "https://streams.tangotv.in/GOLDMINES/ORIGIN/index.m3u8", iconResId = iconId("goldmines")),
            Channel("Goldmines 2", "https://mumt03.tangotv.in/Dsly5z3HGOLDMINES2/index.m3u8", iconResId = iconId("goldmines_2")),
            Channel("Goldmines Bollywood", "https://mumt03.tangotv.in/Dsly5z3HGOLDMINESBOLLYWOOD/index.m3u8", iconResId = iconId("goldmines_bollywood"))
        )
        1 -> listOf(
            Channel("Discovery HD Hindi", "http://202.70.146.135:8000/play/a05z/index.m3u8", iconResId = iconId("discoveryhdhindi")),
            Channel("Sony BBC Earth", "http://202.70.146.135:8000/play/a067/index.m3u8", iconResId = iconId("sonybbcearth")),
            Channel("Animal Planet Hindi", "http://27.116.22.53:5001/live/566.m3u8", iconResId = iconId("animal_planet_hindi")),
            Channel("Nat Geo Wild HD", "http://202.70.146.135:8000/play/a05j/index.m3u8", iconResId = iconId("nat_geo_wild_hd")),
            Channel("National Geographic HD", "http://202.70.146.135:8000/play/a05o/index.m3u8", iconResId = iconId("national_geographic_hd")),
            Channel("Discovery Science Hindi", "http://27.116.22.53:5001/live/3453.m3u8", iconResId = iconId("discovery_science_hindi")),
            Channel("History TV18 HD Hindi", "https://n18syndication.akamaized.net/bpk-tv/History_TV18_Hindi_NW18_MOB/output01/master.m3u8", iconResId = iconId("history_18_hindi")),
            Channel("Gujarat Wild TV", "https://newsliveindia.com:4433/wildlife/index.m3u8", iconResId = iconId("gujarat_wild_tv"))
        )
        2 -> listOf(
            Channel("TV9 Bharatvarsh", "https://dyjmyiv3bp2ez.cloudfront.net/pub-iotv9hinjzgtpe/liveabr/playlist.m3u8", iconResId = iconId("tv9_bharatvarsh")),
            Channel("TV9 Gujarati", "https://dyjmyiv3bp2ez.cloudfront.net/pub-iotv9guj3ki8lu/liveabr/playlist.m3u8", iconResId = iconId("tv_9_gujarat")),
            Channel("TV9 Marathi", "https://dyjmyiv3bp2ez.cloudfront.net/pub-iotv9marlygv8h/liveabr/playlist.m3u8", iconResId = iconId("tv9_maharashtra")),
            Channel("Zee 24 Kalak", "https://vg-zeefta.akamaized.net/ptnr-yupptv/title-zee24kalak/v1/manifest/611d79b11b77e2f571934fd80ca1413453772ac7/497f7199-758d-495d-9d2f-a5489231c428/14b7c8ec-16da-47f2-8d7e-5bbaec67b3e2/3.m3u8", iconResId = iconId("zee_24_kalak")),
            Channel("News Nation", "https://d3qs3d2rkhfqrt.cloudfront.net/out/v1/6cd2f649739a45ca9de1daf81cc7d0f2/index.m3u8", iconResId = iconId("news_nation")),
            Channel("Aaj Tak HD", "https://feeds.intoday.in/aajtak/api/aajtakhd/master.m3u8", iconResId = iconId("aaj_tak")),
            Channel("ABP News", "https://d1rc86nwwc9fag.cloudfront.net/vglive-sk-472500/abpnews/master.m3u8", iconResId = iconId("abp_news_india")),
            Channel("Times Now Navbharat HD", "https://yupprestreamliveus.akamaized.net/v1/vglive-sk-717514/main.m3u8", iconResId = iconId("times_now_navbharat")),
            Channel("India TV", "https://pl-indiatvnews.akamaized.net/out/v1/db79179b608641ceaa5a4d0dd0dca8da/index.m3u8", iconResId = iconId("india_tv")),
            Channel("Zee 24 Taas", "https://raw.githubusercontent.com/amazeyourself/adaptive-streams/refs/heads/main/streams/in/ZMCL/Zee24Taas.m3u8", iconResId = iconId("zee_24_taas"))
        )
        3 -> listOf(
            Channel("B4U Music", "https://cdn.pishow.tv/ott/live/415/master.m3u8", iconResId = iconId("b4u_music")),
            Channel("MTV", "https://da86m1sqpm3o0.cloudfront.net/28072023/smil:mtvindia.smil/playlist.m3u8", iconResId = iconId("mtv")),
            Channel("MTV HD", "http://27.116.22.53:5001/live/1145.m3u8", iconResId = iconId("mtv_hd_plus")),
            Channel("Music India", "http://27.116.22.53:5001/live/250.m3u8", iconResId = iconId("music_india")),
            Channel("Shemaroo Filmy Gaane", "https://prod-runn.cdn.runn.tv/shemaroo/stream/smrfgn/playlist.m3u8", iconResId = iconId("shemaroo_filmy_gaane"))
        )
        4 -> listOf(
            Channel("Nick Hindi", "http://103.185.24.134:3001/NICK/index.m3u8", iconResId = iconId("nick_hindi")),
            Channel("Sonic Hindi", "http://103.185.24.134:3001/SONIC/index.m3u8", iconResId = iconId("sonic_hindi")),
            Channel("Pogo Hindi", "http://27.116.22.53:5001/live/559.m3u8", iconResId = iconId("pogo_hindi")),
            Channel("Cartoon Network Hindi", "http://27.116.22.53:5001/live/816.m3u8", iconResId = iconId("cartoon_network_hindi")),
            Channel("Cartoon Network HD+ Hindi", "http://27.116.22.53:5001/live/3436.m3u8", iconResId = iconId("cartoon_network_hd_plus_hindi")),
            Channel("Discovery Kids Hindi", "http://27.116.22.53:5001/live/554.m3u8", iconResId = iconId("discovery_kids_hindi")),
            Channel("Hungama TV", "http://103.185.24.134:3001/HUNGAMA/index.m3u8", iconResId = iconId("hungama")),
            Channel("Super Hungama", "http://103.185.24.134:3001/SUPER-HUNGAMA/index.m3u8", iconResId = iconId("superhungama")),
            Channel("Disney Channel (India) HD", "http://66.102.126.10:8000/play/a013/index.m3u8", iconResId = iconId("disney_channel"))
        )
        else -> listOf(
            Channel("Underworld: Rise of the Lycans Hindi", "https://st9.febspot.com/videos/945000/945084/945084_720p.mp4", iconResId = iconId("underworld_rise_of_the_lycans_hindi"))
        )
    }

    private var selected = 0
    private var scrollRow = 0
    private val columns = 4
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.rgb(96, 96, 96) }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true; isAntiAlias = true }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = dp(3f); isAntiAlias = true }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
    private val bitmapCache = HashMap<Int, Bitmap>()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        requestFocus()
        for (channel in channels) {
            val id = channel.iconResId
            if (id != 0 && !bitmapCache.containsKey(id)) BitmapFactory.decodeResource(resources, id)?.let { bitmapCache[id] = it }
        }
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.rgb(32, 32, 32))
        if (channels.isEmpty()) return
        val side = dp(48f)
        val gapX = dp(18f)
        val gapY = dp(26f)
        val top = dp(42f)
        val availableWidth = width - side * 2 - gapX * (columns - 1)
        val cardW = (availableWidth / columns.toFloat()) * 0.93f
        val gridWidth = cardW * columns + gapX * (columns - 1)
        val gridLeft = (width - gridWidth) / 2f
        val cardH = cardW * 0.75f
        val rowStep = cardH + gapY

        c.save()
        c.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        channels.forEachIndexed { index, channel ->
            val row = index / columns
            val column = index % columns
            val left = gridLeft + column * (cardW + gapX)
            val topPos = top + (row - scrollRow) * rowStep
            val rect = RectF(left, topPos, left + cardW, topPos + cardH)
            if (rect.bottom < 0f || rect.top > height.toFloat()) return@forEachIndexed
            c.drawRoundRect(rect, dp(12f), dp(12f), cardPaint)
            drawChannelIcon(c, channel, rect)
            if (index == selected) c.drawRoundRect(RectF(rect.left - dp(3f), rect.top - dp(3f), rect.right + dp(3f), rect.bottom + dp(3f)), dp(15f), dp(15f), selectionPaint)
            textPaint.textSize = dp(16f)
            c.drawText(channel.name, rect.centerX(), rect.bottom + dp(24f), textPaint)
        }
        c.restore()
    }

    private fun drawChannelIcon(c: Canvas, channel: Channel, r: RectF) {
        val bitmap = bitmapCache[channel.iconResId] ?: return
        if (bitmap.isRecycled) return
        val safe = RectF(r.left + dp(8f), r.top + dp(8f), r.right - dp(8f), r.bottom - dp(8f))
        val scale = minOf(safe.width() / bitmap.width.toFloat(), safe.height() / bitmap.height.toFloat())
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        c.drawBitmap(bitmap, null, RectF(safe.centerX() - w / 2f, safe.centerY() - h / 2f, safe.centerX() + w / 2f, safe.centerY() + h / 2f), iconPaint)
    }

    private fun iconId(name: String): Int = resources.getIdentifier(name, "drawable", context.packageName)

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
        val maxIndex = channels.lastIndex
        if (maxIndex < 0) return true
        when (k) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (selected < maxIndex) selected++; ensureSelectedVisible(); postInvalidateOnAnimation(); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (selected > 0) selected--; ensureSelectedVisible(); postInvalidateOnAnimation(); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                adjacentRowTarget(selected, channels.size, columns, +1)?.let { selected = it }
                ensureSelectedVisible()
                postInvalidateOnAnimation()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                adjacentRowTarget(selected, channels.size, columns, -1)?.let { selected = it }
                ensureSelectedVisible()
                postInvalidateOnAnimation()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { (context as? MainActivity)?.openStream(channels[selected].streamUrl); return true }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { (context as? MainActivity)?.showHome(); return true }
        }
        return false
    }

    private fun ensureSelectedVisible() {
        val row = selected / columns
        val cardW = ((width - dp(48f) * 2 - dp(18f) * (columns - 1)) / columns.toFloat()) * 0.93f
        val rowStep = cardW * 0.75f + dp(26f)
        val visibleRows = maxOf(1, ((height - dp(42f)) / rowStep).toInt())
        if (row < scrollRow) scrollRow = row
        else if (row >= scrollRow + visibleRows) scrollRow = row - visibleRows + 1
        scrollRow = scrollRow.coerceIn(0, channels.lastIndex / columns)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
