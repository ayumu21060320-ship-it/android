package com.mochimate.maid

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** リリとのチャット画面 */
class ChatActivity : Activity() {

    private lateinit var store: ChatStore
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var send: TextView
    private lateinit var typing: TextView
    private lateinit var maid: MochiView
    private var waiting = false
    private val d by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ChatStore(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.bg))
            fitsSystemWindows = true
        }
        setContentView(root)

        // 上: リリと名前、ノート
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(12), px(6), px(12), px(6))
        }
        maid = MochiView(this)
        top.addView(maid, LinearLayout.LayoutParams(px(52), px(66)))
        top.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(6), 0, 0, 0)
            addView(TextView(this@ChatActivity).apply {
                text = "リリ"; textSize = 18f; setTextColor(getColor(R.color.ink)); paint.isFakeBoldText = true
            })
            typing = TextView(this@ChatActivity).apply { textSize = 12f; setTextColor(getColor(R.color.ink_sub)) }
            addView(typing)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(TextView(this).apply {
            text = "📒 ノート"
            textSize = 14f
            setTextColor(getColor(R.color.accent))
            paint.isFakeBoldText = true
            setBackgroundResource(R.drawable.button_sub_bg)
            setPadding(px(12), px(6), px(12), px(6))
            setOnClickListener { showNotebook() }
        })
        root.addView(top)

        // まんなか: 会話
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(4), px(12), px(12))
        }
        scroll = ScrollView(this).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // 下: 入力
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(getColor(R.color.card))
            setPadding(px(10), px(8), px(10), px(8))
        }
        input = EditText(this).apply {
            hint = "リリに話しかける…"
            textSize = 16f
            setTextColor(getColor(R.color.ink))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 4
            imeOptions = EditorInfo.IME_ACTION_SEND
        }
        bar.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        send = TextView(this).apply {
            text = "送る"
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            paint.isFakeBoldText = true
            setBackgroundResource(R.drawable.button_bg)
            gravity = Gravity.CENTER
            setPadding(px(16), px(10), px(16), px(10))
            setOnClickListener { sendMessage() }
        }
        bar.addView(send, LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(8) })
        root.addView(bar)

        render()
        if (store.messages().isEmpty()) {
            addBubble(ChatMessage(false, "あっ、話しかけてくれるんですか？えへへ、うれしいです！", System.currentTimeMillis()), save = true)
        }
        typing.text = if (Prefs(this).anthropicKey.isNullOrBlank()) "API キーが未設定です（設定タブ）" else "オンライン"
    }

    private fun render() {
        list.removeAllViews()
        store.messages().forEach { addBubble(it, save = false) }
        scrollDown()
    }

    private fun addBubble(m: ChatMessage, save: Boolean) {
        if (save) store.add(m)
        val tv = TextView(this).apply {
            text = m.text
            textSize = 15f
            setTextColor(getColor(R.color.ink))
            setLineSpacing(0f, 1.15f)
            setPadding(px(12), px(8), px(12), px(8))
            setBackgroundResource(if (m.fromUser) R.drawable.chip_custom_bg else R.drawable.bubble_card_bg)
            maxWidth = px(280)
            if (m.feedback < 0) alpha = 0.55f
            if (!m.fromUser) setOnLongClickListener {
                // 長押しで「いまいち」(リリっぽくない返事を集めて、あとで調整に使う)
                val next = if (m.feedback < 0) 0 else -1
                store.setFeedback(m.time, next)
                alpha = if (next < 0) 0.55f else 1f
                typing.text = if (next < 0) "👎 いまいちな返事として記録しました" else "記録を取り消しました"
                true
            }
        }
        val row = LinearLayout(this).apply {
            gravity = if (m.fromUser) Gravity.END else Gravity.START
            setPadding(0, px(4), 0, px(4))
            addView(tv)
        }
        list.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun scrollDown() = scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty() || waiting) return
        input.setText("")
        // 自分の吹き出しは保存せずに表示だけ(保存は LiliBrain がする)
        addBubble(ChatMessage(true, text, System.currentTimeMillis()), save = false)
        scrollDown()
        waiting = true
        send.isEnabled = false
        typing.text = "入力中…"
        LiliBrain.reply(this, text) { lines -> showLines(lines) }
    }

    /** 人がチャットするみたいに、少し間をあけて1行ずつ出す */
    private fun showLines(lines: List<String>) {
        var delay = 300L
        val now = System.currentTimeMillis()
        lines.forEachIndexed { i, line ->
            list.postDelayed({
                if (isFinishing) return@postDelayed
                addBubble(ChatMessage(false, line, now + i), save = false)
                maid.react()
                scrollDown()
                if (i == lines.lastIndex) {
                    waiting = false
                    send.isEnabled = true
                    typing.text = "オンライン"
                }
            }, delay)
            delay += 500L + line.length * 35L
        }
    }

    private fun showNotebook() {
        val edit = EditText(this).apply {
            setText(store.notebook)
            hint = "まだ何も書いていません。リリと話すと、少しずつ覚えていきます"
            textSize = 14f
            minLines = 6
            gravity = Gravity.TOP
        }
        val box = LinearLayout(this).apply { setPadding(px(20), px(8), px(20), 0); addView(edit, LinearLayout.LayoutParams(-1, -2)) }
        AlertDialog.Builder(this)
            .setTitle("📒 ご主人様ノート")
            .setMessage("リリが覚えていることです。まちがいは直したり消したりできます。")
            .setView(box)
            .setPositiveButton("保存") { _, _ -> store.notebook = edit.text.toString() }
            .setNeutralButton("会話の記録を消す") { _, _ ->
                AlertDialog.Builder(this).setMessage("これまでの会話の記録を消しますか？（ノートは残ります）")
                    .setPositiveButton("消す") { _, _ -> store.clear(); render() }
                    .setNegativeButton("やめる", null).show()
            }
            .setNegativeButton("閉じる", null)
            .show()
    }
}
