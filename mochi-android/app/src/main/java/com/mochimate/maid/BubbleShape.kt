package com.mochimate.maid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.widget.LinearLayout

/** 吹き出しの形(角丸の四角+しっぽ)を描く。ふつうの吹き出しとカード入りの吹き出しで共通。 */
class BubbleShape(density: Float) {
    val tail = 10 * density
    private val radius = 16 * density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8738C"); style = Paint.Style.STROKE; strokeWidth = 2 * density
    }
    private val path = Path()
    private val rect = RectF()

    fun draw(canvas: Canvas, w: Float, h: Float, tailOnTop: Boolean, tailX: Float) {
        val inset = stroke.strokeWidth / 2
        if (tailOnTop) rect.set(inset, tail, w - inset, h - inset)
        else rect.set(inset, inset, w - inset, h - tail)
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        val minX = radius + tail
        val cx = if (tailX < 0 || w < minX * 2) w / 2 else tailX.coerceIn(minX, w - minX)
        val tailPath = Path().apply {
            if (tailOnTop) {
                moveTo(cx - tail, tail + 1); lineTo(cx, inset); lineTo(cx + tail, tail + 1)
            } else {
                moveTo(cx - tail, h - tail - 1); lineTo(cx, h - inset); lineTo(cx + tail, h - tail - 1)
            }
            close()
        }
        path.op(tailPath, Path.Op.UNION)
        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)
    }
}

/** ボタンやチップを入れられる吹き出し(支払い・アラームなど) */
class BubbleFrame(context: Context) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private val shape = BubbleShape(density)
    private val pad = (14 * density).toInt()

    var tailX = -1f
        set(value) { field = value; invalidate() }

    var tailOnTop = false
        set(value) { field = value; applyPadding(); invalidate() }

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
        applyPadding()
    }

    private fun applyPadding() {
        val t = shape.tail.toInt()
        if (tailOnTop) setPadding(pad, pad + t, pad, pad) else setPadding(pad, pad, pad, pad + t)
    }

    override fun onDraw(canvas: Canvas) {
        shape.draw(canvas, width.toFloat(), height.toFloat(), tailOnTop, tailX)
        super.onDraw(canvas)
    }
}
