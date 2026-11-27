package com.mochimate.maid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** 今日のカロリーの「進み具合リング」。真ん中に%と数字を出す */
class RingView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 14 * d; color = Color.parseColor("#F6E4E9"); strokeCap = Paint.Cap.ROUND
    }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 14 * d; strokeCap = Paint.Cap.ROUND
    }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 34 * d; isFakeBoldText = true
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; textSize = 13 * d; color = Color.parseColor("#857A8C")
    }
    private val rect = RectF()

    private var value = 0
    private var goal = 1

    fun set(value: Int, goal: Int) {
        this.value = value
        this.goal = goal.coerceAtLeast(1)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = (170 * d).toInt()
        setMeasuredDimension(resolveSize(s, widthMeasureSpec), resolveSize(s, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        val pad = track.strokeWidth / 2 + 2 * d
        val cx = width / 2f
        val cy = height / 2f
        rect.set(cx - size / 2 + pad, cy - size / 2 + pad, cx + size / 2 - pad, cy + size / 2 - pad)
        canvas.drawArc(rect, 0f, 360f, false, track)

        val ratio = value.toFloat() / goal
        val over = ratio > 1f
        bar.color = Color.parseColor(if (over) "#E0566E" else "#F08A5D")
        canvas.drawArc(rect, -90f, 360f * ratio.coerceAtMost(1f), false, bar)
        if (over) {
            // 目標をこえた分は、色を濃くしてもう1周
            bar.color = Color.parseColor("#B8324B")
            canvas.drawArc(rect, -90f, 360f * (ratio - 1f).coerceAtMost(1f), false, bar)
        }

        big.color = bar.color
        canvas.drawText("${(ratio * 100).toInt()}%", cx, cy + 6 * d, big)
        canvas.drawText("$value / $goal kcal", cx, cy + 28 * d, small)
        canvas.drawText(if (over) "${value - goal} kcal オーバー" else "あと ${goal - value} kcal", cx, cy - 26 * d, small)
    }
}
