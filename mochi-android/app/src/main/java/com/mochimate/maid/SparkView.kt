package com.mochimate.maid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** カード用の小さな折れ線(目盛りなし) */
class SparkView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.4f * d; color = Color.parseColor("#E0566E"); strokeJoin = Paint.Join.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E0566E") }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1FE0566E") }
    private val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B9AEB5"); textSize = 11 * d; textAlign = Paint.Align.CENTER
    }

    private var values: List<Double> = emptyList()

    fun set(values: List<Double>) {
        this.values = values
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (values.size < 2) {
            canvas.drawText("記録が2日分たまるとグラフになります", width / 2f, height / 2f, empty)
            return
        }
        val lo = values.min() - 0.3
        val hi = values.max() + 0.3
        val pad = 4 * d
        fun x(i: Int) = pad + i * (width - 2 * pad) / (values.size - 1)
        fun y(v: Double) = (height - pad - (v - lo) / (hi - lo) * (height - 2 * pad)).toFloat()
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        val area = Path(path).apply { lineTo(x(values.size - 1), height.toFloat()); lineTo(x(0), height.toFloat()); close() }
        canvas.drawPath(area, fill)
        canvas.drawPath(path, line)
        values.forEachIndexed { i, v -> canvas.drawCircle(x(i), y(v), 2.6f * d, dot) }
    }
}
