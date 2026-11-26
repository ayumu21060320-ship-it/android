package com.mochimate.maid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.widget.TextView

/** もちもちの吹き出し。しっぽ(▼)の向きは上下どちらにもできる。 */
class BubbleView(context: Context) : TextView(context) {

    private val density = resources.displayMetrics.density
    private val tail = 10 * density
    private val radius = 16 * density
    private val pad = (12 * density).toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8738C"); style = Paint.Style.STROKE; strokeWidth = 2 * density
    }
    private val path = Path()
    private val rect = RectF()

    /** しっぽの先の横位置(吹き出し内のpx)。負の値なら真ん中。 */
    var tailX = -1f
        set(value) { field = value; invalidate() }

    /** true ならしっぽが上(もちもちの下に吹き出しがある時) */
    var tailOnTop = false
        set(value) {
            field = value
            applyPadding()
            invalidate()
        }

    init {
        setTextColor(Color.parseColor("#3B3245"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setLineSpacing(0f, 1.15f)
        maxWidth = (240 * density).toInt()
        applyPadding()
    }

    private fun applyPadding() {
        val t = tail.toInt()
        if (tailOnTop) setPadding(pad + 4, pad + t, pad + 4, pad)
        else setPadding(pad + 4, pad, pad + 4, pad + t)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
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
        super.onDraw(canvas)
    }
}
