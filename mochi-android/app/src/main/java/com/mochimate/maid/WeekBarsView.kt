package com.mochimate.maid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import java.time.LocalDate

/** 1週間の棒グラフ(集中した分数など) */
class WeekBarsView(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2F4A7A") }
    private val today = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8738C") }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#857A8C"); textSize = 11 * d; textAlign = Paint.Align.CENTER }
    private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3B3245"); textSize = 10 * d; textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private var data: List<Pair<LocalDate, Int>> = emptyList()

    fun set(data: List<Pair<LocalDate, Int>>) { this.data = data; invalidate() }

    override fun onDraw(canvas: Canvas) {
        if (data.isEmpty()) return
        val maxV = maxOf(30, data.maxOf { it.second })
        val bottom = height - 18 * d
        val top = 14 * d
        val slot = width.toFloat() / data.size
        val names = listOf("月", "火", "水", "木", "金", "土", "日")
        data.forEachIndexed { i, (date, v) ->
            val cx = slot * i + slot / 2
            val h = (bottom - top) * v / maxV
            rect.set(cx - slot * 0.28f, bottom - h, cx + slot * 0.28f, bottom)
            canvas.drawRoundRect(rect, 4 * d, 4 * d, if (date == LocalDate.now()) today else bar)
            if (v > 0) canvas.drawText("$v", cx, bottom - h - 3 * d, value)
            canvas.drawText(names[date.dayOfWeek.value - 1], cx, height - 4 * d, label)
        }
    }
}
