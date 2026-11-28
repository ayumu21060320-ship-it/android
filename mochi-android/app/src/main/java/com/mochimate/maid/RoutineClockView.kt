package com.mochimate.maid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 1日を24時間の円で見せるグラフ(上が0時、時計回り)。
 * 紺=寝ている、ピンク=家で起きている、緑=外にいる。起きる・出る・帰る・寝る時刻に印。
 */
class RoutineClockView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val seg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B9AEB5"); textAlign = Paint.Align.CENTER }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val center = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Color.parseColor("#3B3245"); isFakeBoldText = true }
    private val centerSub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Color.parseColor("#857A8C") }
    private val now = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E0566E"); strokeWidth = 2 * d }
    private val rect = RectF()

    private var r: DayRhythm = DayRhythm(null, null, null, null)
    private var title = ""

    fun set(rhythm: DayRhythm, title: String) {
        r = rhythm
        this.title = title
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, min(w, (300 * d).toInt()))
    }

    /** 0時を上にした角度(度)。Canvas は右が0度なので -90 する */
    private fun deg(min: Int) = min / 1440f * 360f - 90f

    private fun arc(canvas: Canvas, from: Int, to: Int, color: String) {
        seg.color = Color.parseColor(color)
        val sweep = ((to - from + 1440) % 1440) / 1440f * 360f
        canvas.drawArc(rect, deg(from), if (sweep == 0f) 360f else sweep, false, seg)
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val ring = size * 0.12f
        val radius = size / 2 - ring / 2 - 26 * d
        seg.strokeWidth = ring
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // 下地: 家で起きている(ピンク)
        arc(canvas, 0, 0, "#F8C9D4")
        // 外にいる(緑)
        if (r.leave != null && r.back != null) arc(canvas, r.leave!!, r.back!!, "#8FD3A8")
        // 寝ている(紺)
        if (r.sleep != null && r.wake != null) arc(canvas, r.sleep!!, r.wake!!, "#2F4A7A")

        // 時刻の目盛り(0,3,6,…)
        tick.textSize = 11 * d
        for (h in 0 until 24 step 3) {
            val a = Math.toRadians(deg(h * 60).toDouble())
            val tr = radius - ring / 2 - 12 * d
            canvas.drawText("$h", cx + (tr * cos(a)).toFloat(), cy + (tr * sin(a)).toFloat() + 4 * d, tick)
        }

        // 起きる・出る・帰る・寝るの印(円の外側)
        mark.textSize = 16 * d
        fun pin(m: Int?, emoji: String) {
            m ?: return
            val a = Math.toRadians(deg(m).toDouble())
            val pr = radius + ring / 2 + 14 * d
            canvas.drawText(emoji, cx + (pr * cos(a)).toFloat(), cy + (pr * sin(a)).toFloat() + 6 * d, mark)
        }
        pin(r.wake, "☀️"); pin(r.leave, "🚪"); pin(r.back, "🏠"); pin(r.sleep, "🌙")

        // 今の時刻
        val nowMin = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        val a = Math.toRadians(deg(nowMin).toDouble())
        val r1 = radius - ring / 2
        val r2 = radius + ring / 2
        canvas.drawLine(cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat(), cx + (r2 * cos(a)).toFloat(), cy + (r2 * sin(a)).toFloat(), now)

        // 真ん中
        center.textSize = 16 * d
        centerSub.textSize = 12 * d
        canvas.drawText(title, cx, cy - 4 * d, center)
        val sl = r.sleepMinutes
        canvas.drawText(if (sl != null) "睡眠 ${sl / 60}時間${if (sl % 60 > 0) "${sl % 60}分" else ""}" else "記録をあつめ中", cx, cy + 16 * d, centerSub)
    }
}
