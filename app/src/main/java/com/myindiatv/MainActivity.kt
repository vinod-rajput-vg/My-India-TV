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
        val view = ChannelView(this, index)
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
    private val names = listOf("Entertainment", "Imfotainment", "News", "Musics", "Kids", "Drama", "Movies")
    private val iconIds = intArrayOf(R.drawable.entertainment, R.drawable.imfotainment, R.drawable.news, R.drawable.music, R.drawable.kids, R.drawable.drama, R.drawable.movies)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true; isAntiAlias = true }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; isAntiAlias = true }
    private val bitmaps = arrayOfNulls<Bitmap>(7)
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

private class ChannelView(context: Context, categoryIndex: Int) : View(context) {
    private val channels: List<Channel> = when (categoryIndex) {
        0 -> listOf(
            Channel("Star Gold HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/98858.ts", iconResId = iconId("star_gold_hd")),
            Channel("Star Gold 2 HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/157081.ts", iconResId = iconId("star_gold_2_hd")),
            Channel("Star Gold Romance", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/972.ts", iconResId = iconId("star_gold_romance")),
            Channel("Sony Max HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/98857.ts", iconResId = iconId("sony_max_hd")),
            Channel("Sony Max 2", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/973.ts", iconResId = iconId("sony_max_2")),
            Channel("SONY PIX HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/98846.ts", iconResId = iconId("sony_pix_hd")),
            Channel("&PICTURE HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/98856.ts", iconResId = iconId("and_picture_hd")),
            Channel("Star Utsav Movies", "http://51.75.127.199:3141/starutsavmovies/index.m3u8", iconResId = iconId("star_utsav_movies")),
            Channel("Star Gold Select HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/98859.ts", iconResId = iconId("star_gold_select_hd")),
            Channel("ZEE Cinema HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/98860.ts", iconResId = iconId("zeecinema")),
            Channel("ZEE Action", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/967.ts", iconResId = iconId("zee_action")),
            Channel("ZEE Bollywood", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/966.ts", iconResId = iconId("zee_bollywood")),
            Channel("ZEE Classic", "http://107.167.16.138/zeeclassic/index.m3u8?token=test", iconResId = iconId("zeeclassic")),
            Channel("ZEE Cine Classic", "https://amg00862-amg00862c8-amgplt0173.playout.now3.amagi.tv/playlist/amg00862-amg00862c8-amgplt0173/playlist.m3u8", iconResId = iconId("zeecineclassic")),
            Channel("&Xplore HD", "http://51.75.127.199:3141/andxplorehd/index.m3u8", iconResId = iconId("xplor_hd")),
            Channel("Colors Cineplex HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/98908.ts", iconResId = iconId("colors_cineplex_hd")),
            Channel("Colors Cineplex Bollywood", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/37074.ts", iconResId = iconId("colorscineplexbollywood")),
            Channel("Colors Cineplex Superhits", "http://51.75.127.199:3141/colorscineplexsuperhit/index.m3u8", iconResId = iconId("colors_cineplex_superhits")),
            Channel("Sony Wah", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/4077.ts", iconResId = iconId("sony_wah")),
            Channel("Shemaroo Bollywood", "https://prod-runn.cdn.runn.tv/shemaroo/stream/smrbol/playlist.m3u8", iconResId = iconId("shemaroo_bollywood")),
            Channel("B4U Kadak", "https://streams.tangotv.in/B4UKADAK/ORIGIN/index.m3u8", iconResId = iconId("b4u_kadak")),
            Channel("B4U Movies", "https://streams.tangotv.in/B4UMOVIES/ORIGIN/index.m3u8", iconResId = iconId("b4u_movies")),
            Channel("Goldmines", "https://streams.tangotv.in/GOLDMINES/ORIGIN/index.m3u8", iconResId = iconId("goldmines")),
            Channel("Goldmines 2", "https://mumt03.tangotv.in/Dsly5z3HGOLDMINES2/index.m3u8", iconResId = iconId("goldmines_2")),
            Channel("Goldmines Bollywood", "https://mumt03.tangotv.in/Dsly5z3HGOLDMINESBOLLYWOOD/index.m3u8", iconResId = iconId("goldmines_bollywood"))
        )
        1 -> listOf(
            Channel("Discovery HD Hindi", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/98872.ts", iconResId = iconId("discoveryhdhindi")),
            Channel("Sony BBC Earth HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/98871.ts", iconResId = iconId("sony_bbc_earth_hd")),
            Channel("TLC HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/98878.ts", iconResId = iconId("tlc_hd")),
            Channel("Animal Planet HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/98873.ts", iconResId = iconId("animal_planet_hd")),
            Channel("Nat Geo Wild HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/9395.ts", iconResId = iconId("nat_geo_wild_hd")),
            Channel("National Geographic HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/7343.ts", iconResId = iconId("national_geographic_hd")),
            Channel("Discovery Science Hindi", "http://27.116.22.53:5001/live/3453.m3u8", iconResId = iconId("discovery_science_hindi")),
            Channel("History TV18 HD", "https://n18syndication.akamaized.net/bpk-tv/History_TV18_Hindi_NW18_MOB/output01/master.m3u8", iconResId = iconId("history_18_hd")),
        )
        2 -> listOf(
            Channel("TV9 Bharatvarsh", "https://dyjmyiv3bp2ez.cloudfront.net/pub-iotv9hinjzgtpe/liveabr/playlist.m3u8", iconResId = iconId("tv9_bharatvarsh")),
            Channel("TV9 Gujarati", "https://dyjmyiv3bp2ez.cloudfront.net/pub-iotv9guj3ki8lu/liveabr/playlist.m3u8", iconResId = iconId("tv_9_gujarat")),
            Channel("TV9 Marathi", "https://dyjmyiv3bp2ez.cloudfront.net/pub-iotv9marlygv8h/liveabr/playlist.m3u8", iconResId = iconId("tv9_maharashtra")),
            Channel("Zee 24 Kalak", "https://vg-zeefta.akamaized.net/ptnr-yupptv/title-zee24kalak/v1/manifest/611d79b11b77e2f571934fd80ca1413453772ac7/497f7199-758d-495d-9d2f-a5489231c428/14b7c8ec-16da-47f2-8d7e-5bbaec67b3e2/3.m3u8", iconResId = iconId("zee_24_kalak")),
            Channel("Republic Bharat", "https://cdn.pishow.tv/ott/live/1053/master.m3u8", iconResId = iconId("republic_bharat")),
            Channel("News Nation", "https://d3qs3d2rkhfqrt.cloudfront.net/out/v1/6cd2f649739a45ca9de1daf81cc7d0f2/index.m3u8", iconResId = iconId("news_nation")),
            Channel("Aaj Tak HD", "https://feeds.intoday.in/aajtak/api/aajtakhd/master.m3u8", iconResId = iconId("aaj_tak")),
            Channel("ABP News", "https://d1rc86nwwc9fag.cloudfront.net/vglive-sk-472500/abpnews/master.m3u8", iconResId = iconId("abp_news_india")),
            Channel("Times Now Navbharat", "https://d3qs3d2rkhfqrt.cloudfront.net/out/v1/a5ee7c4e7a2a4b15a22a3bcf523b9776/index.m3u8", iconResId = iconId("times_now_navbharat")),
            Channel("India TV", "https://pl-indiatvnews.akamaized.net/out/v1/db79179b608641ceaa5a4d0dd0dca8da/index.m3u8", iconResId = iconId("india_tv")),
            Channel("Zee 24 Taas", "https://raw.githubusercontent.com/amazeyourself/adaptive-streams/refs/heads/main/streams/in/ZMCL/Zee24Taas.m3u8", iconResId = iconId("zee_24_taas"))
        )
        3 -> listOf(
            Channel("B4U Music", "https://streams.tangotv.in/B4UMUSIC/ORIGIN/index.m3u8", iconResId = iconId("b4u_music")),
            Channel("Shemaroo Filmy Gaane", "https://prod-runn.cdn.runn.tv/shemaroo/stream/smrfgn/playlist.m3u8", iconResId = iconId("shemaroo_filmy_gaane"))
        )
        4 -> listOf(
            Channel("Nick Hindi", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/205.ts", iconResId = iconId("nick_hindi")),
            Channel("Pogo Hindi", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/1918.ts", iconResId = iconId("pogo_hindi")),
            Channel("Sonic Hindi", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/207.ts", iconResId = iconId("sonic_hindi")),
            Channel("Cartoon Network Hindi", "http://27.116.22.53:5001/live/816.m3u8", iconResId = iconId("cartoon_network_hindi")),
            Channel("Cartoon Network HD Plus Hindi", "http://27.116.22.53:5001/live/3436.m3u8", iconResId = iconId("cartoon_network_hd_plus_hindi")),
            Channel("Discovery Kids Hindi", "http://27.116.22.53:5001/live/554.m3u8", iconResId = iconId("discovery_kids_hindi")),
            Channel("Hungama", "http://51.75.127.199:3141/hungama/index.m3u8", iconResId = iconId("hungama")),
            Channel("Super Hungama", "http://51.75.127.199:3141/superhungama/index.m3u8", iconResId = iconId("superhungama")),
            Channel("Disney Channel", "http://51.75.127.199:3141/disneychannel/index.m3u8", iconResId = iconId("disney_channel"))
        )
        5 -> listOf(
            Channel("Zee TV HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/161.ts", iconResId = iconId("zee_tv")),
            Channel("Star Plus HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/221.ts", iconResId = iconId("star_plus_hd")),
            Channel("SONY SAB HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/98853.ts", iconResId = iconId("sony_sab_hd")),
            Channel("Colors HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/98851.ts", iconResId = iconId("colors")),
            Channel("Colors Gujarati", "https://raw.githubusercontent.com/amazeyourself/adaptive-streams/refs/heads/main/streams/in/YuppTV/ColorsGujarati.m3u8", iconResId = iconId("colors_gujarati")),
            Channel("Star Utsav", "http://51.75.127.199:3141/starutsav/index.m3u8", iconResId = iconId("star_utsav")),
            Channel("Zing TV", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/970.ts", iconResId = iconId("zing_tv")),
            Channel("&TV HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/98852.ts", iconResId = iconId("and_tv_hd"))
            Channel("Sony Television HD", "http://skyfilex.fun:80/live/5axHnPxfJG/automatic8meet/98854.ts", iconResId = iconId("sony_television_hd"))
        )
        6 -> listOf(
            Channel("Underworld: Rise of the Lycans Hindi", "https://st9.febspot.com/videos/945000/945084/945084_720p.mp4", iconResId = iconId("underworld_rise_of_the_lycans_hindi"))
        )
        else -> emptyList()
    }

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
        for (i in channels.indices) {
            val resId = channels[i].iconResId
            if (resId != 0) bitmaps[i] = BitmapFactory.decodeResource(resources, resId)
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

private fun iconId(name: String): Int = try {
    com.myindiatv.R.drawable::class.java.getField(name).getInt(null)
} catch (_: Exception) { 0 }
