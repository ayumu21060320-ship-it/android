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
import kotlin.math.abs

/** メイドさんが知らせてくれる「お知らせ」の中身 */
data class CardSpec(
    val icon: Drawable?,
    val appLabel: String,
    val title: String,
    val body: String?,
    /** メイドさんのセリフ(吹き出しのいちばん上に出す) */
    val header: String? = null,
    /** 分類のチップ(支払い用)。null なら出さない */
    val chips: List<String>? = null,
    val selectedChip: String? = null,
    val onChip: ((String) -> Unit)? = null,
    /** 「取り消し」ボタン(支払い用) */
    val onUndo: (() -> Unit)? = null,
    /** タップした時(そのアプリを開く) */
    val onOpen: (() -> Unit)? = null,
    /** そのほかのボタン(アラームの「止める」「スヌーズ」など) */
    val actions: List<Pair<String, () -> Unit>> = emptyList(),
    /** 通知が消えた時に一緒に消すための目印 */
    val tag: String? = null,
    /** true なら時間で消えない(アラームなど) */
    val sticky: Boolean = false,
    /** 吹き出しの中に出す小さなグラフなど(カロリーのリング・体重のグラフ…) */
    val visual: ((Context) -> View)? = null,
    /** 自動で消えるまでの時間(ミリ秒) */
    val timeoutMs: Long = 12_000,
)

/** お知らせの中身(アイコン・タイトル・チップ・ボタン)を組み立てる。吹き出しとカードで共通。 */
object CardContent {

    /**
     * @param onClose 閉じる時(✕やボタンを押した後)
     * @param onTouched 触られた時(自動で消えるまでの時間をのばす)
     */
    @SuppressLint("ClickableViewAccessibility")
    fun build(context: Context, spec: CardSpec, onClose: () -> Unit, onTouched: () -> Unit): View {
        val d = context.resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        val ink = context.getColor(R.color.ink)
        val sub = context.getColor(R.color.ink_sub)
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // 上の段: 大きなアプリのアイコン | アプリ名・セリフ・タイトル・本文 | ✕
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        if (spec.icon != null) {
            top.addView(ImageView(context).apply {
                setImageDrawable(spec.icon)
                contentDescription = spec.appLabel
            }, LinearLayout.LayoutParams(px(44), px(44)).apply { marginEnd = px(10) })
        }
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        if (spec.appLabel.isNotBlank()) {
            texts.addView(TextView(context).apply {
                text = spec.appLabel
                textSize = 12f
                setTextColor(sub)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        if (spec.header != null) {
            texts.addView(TextView(context).apply {
                text = spec.header
                textSize = 15f
                setTextColor(ink)
                setLineSpacing(0f, 1.15f)
                setPadding(0, px(2), 0, 0)
            })
        }
        if (spec.title.isNotBlank()) {
            texts.addView(TextView(context).apply {
                text = spec.title
                textSize = 18f
                setTextColor(ink)
                paint.isFakeBoldText = true
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, px(4), 0, 0)
            })
        }
        if (!spec.body.isNullOrBlank()) {
            texts.addView(TextView(context).apply {
                text = spec.body
                textSize = 14f
                setTextColor(sub)
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, px(2), 0, 0)
            })
        }
        top.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(TextView(context).apply {
            text = "✕"
            textSize = 15f
            setTextColor(sub)
            setPadding(px(10), 0, px(2), px(4))
            contentDescription = "閉じる"
            setOnClickListener { onClose() }
        })
        root.addView(top)

        spec.visual?.let { make ->
            root.addView(make(context), LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8) })
        }

        if (spec.chips != null) {
            val flow = FlowLayout(context).apply { setPadding(0, px(8), 0, 0) }
            var selected = spec.selectedChip
            fun render() {
                flow.removeAllViews()
                for (c in spec.chips) {
                    flow.addView(TextView(context).apply {
                        text = c
                        textSize = 13f
                        setTextColor(ink)
                        setBackgroundResource(if (c == selected) R.drawable.chip_custom_bg else R.drawable.chip_bg)
                        setPadding(px(11), px(5), px(11), px(5))
                        setOnClickListener {
                            selected = c
                            spec.onChip?.invoke(c)
                            render()
                            onTouched()
                        }
                    })
                }
            }
            render()
            root.addView(flow)
        }

        val buttons = buildList {
            spec.actions.forEach { (label, run) -> add(label to { run(); onClose() }) }
            spec.onUndo?.let { undo -> add("取り消し" to { undo(); onClose() }) }
            spec.onOpen?.let { open -> add("ひらく" to { open(); onClose() }) }
        }
        if (buttons.isNotEmpty()) {
            val row = FlowLayout(context).apply { setPadding(0, px(10), 0, 0) }
            for ((label, run) in buttons) {
                row.addView(TextView(context).apply {
                    text = label
                    textSize = 14f
                    setTextColor(context.getColor(R.color.accent))
                    paint.isFakeBoldText = true
                    setBackgroundResource(R.drawable.button_sub_bg)
                    setPadding(px(16), px(7), px(16), px(7))
                    setOnClickListener { run() }
                })
            }
            root.addView(row)
        }

        // 余白をタップしてもアプリを開く
        var downY = 0f
        root.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downY = e.rawY; onTouched() }
                MotionEvent.ACTION_UP -> if (abs(e.rawY - downY) < px(8)) spec.onOpen?.let { it(); onClose() }
            }
            true
        }
        return root
    }
}

/** 画面の上に出すカード(「吹き出しではなく上に出す」設定の時) */
class NotifyCard(private val context: Context, private val wm: WindowManager) {

    private val handler = Handler(Looper.getMainLooper())
    private val d = context.resources.displayMetrics.density
    private var view: View? = null
    var tag: String? = null
        private set
    private val dismissRunnable = Runnable { dismiss() }

    private fun px(v: Int) = (v * d).toInt()

    fun show(spec: CardSpec) {
        dismiss()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.notify_card_bg)
            elevation = px(8).toFloat()
            setPadding(px(14), px(12), px(14), px(12))
            addView(CardContent.build(context, spec, onClose = { dismiss() }, onTouched = { keepAlive(spec) }))
        }
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
        tag = spec.tag
        keepAlive(spec)
    }

    fun dismiss() {
        handler.removeCallbacks(dismissRunnable)
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        tag = null
    }

    private fun keepAlive(spec: CardSpec) {
        handler.removeCallbacks(dismissRunnable)
        if (!spec.sticky) handler.postDelayed(dismissRunnable, 10_000)
    }
}
