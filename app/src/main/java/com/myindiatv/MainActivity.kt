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
    private val accents = listOf(
        Color.rgb(255, 35, 70),
        Color.rgb(0, 180, 255),
        Color.rgb(255, 190, 30),
        Color.rgb(235, 20, 230),
        Color.rgb(0, 240, 100)
    )

    private val icons = intArrayOf(
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
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isAntiAlias = true
    }

    private val bitmaps = arrayOf<Bitmap?>(
        BitmapFactory.decodeResource(resources, icons[0]),
        BitmapFactory.decodeResource(resources, icons[1]),
        BitmapFactory.decodeResource(resources, icons[2]),
        BitmapFactory.decodeResource(resources, icons[3]),
        BitmapFactory.decodeResource(resources, icons[4])
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
            p.color = Color.rgb(10, 10, 10)
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
        val bitmap = bitmaps[index] ?: return
        if (bitmap.isRecycled) return

        val safe = RectF(
            r.left + dp(14f),
            r.top + dp(14f),
            r.right - dp(14f),
            r.bottom - dp(14f)
        )
        val maxSize = minOf(safe.width(), safe.height()) * 0.82f
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
        c.clipPath(Path().apply {
            addRoundRect(safe, dp(20f), dp(20f), Path.Direction.CW)
        })
        c.drawBitmap(bitmap, visible, dst, p)
        c.restore()
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

        return if (right >= left && bottom >= top) {
            Rect(left, top, right + 1, bottom + 1)
        } else {
            null
        }
    }

    private fun drawSettings(c: Canvas) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(238, 0, 0, 0)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)

        val b = RectF(width * .25f, height * .18f, width * .75f, height * .82f)
        p.color = Color.rgb(20, 20, 20)
        c.drawRoundRect(b, dp(28f), dp(28f), p)

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

    override fun onKeyDown(k: Int, e: KeyEvent): Boolean {
        if (settings) {
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_DPAD_CENTER) {
                settings = false
                invalidate()
            }
            return true
        }

        when (k) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                selected = (selected + 4) % 5
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                selected = (selected + 1) % 5
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                Toast.makeText(context, names[selected] + " selected", Toast.LENGTH_SHORT).show()
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                settings = true
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_BACK -> return true
        }

        return super.onKeyDown(k, e)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}
