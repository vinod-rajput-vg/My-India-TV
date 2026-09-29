package com.myindiatv

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.util.Base64
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
    private val icons = intArrayOf(
        R.drawable.entertainment,
        R.drawable.imfotainment,
        R.drawable.news,
        0,
        0
    )
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true; isAntiAlias = true }
    private val bitmaps = arrayOf<Bitmap?>(
        BitmapFactory.decodeResource(resources, icons[0]),
        BitmapFactory.decodeResource(resources, icons[1]),
        BitmapFactory.decodeResource(resources, icons[2]),
        decodeBase64(MUSIC_ICON_BASE64),
        decodeBase64(KIDS_ICON_BASE64)
    )
    private var selected = 0
    private var settings = false

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.BLACK)
        drawHome(c)
        if (settings) drawSettings(c)
    }

    private fun drawHome(c: Canvas) {
        body.color = Color.LTGRAY
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
            drawCategoryIcon(c, i, r)
            title.textAlign = Paint.Align.CENTER
            title.textSize = dp(22f)
            c.drawText(names[i], r.centerX(), r.bottom + dp(34f), title)
        }
    }

    private fun drawCategoryIcon(c: Canvas, index: Int, r: RectF) {
        val bitmap = bitmaps[index]
        val safe = RectF(r.left + dp(14f), r.top + dp(14f), r.right - dp(14f), r.bottom - dp(14f))
        val maxSize = minOf(safe.width(), safe.height()) * 0.82f

        if (bitmap != null && !bitmap.isRecycled) {
            val visible = findVisibleBounds(bitmap) ?: return
            val visibleWidth = visible.width().toFloat()
            val visibleHeight = visible.height().toFloat()
            val scale = minOf(maxSize / visibleWidth, maxSize / visibleHeight)
            val dstW = visibleWidth * scale
            val dstH = visibleHeight * scale
            val dst = RectF(
                safe.centerX() - dstW / 2f,
                safe.centerY() - dstH / 2f,
                safe.centerX() + dstW / 2f,
                safe.centerY() + dstH / 2f
            )
            c.save()
            c.clipPath(Path().apply { addRoundRect(safe, dp(20f), dp(20f), Path.Direction.CW) })
            c.drawBitmap(bitmap, visible, dst, p)
            c.restore()
        }
    }

    private fun findVisibleBounds(bitmap: Bitmap): Rect? {
        val width = bitmap.width
        val height = bitmap.height
        var left = width
        var top = height
        var right = -1
        var bottom = -1

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 12) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right >= left && bottom >= top) Rect(left, top, right + 1, bottom + 1) else null
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
        body.color = Color.LTGRAY
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

    private fun decodeBase64(value: String): Bitmap? {
        return try {
            val bytes = Base64.decode(value, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun dp(v:Float) = v * resources.displayMetrics.density

    companion object {
        private const val MUSIC_ICON_BASE64 = "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"
        private const val KIDS_ICON_BASE64 = "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"
    }
}
