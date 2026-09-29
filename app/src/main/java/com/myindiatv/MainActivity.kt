package com.myindiatv

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        setContentView(HomeView(this))
    }
}

private class HomeView(context: Context) : View(context) {
    private val names = listOf("Entertainment", "Imfotainment", "News", "Musics", "Kids")
    private val accents = listOf(Color.rgb(255,35,70), Color.rgb(0,180,255), Color.rgb(255,190,30), Color.rgb(235,20,230), Color.rgb(0,240,100))
    private val emojiSets = listOf(
        listOf("🎬" to Pair(-28f, -18f), "🍿" to Pair(28f, -2f), "⭐" to Pair(42f, 35f)),
        listOf("🌍" to Pair(-22f, -4f), "🔍" to Pair(28f, 28f)),
        listOf("📰" to Pair(0f, 0f)),
        listOf("🎧" to Pair(-20f, -2f), "🎵" to Pair(30f, 25f)),
        listOf("👦" to Pair(8f, -22f), "🧸" to Pair(-30f, 28f), "⭐" to Pair(34f, 32f))
    )
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var selected = 0
    private var settings = false

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.BLACK)
        drawHome(c)
        if (settings) drawSettings(c)
    }

    private fun drawHome(c: Canvas) {
        body.textAlign = Paint.Align.RIGHT
        body.textSize = dp(18f)
        c.drawText("SETTINGS", width - dp(42f), dp(46f), body)

        val side = dp(52f)
        val gap = dp(22f)
        val top = dp(95f)
        val cw = (width - side * 2 - gap * 4) / 5f
        val ch = cw

        repeat(5) { i ->
            val left = side + i * (cw + gap)
            val r = RectF(left, top, left + cw, top + ch)
            p.style = Paint.Style.FILL
            p.color = Color.rgb(10,10,10)
            c.drawRoundRect(r, dp(28f), dp(28f), p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = if (i == selected) dp(5f) else dp(3f)
            p.color = accents[i]
            c.drawRoundRect(r, dp(28f), dp(28f), p)

            drawCategoryEmojis(c, i, r)

            title.textAlign = Paint.Align.CENTER
            title.textSize = dp(22f)
            c.drawText(names[i], r.centerX(), r.bottom + dp(34f), title)
        }
    }

    private fun drawCategoryEmojis(c: Canvas, index: Int, r: RectF) {
        // Keep every icon completely inside the 1:1 card, including emoji glyph bounds.
        val safe = RectF(r.left + dp(18f), r.top + dp(18f), r.right - dp(18f), r.bottom - dp(18f))
        val textSize = minOf(dp(58f), safe.width() * 0.30f)
        emojiPaint.textSize = textSize
        emojiPaint.textAlign = Paint.Align.CENTER
        val centerX = safe.centerX()
        val centerY = safe.centerY() + textSize * 0.28f
        val scale = safe.width() / dp(220f)

        c.save()
        c.clipRect(safe)
        for ((emoji, offset) in emojiSets[index]) {
            val x = centerX + dp(offset.first) * scale
            val y = centerY + dp(offset.second) * scale
            c.drawText(emoji, x, y, emojiPaint)
        }
        c.restore()
    }

    private fun drawSettings(c: Canvas) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(238,0,0,0)
        c.drawRect(0f,0f,width.toFloat(),height.toFloat(),p)
        val b = RectF(width*.25f,height*.18f,width*.75f,height*.82f)
        p.color = Color.rgb(20,20,20)
        c.drawRoundRect(b,dp(28f),dp(28f),p)
        title.textAlign = Paint.Align.LEFT
        title.textSize = dp(30f)
        c.drawText("Settings",b.left+dp(30f),b.top+dp(55f),title)
        title.textSize = dp(21f)
        c.drawText("Default Player",b.left+dp(30f),b.top+dp(110f),title)
        body.textAlign = Paint.Align.LEFT
        body.textSize = dp(18f)
        c.drawText("Built-in / Web Player",b.left+dp(30f),b.top+dp(155f),body)
    }

    override fun onKeyDown(k:Int,e:KeyEvent):Boolean {
        if (settings) {
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_DPAD_CENTER) { settings=false; invalidate() }
            return true
        }
        when (k) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { selected=(selected+4)%5; invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { selected=(selected+1)%5; invalidate(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { Toast.makeText(context,names[selected]+" selected",Toast.LENGTH_SHORT).show(); return true }
            KeyEvent.KEYCODE_MENU -> { settings=true; invalidate(); return true }
            KeyEvent.KEYCODE_BACK -> return true
        }
        return super.onKeyDown(k,e)
    }

    private fun dp(v:Float) = v * resources.displayMetrics.density
}
