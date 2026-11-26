package com.mochimate.maid

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * メイドさんのヘッドドレスをつけた「もちもち」を描くView。
 * ふわふわ呼吸・まばたき・タップでぴょんと跳ねる、をすべてコードで描いている。
 */
class MochiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private val bodyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFDF8") }
    private val bodyLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6D3C3"); style = Paint.Style.STROKE; strokeWidth = 2f * density
    }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#33000000") }
    private val navy = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E3A66") }
    private val frill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val frillLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D9CCC0"); style = Paint.Style.STROKE; strokeWidth = 1.2f * density
    }
    private val eye = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3B3245") }
    private val eyeShine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B3245"); style = Paint.Style.STROKE
        strokeWidth = 2.2f * density; strokeCap = Paint.Cap.ROUND
    }
    private val cheek = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#88F49AAE") }

    private val bodyPath = Path()
    private val tmpRect = RectF()
    private val tmpPath = Path()

    private var floatPhase = 0f  // 0..1 プカプカの周期
    private var jump = 0f        // 0..1 ジャンプ
    private var eyeOpen = 1f     // 1=開いてる 0=閉じてる
    private var happy = false    // ^ ^ の目
    private var dizzy = false    // > < の目(壁に強くぶつかった時)

    // 壁にぶつかった時の「むにっ」
    private var wobble = 0f
    private var wobbleHorizontal = true

    private val floatAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2600
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { floatPhase = it.animatedValue as Float; invalidate() }
    }

    private var wobbleAnim: ValueAnimator? = null

    private val jumpAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 480
        interpolator = LinearInterpolator()
        addUpdateListener { jump = it.animatedValue as Float; invalidate() }
    }

    private val blinkRunnable = object : Runnable {
        override fun run() {
            ValueAnimator.ofFloat(1f, 0f, 1f).apply {
                duration = 180
                addUpdateListener { eyeOpen = it.animatedValue as Float; invalidate() }
                start()
            }
            postDelayed(this, 2500L + Random.nextLong(3000))
        }
    }

    private val endHappy = Runnable { happy = false; invalidate() }
    private val endDizzy = Runnable { dizzy = false; invalidate() }

    /**
     * 壁にぶつかった時のリアクション。
     * @param horizontal 左右の壁なら true、上下の壁なら false
     * @param strength 0..1 ぶつかった強さ
     */
    fun bump(horizontal: Boolean, strength: Float) {
        val st = strength.coerceIn(0f, 1f)
        wobbleHorizontal = horizontal
        wobbleAnim?.cancel()
        wobbleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 550
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                // だんだん小さくなる、ぷるぷる
                wobble = st * exp(-4.5f * t) * cos(3f * 2f * PI.toFloat() * t)
                invalidate()
            }
            start()
        }
        if (st > 0.45f) {
            dizzy = true
            removeCallbacks(endDizzy)
            postDelayed(endDizzy, 700)
        }
    }

    /** タップされた時のリアクション */
    fun react() {
        happy = true
        removeCallbacks(endHappy)
        postDelayed(endHappy, 1600)
        jumpAnim.cancel()
        jumpAnim.start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        floatAnim.start()
        postDelayed(blinkRunnable, 2000)
    }

    override fun onDetachedFromWindow() {
        floatAnim.cancel()
        jumpAnim.cancel()
        wobbleAnim?.cancel()
        removeCallbacks(endDizzy)
        removeCallbacks(blinkRunnable)
        removeCallbacks(endHappy)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (96 * density).toInt()
        setMeasuredDimension(
            resolveSize(size, widthMeasureSpec),
            resolveSize(size, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f

        val hop = sin(PI * jump).toFloat()          // 0→1→0
        // プカプカ: 0(いちばん下)〜1(いちばん上)をゆっくり行き来する
        val bob = (sin(2.0 * PI * floatPhase).toFloat() + 1f) / 2f
        val lift = hop * h * 0.12f + bob * h * 0.06f
        // 浮いている時は少し縦長、下りてくると少しむにっ
        val breathe = 0.02f * cos(2.0 * PI * floatPhase).toFloat()
        var sx = 1f + breathe
        var sy = 1f - breathe
        // 壁にぶつかった方向につぶれる
        if (wobbleHorizontal) { sx -= wobble * 0.28f; sy += wobble * 0.22f }
        else { sx += wobble * 0.22f; sy -= wobble * 0.28f }

        // かげ(高く浮くほど小さく薄く)
        val height01 = (lift / (h * 0.18f)).coerceIn(0f, 1f)
        val shW = w * 0.32f * (1f - height01 * 0.4f)
        shadow.alpha = (0x33 * (1f - height01 * 0.5f)).toInt()
        tmpRect.set(cx - shW, h * 0.91f, cx + shW, h * 0.97f)
        canvas.drawOval(tmpRect, shadow)

        canvas.save()
        canvas.translate(0f, -lift - h * 0.03f)
        canvas.scale(sx, sy, cx, h * 0.62f)

        // からだ
        val left = w * 0.1f
        val right = w * 0.9f
        val top = h * 0.3f
        val bottom = h * 0.93f
        val bw = right - left
        val bh = bottom - top
        tmpRect.set(left, top, right, bottom)
        bodyPath.reset()
        val topR = bw * 0.5f
        val botR = bw * 0.24f
        bodyPath.addRoundRect(
            tmpRect,
            floatArrayOf(topR, bh * 0.75f, topR, bh * 0.75f, botR, botR, botR, botR),
            Path.Direction.CW,
        )
        canvas.drawPath(bodyPath, bodyFill)
        canvas.drawPath(bodyPath, bodyLine)

        // ヘッドドレス(白いフリル+紺のバンド)
        val bandY = top + bh * 0.1f
        val bandL = left + bw * 0.2f
        val bandR = right - bw * 0.2f
        val r = (bandR - bandL) / 8f
        for (i in 0 until 4) {
            val fx = bandL + r + i * 2 * r
            canvas.drawCircle(fx, bandY, r * 1.15f, frill)
            canvas.drawCircle(fx, bandY, r * 1.15f, frillLine)
        }
        tmpRect.set(bandL - r * 0.3f, bandY, bandR + r * 0.3f, bandY + r * 0.9f)
        canvas.drawRoundRect(tmpRect, r * 0.45f, r * 0.45f, navy)

        // 目
        val eyeY = top + bh * 0.52f
        val eyeDX = bw * 0.17f
        if (dizzy) {
            // > <
            for (s in arrayOf(-1f, 1f)) {
                val ex = cx + s * eyeDX
                val d = bw * 0.05f
                tmpPath.reset()
                tmpPath.moveTo(ex - s * d, eyeY - d)
                tmpPath.lineTo(ex + s * d, eyeY)
                tmpPath.lineTo(ex - s * d, eyeY + d)
                canvas.drawPath(tmpPath, line)
            }
        } else if (happy) {
            for (s in arrayOf(-1f, 1f)) {
                val ex = cx + s * eyeDX
                tmpPath.reset()
                tmpPath.moveTo(ex - bw * 0.055f, eyeY + bh * 0.02f)
                tmpPath.quadTo(ex, eyeY - bh * 0.09f, ex + bw * 0.055f, eyeY + bh * 0.02f)
                canvas.drawPath(tmpPath, line)
            }
        } else {
            val rx = bw * 0.048f
            val ry = (bh * 0.085f * eyeOpen).coerceAtLeast(bh * 0.008f)
            for (s in arrayOf(-1f, 1f)) {
                val ex = cx + s * eyeDX
                tmpRect.set(ex - rx, eyeY - ry, ex + rx, eyeY + ry)
                canvas.drawOval(tmpRect, eye)
                if (eyeOpen > 0.6f) {
                    canvas.drawCircle(ex + rx * 0.3f, eyeY - ry * 0.4f, rx * 0.35f, eyeShine)
                }
            }
        }

        // ほっぺ
        val cheekY = eyeY + bh * 0.15f
        for (s in arrayOf(-1f, 1f)) {
            val chx = cx + s * bw * 0.3f
            tmpRect.set(chx - bw * 0.075f, cheekY - bh * 0.04f, chx + bw * 0.075f, cheekY + bh * 0.04f)
            canvas.drawOval(tmpRect, cheek)
        }

        // 口(ω)
        val mouthY = eyeY + bh * 0.12f
        val m = bw * 0.045f
        tmpPath.reset()
        tmpPath.moveTo(cx - 2 * m, mouthY)
        tmpPath.quadTo(cx - m, mouthY + m * 1.4f, cx, mouthY)
        tmpPath.quadTo(cx + m, mouthY + m * 1.4f, cx + 2 * m, mouthY)
        canvas.drawPath(tmpPath, line)

        canvas.restore()
    }
}
