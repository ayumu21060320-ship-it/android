package com.mochimate.maid

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * ちびメイドさん(Mochi)を描くView。
 * 絵は tools/maid_art.py が作った VectorDrawable(からだ+表情パーツ)で、
 * プカプカ・まばたき・タップでぴょん・壁でむにっ、をコードで動かしている。
 *
 * View の中は 220x280 の「論理座標」で考える。キャラの絵(200x240)は
 * (10, 36) にずらして置き、上と左右に跳ねたり傾いたりする余白を取っている。
 */
class MochiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val tmpRect = RectF()
    private val artRect = RectF(0f, 0f, ART_W, ART_H)

    // 表示する大きさに合わせて、絵を一度だけ画像にしておく(毎フレーム描き直さないため)
    private var unit = 1f
    private var body: Bitmap? = null
    private val faces = HashMap<Int, Bitmap>()

    private var floatPhase = 0f  // 0..1 プカプカの周期
    private var jump = 0f        // 0..1 ジャンプ
    private var eyeOpen = 1f     // 1=開いてる 0=閉じてる
    private var happy = false    // にっこり
    private var dizzy = false    // > <(壁に強くぶつかった時)

    // 壁にぶつかった時の「むにっ」
    private var wobble = 0f
    private var wobbleHorizontal = true

    /** 進む向きへの傾き(度)。止まっている時のゆらゆらは自分で足す。 */
    var tilt = 0f
        set(value) { field = value; invalidate() }

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
                duration = 200
                addUpdateListener { eyeOpen = it.animatedValue as Float; invalidate() }
                start()
            }
            postDelayed(this, 2500L + Random.nextLong(3000))
        }
    }

    private val endHappy = Runnable { happy = false; invalidate() }
    private val endDizzy = Runnable { dizzy = false; invalidate() }

    /** タップされた時のリアクション */
    fun react() {
        happy = true
        removeCallbacks(endHappy)
        postDelayed(endHappy, 1600)
        jumpAnim.cancel()
        jumpAnim.start()
    }

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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        floatAnim.start()
        postDelayed(blinkRunnable, 2000)
    }

    override fun onDetachedFromWindow() {
        floatAnim.cancel()
        jumpAnim.cancel()
        wobbleAnim?.cancel()
        removeCallbacks(blinkRunnable)
        removeCallbacks(endHappy)
        removeCallbacks(endDizzy)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (DEFAULT_WIDTH_DP * density).toInt()
        val h = (w * VIEW_H / VIEW_W).toInt()
        setMeasuredDimension(resolveSize(w, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        unit = min(w / VIEW_W, h / VIEW_H)
        // 「むにっ」で少し大きくなっても粗くならないよう、1.25倍の解像度で作っておく
        val px = unit * 1.25f
        val bw = ceil(ART_W * px).toInt().coerceAtLeast(1)
        val bh = ceil(ART_H * px).toInt().coerceAtLeast(1)
        body?.recycle()
        faces.values.forEach { it.recycle() }
        faces.clear()
        body = raster(R.drawable.maid_body, bw, bh)
        for (id in FACE_PARTS) faces[id] = raster(id, bw, bh)
    }

    private fun raster(id: Int, bw: Int, bh: Int): Bitmap {
        val d = context.getDrawable(id)!!
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, bw, bh)
        d.draw(Canvas(bmp))
        return bmp
    }

    override fun onDraw(canvas: Canvas) {
        val b = body ?: return
        canvas.save()
        // View の真ん中に、論理座標 220x280 を置く
        canvas.translate((width - VIEW_W * unit) / 2f, (height - VIEW_H * unit) / 2f)
        canvas.scale(unit, unit)

        val hop = sin(PI * jump).toFloat()                          // 0→1→0
        val bob = (sin(2.0 * PI * floatPhase).toFloat() + 1f) / 2f  // プカプカ 0..1
        val lift = 6f + hop * 30f + bob * 14f

        // かげ(高く浮くほど小さく薄く)
        val h01 = ((lift - 6f) / 44f).coerceIn(0f, 1f)
        val shW = 44f * (1f - h01 * 0.4f)
        shadow.alpha = (0x2A * (1f - h01 * 0.5f)).toInt()
        tmpRect.set(110f - shW, 266f, 110f + shW, 276f)
        canvas.drawOval(tmpRect, shadow)

        // 浮いている時は少し縦長、下りてくると少しむにっ。壁にぶつかると、その向きにつぶれる。
        val breathe = 0.02f * cos(2.0 * PI * floatPhase).toFloat()
        var sx = 1f + breathe
        var sy = 1f - breathe
        if (wobbleHorizontal) { sx -= wobble * 0.22f; sy += wobble * 0.18f }
        else { sx += wobble * 0.18f; sy -= wobble * 0.22f }
        val sway = 3f * sin(2.0 * PI * floatPhase * 0.5 + 0.8).toFloat()

        canvas.translate(10f, 36f - lift)
        canvas.scale(sx, sy, 100f, 160f)
        canvas.rotate(tilt + sway, 100f, 160f)

        canvas.drawBitmap(b, null, artRect, bitmapPaint)
        val eyes = when {
            dizzy -> R.drawable.maid_eyes_dizzy
            happy -> R.drawable.maid_eyes_happy
            eyeOpen < 0.45f -> R.drawable.maid_eyes_closed
            else -> R.drawable.maid_eyes_open
        }
        val mouth = when {
            dizzy -> R.drawable.maid_mouth_dizzy
            happy -> R.drawable.maid_mouth_happy
            else -> R.drawable.maid_mouth_normal
        }
        faces[eyes]?.let { canvas.drawBitmap(it, null, artRect, bitmapPaint) }
        faces[mouth]?.let { canvas.drawBitmap(it, null, artRect, bitmapPaint) }

        canvas.restore()
    }

    companion object {
        private const val ART_W = 200f
        private const val ART_H = 240f
        const val VIEW_W = 220f
        const val VIEW_H = 280f
        private const val DEFAULT_WIDTH_DP = 112

        /** キャラの絵が View の中で占める範囲(割合)。壁との当たり判定に使う。 */
        const val CONTENT_LEFT = (10f + 30f) / VIEW_W
        const val CONTENT_RIGHT = (10f + 170f) / VIEW_W
        const val CONTENT_TOP = (36f + 31f - 6f) / VIEW_H
        const val CONTENT_BOTTOM = 276f / VIEW_H

        private val FACE_PARTS = intArrayOf(
            R.drawable.maid_eyes_open, R.drawable.maid_eyes_closed,
            R.drawable.maid_eyes_happy, R.drawable.maid_eyes_dizzy,
            R.drawable.maid_mouth_normal, R.drawable.maid_mouth_happy, R.drawable.maid_mouth_dizzy,
        )
    }
}
