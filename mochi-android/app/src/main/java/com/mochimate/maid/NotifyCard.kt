package com.mochimate.maid

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** ホーム画面に出す小さなカードの中身 */
data class CardSpec(
    val icon: Drawable?,
    val appLabel: String,
    val title: String,
    val body: String?,
    /** 分類のチップ(支払いカード用)。null なら出さない */
    val chips: List<String>? = null,
    val selectedChip: String? = null,
    val onChip: ((String) -> Unit)? = null,
    /** 「取り消し」ボタン(支払いカード用) */
    val onUndo: (() -> Unit)? = null,
    /** カードをタップした時(そのアプリを開く) */
    val onOpen: (() -> Unit)? = null,
)

/**
 * iPhone の小窓(ピクチャ・イン・ピクチャ)のような、ホーム画面に浮かぶ小さなカード。
 * 画面の上のほうに出て、触らなければ10秒で消える。上にスワイプでも消せる。
 */
class NotifyCard(private val context: Context, private val wm: WindowManager) {

    private val handler = Handler(Looper.getMainLooper())
    private val d = context.resources.displayMetrics.density
    private var view: View? = null
    private val dismissRunnable = Runnable { dismiss() }

    private fun px(v: Int) = (v * d).toInt()

    fun show(spec: CardSpec) {
        dismiss()
        val card = build(spec)
        val params = WindowManager.LayoutParams(
            px(320).coerceAtMost(context.resources.displayMetrics.widthPixels - px(24)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = px(44)
            windowAnimations = android.R.style.Animation_Toast
        }
        wm.addView(card, params)
        view = card
        keepAlive()
    }

    fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    private fun keepAlive() {
        handler.removeCallbacks(dismissRunnable)
        handler.postDelayed(dismissRunnable, 10_000)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun build(spec: CardSpec): View {
        val ink = context.getColor(R.color.ink)
        val sub = context.getColor(R.color.ink_sub)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.notify_card_bg)
            elevation = px(8).toFloat()
            setPadding(px(14), px(12), px(14), px(12))
        }

        // ヘッダー: アイコン・アプリ名・✕
        val head = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(ImageView(context).apply { setImageDrawable(spec.icon) }, LinearLayout.LayoutParams(px(22), px(22)))
        head.addView(TextView(context).apply {
            text = spec.appLabel
            textSize = 13f
            setTextColor(sub)
            setPadding(px(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(TextView(context).apply {
            text = "✕"
            textSize = 16f
            setTextColor(sub)
            setPadding(px(10), px(2), px(2), px(2))
            contentDescription = "閉じる"
            setOnClickListener { dismiss() }
        })
        root.addView(head)

        root.addView(TextView(context).apply {
            text = spec.title
            textSize = 17f
            setTextColor(ink)
            paint.isFakeBoldText = true
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, px(6), 0, 0)
        })
        if (!spec.body.isNullOrBlank()) {
            root.addView(TextView(context).apply {
                text = spec.body
                textSize = 14f
                setTextColor(sub)
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, px(2), 0, 0)
            })
        }

        // 分類のチップ
        if (spec.chips != null) {
            val flow = FlowLayout(context).apply { setPadding(0, px(10), 0, 0) }
            var selected = spec.selectedChip
            fun render() {
                flow.removeAllViews()
                for (c in spec.chips) {
                    flow.addView(TextView(context).apply {
                        text = c
                        textSize = 13f
                        setTextColor(ink)
                        setBackgroundResource(if (c == selected) R.drawable.chip_custom_bg else R.drawable.chip_bg)
                        setPadding(px(12), px(6), px(12), px(6))
                        setOnClickListener {
                            selected = c
                            spec.onChip?.invoke(c)
                            render()
                            keepAlive()
                        }
                    })
                }
            }
            render()
            root.addView(flow)
        }

        // ボタン
        if (spec.onUndo != null || spec.onOpen != null) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, px(10), 0, 0)
            }
            spec.onUndo?.let { undo ->
                row.addView(smallButton("取り消し") { undo(); dismiss() })
            }
            spec.onOpen?.let { open ->
                row.addView(smallButton("ひらく") { open(); dismiss() }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(8) })
            }
            root.addView(row)
        }

        // カード全体をタップでアプリを開く / 上にスワイプで閉じる
        var downY = 0f
        root.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downY = e.rawY; keepAlive() }
                MotionEvent.ACTION_MOVE -> v.translationY = (e.rawY - downY).coerceAtMost(0f)
                MotionEvent.ACTION_UP -> {
                    if (e.rawY - downY < -px(40)) dismiss()
                    else {
                        v.translationY = 0f
                        if (kotlin.math.abs(e.rawY - downY) < px(8)) {
                            spec.onOpen?.let { it(); dismiss() }
                        }
                    }
                }
            }
            true
        }
        return root
    }

    private fun smallButton(label: String, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 14f
        setTextColor(context.getColor(R.color.accent))
        paint.isFakeBoldText = true
        setBackgroundResource(R.drawable.button_sub_bg)
        setPadding(px(16), px(7), px(16), px(7))
        setOnClickListener { onClick() }
    }
}
