package com.mochimate.maid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.TypedValue
import android.widget.TextView

/** もちもちの吹き出し。しっぽ(▼)の向きは上下どちらにもできる。 */
class BubbleView(context: Context) : TextView(context) {

    private val density = resources.displayMetrics.density
    private val pad = (12 * density).toInt()

    private val shape = BubbleShape(density)
    private val tail = shape.tail

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
        shape.draw(canvas, width.toFloat(), height.toFloat(), tailOnTop, tailX)
        super.onDraw(canvas)
    }
}
