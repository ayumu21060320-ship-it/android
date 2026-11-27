package com.mochimate.maid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * 体重のグラフ。折れ線(下を薄く塗る)・目標のライン・このペースで進んだ時の予測(点線)。
 */
class WeightChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f * d; color = Color.parseColor("#E0566E"); strokeJoin = Paint.Join.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E0566E") }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EFE3E7"); strokeWidth = 1f * d }
    private val goal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F2B84B"); strokeWidth = 2f * d }
    private val pred = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * d; color = Color.parseColor("#E0566E")
        pathEffect = DashPathEffect(floatArrayOf(6 * d, 5 * d), 0f)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#857A8C"); textSize = 11 * d }
    private val goalLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D9982C"); textSize = 11 * d; isFakeBoldText = true
    }
    private val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#857A8C"); textSize = 14 * d; textAlign = Paint.Align.CENTER
    }

    private var points: List<Pair<LocalDate, Double>> = emptyList()
    private var target: Double? = null
    private var predictDate: LocalDate? = null
    private var days = 30

    /** @param days 何日分を見せるか(0 なら全部) */
    fun set(points: List<Pair<LocalDate, Double>>, target: Double?, predictDate: LocalDate?, days: Int) {
        this.points = points
        this.target = target
        this.predictDate = predictDate
        this.days = days
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize((300 * d).toInt(), widthMeasureSpec),
            resolveSize((200 * d).toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val left = 34 * d
        val right = width - 10 * d
        val top = 12 * d
        val bottom = height - 22 * d

        val today = LocalDate.now()
        val start = when {
            days > 0 -> today.minusDays(days.toLong() - 1)
            points.isNotEmpty() -> points.first().first
            else -> today.minusDays(29)
        }
        val shown = points.filter { it.first >= start }
        if (shown.isEmpty()) {
            canvas.drawText("体重を記録すると、ここにグラフが出ます", width / 2f, height / 2f, empty)
            return
        }
        // 予測の日が近ければ、そこまで横に伸ばす(表示期間の1.5倍まで)
        val span = (today.toEpochDay() - start.toEpochDay()).coerceAtLeast(6)
        val p = predictDate
        val end = if (p != null && p > today && p.toEpochDay() - start.toEpochDay() <= span * 3 / 2) p else today
        val x0 = start.toEpochDay().toFloat()
        val x1 = max(end.toEpochDay().toFloat(), x0 + 1)

        // 縦の範囲: 記録と目標が入るように、少し余白をとる
        var lo = shown.minOf { it.second }
        var hi = shown.maxOf { it.second }
        target?.let { lo = min(lo, it); hi = max(hi, it) }
        lo = floor(lo - 0.5); hi = ceil(hi + 0.5)
        if (hi - lo < 2) { hi += 1; lo -= 1 }

        fun x(date: LocalDate) = left + (date.toEpochDay() - x0) / (x1 - x0) * (right - left)
        fun y(kg: Double) = bottom - ((kg - lo) / (hi - lo)).toFloat() * (bottom - top)

        // 目盛り
        val step = if (hi - lo > 8) 2.0 else 1.0
        var v = lo
        while (v <= hi) {
            val yy = y(v)
            canvas.drawLine(left, yy, right, yy, grid)
            canvas.drawText(if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v), 2 * d, yy + 4 * d, label)
            v += step
        }
        canvas.drawText("${start.monthValue}/${start.dayOfMonth}", left, height - 6 * d, label)
        val endText = if (end == today) "今日" else "${end.monthValue}/${end.dayOfMonth}"
        canvas.drawText(endText, right - label.measureText(endText), height - 6 * d, label)

        // 目標
        target?.let {
            val yy = y(it)
            canvas.drawLine(left, yy, right, yy, goal)
            canvas.drawText("目標 ${it}kg", left + 4 * d, yy - 4 * d, goalLabel)
        }

        // 折れ線と、その下の塗り
        val path = Path()
        shown.forEachIndexed { i, (date, kg) -> if (i == 0) path.moveTo(x(date), y(kg)) else path.lineTo(x(date), y(kg)) }
        val area = Path(path).apply {
            lineTo(x(shown.last().first), bottom)
            lineTo(x(shown.first().first), bottom)
            close()
        }
        fill.shader = LinearGradient(0f, top, 0f, bottom, Color.parseColor("#66F49AAE"), Color.parseColor("#05F49AAE"), Shader.TileMode.CLAMP)
        canvas.drawPath(area, fill)
        canvas.drawPath(path, line)
        if (shown.size <= 40) shown.forEach { (date, kg) -> canvas.drawCircle(x(date), y(kg), 3 * d, dot) }

        // 予測の点線: 最後の記録 → 目標に届く日
        val t = target
        if (p != null && t != null && p > shown.last().first && p <= end) {
            canvas.drawLine(x(shown.last().first), y(shown.last().second), x(p), y(t), pred)
            canvas.drawCircle(x(p), y(t), 5 * d, goal)
        }
    }
}
