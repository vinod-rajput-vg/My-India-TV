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

    private val bitmaps = arrayOfNulls<Bitmap>(5)

    init {
        for (i in iconIds.indices) {
            bitmaps[i] = BitmapFactory.decodeResource(resources, iconIds[i])
        }
    }

    private var selected = 0
    private var settings = false

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.rgb(32, 32, 32))
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

            // Gray category background.
            iconPaint.style = Paint.Style.FILL
            iconPaint.color = Color.rgb(96, 96, 96)
            c.drawRoundRect(r, dp(28f), dp(28f), iconPaint)

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
            r.left + dp(5f),
            r.top + dp(5f),
            r.right - dp(5f),
            r.bottom - dp(5f)
        )

        // Icons are 5% larger than the previous 0.94 scale.
        val scale = minOf(
            safe.width() / bitmap.width.toFloat(),
            safe.height() / bitmap.height.toFloat()
        ) * 0.987f

        val dstW = bitmap.width * scale
        val dstH = bitmap.height * scale

        val dst = RectF(
            safe.centerX() - dstW / 2f,
            safe.centerY() - dstH / 2f,
            safe.centerX() + dstW / 2f,
            safe.centerY() + dstH / 2f
        )

        // Draw the PNG directly; do not add any generated icon or border.
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
