package com.mochimate.maid

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** 今日のやることリスト */
class TodoActivity : Activity() {

    private lateinit var store: TodoStore
    private lateinit var col: LinearLayout
    private lateinit var say: TextView
    private lateinit var maid: MochiView
    private val d by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TodoStore(this)
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(16), px(20), px(24)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(getColor(R.color.bg)); fitsSystemWindows = true; addView(col) })
        render()
    }

    private fun render() {
        col.removeAllViews()
        col.addView(text("✅ 今日のやること", 20f, bold = true))

        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, px(8), 0, 0) }
        maid = MochiView(this)
        head.addView(maid, LinearLayout.LayoutParams(px(60), px(76)))
        say = text(line(), 14f).apply { setBackgroundResource(R.drawable.bubble_card_bg); setPadding(px(12), px(8), px(12), px(8)) }
        head.addView(say, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(6) })
        col.addView(head)

        // 追加
        val add = card()
        val input = EditText(this).apply {
            hint = "やることを追加（例: レポートを出す）"
            textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine()
        }
        fun submit() {
            val t = input.text.toString().trim()
            if (t.isEmpty()) return
            store.add(t)
            render()
        }
        input.setOnEditorActionListener { _, _, _ -> submit(); true }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text("追加", 15f, bold = true).apply {
            setTextColor(getColor(R.color.accent))
            setBackgroundResource(R.drawable.button_sub_bg)
            setPadding(px(16), px(8), px(16), px(8))
            setOnClickListener { submit() }
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(8) })
        add.addView(row)

        // 一覧
        val list = card()
        val items = store.today()
        if (items.isEmpty()) list.addView(text("今日のやることはまだありません", 14f, sub = true))
        items.forEach { t ->
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(CheckBox(this@TodoActivity).apply {
                    isChecked = t.done
                    text = t.text
                    textSize = 16f
                    setTextColor(getColor(if (t.done) R.color.ink_sub else R.color.ink))
                    paintFlags = if (t.done) paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG else paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    setOnClickListener {
                        store.toggle(t.id)
                        maid.react()
                        render()
                        if (!t.done) say.text = if (store.today().all { it.done }) "全部おわりました！すごいです、${Prefs(this@TodoActivity).masterName}♪" else "「${t.text}」おわりましたね！えらいです♪"
                    }
                    setOnLongClickListener {
                        AlertDialog.Builder(this@TodoActivity).setMessage("「${t.text}」を消しますか？")
                            .setPositiveButton("消す") { _, _ -> store.remove(t.id); render() }
                            .setNegativeButton("やめる", null).show()
                        true
                    }
                }, LinearLayout.LayoutParams(-1, -2))
            })
        }
        list.addView(text("終わらなかったものは、次の日に自動で残ります。長押しで削除", 12f, sub = true).apply { setPadding(0, px(8), 0, 0) })
    }

    private fun line(): String {
        val items = store.today()
        val left = items.count { !it.done }
        return when {
            items.isEmpty() -> "今日やること、書いておきませんか？わたしが覚えておきますよ！"
            left == 0 -> "今日のやること、全部おわってます！おつかれさまです♪"
            else -> "あと${left}つですね。いっしょにがんばりましょ！"
        }
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_bg)
            setPadding(px(16), px(12), px(16), px(12))
        }
        col.addView(c, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) })
        return c
    }

    private fun text(t: String, size: Float, bold: Boolean = false, sub: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(getColor(if (sub) R.color.ink_sub else R.color.ink)); if (bold) paint.isFakeBoldText = true
    }
}
