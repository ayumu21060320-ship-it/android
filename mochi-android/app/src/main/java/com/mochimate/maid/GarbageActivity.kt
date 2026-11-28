package com.mochimate.maid

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate

/** ゴミの日の設定 */
class GarbageActivity : Activity() {

    private lateinit var store: GarbageStore
    private lateinit var col: LinearLayout
    private val d by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GarbageStore(this)
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(16), px(20), px(24)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(getColor(R.color.bg)); fitsSystemWindows = true; addView(col) })
        render()
    }

    private fun render() {
        col.removeAllViews()
        col.addView(text("🗑️ ゴミの日", 20f, bold = true))

        // 今日・明日
        val c = card()
        val today = store.on(LocalDate.now())
        val tomorrow = store.on(LocalDate.now().plusDays(1))
        c.addView(text("今日：" + (if (today.isEmpty()) "なし" else today.joinToString("・") { "${it.emoji}${it.name}" }), 16f, bold = true))
        c.addView(text("明日：" + (if (tomorrow.isEmpty()) "なし" else tomorrow.joinToString("・") { "${it.emoji}${it.name}" }), 15f).apply { setPadding(0, px(4), 0, 0) })

        // ルール
        val list = card()
        list.addView(text("ゴミの種類と曜日（長押しで削除）", 14f, sub = true))
        val rules = store.rules()
        if (rules.isEmpty()) list.addView(text("まだ登録がありません。下の「＋ 追加」から登録してね", 14f, sub = true).apply { setPadding(0, px(8), 0, 0) })
        rules.forEachIndexed { i, r ->
            list.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(8), 0, px(8))
                addView(text("${r.emoji} ${r.name}", 16f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
                addView(text(r.describe(), 14f, sub = true))
                setOnClickListener { editRule(i, r) }
                setOnLongClickListener {
                    AlertDialog.Builder(this@GarbageActivity).setMessage("「${r.name}」を消しますか？")
                        .setPositiveButton("消す") { _, _ -> store.save(store.rules().filterIndexed { j, _ -> j != i }); changed() }
                        .setNegativeButton("やめる", null).show()
                    true
                }
            })
        }
        list.addView(button("＋ 追加") { editRule(null, null) }, LinearLayout.LayoutParams(-1, px(44)).apply { topMargin = px(8) })

        // お知らせの時間
        val t = card()
        t.addView(text("リリが教えてくれる時間", 14f, sub = true))
        fun timeRow(label: String, get: () -> Int, set: (Int) -> Unit) {
            val m = get()
            t.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(8), 0, px(4))
                addView(text(label, 15f), LinearLayout.LayoutParams(0, -2, 1f))
                addView(button(if (m < 0) "オフ" else GarbageStore.hhmm(m)) {
                    val cur = if (m < 0) 7 * 60 else m
                    TimePickerDialog(this@GarbageActivity, { _, h, min -> set(h * 60 + min); changed() }, cur / 60, cur % 60, true).show()
                })
                addView(button(if (m < 0) "オンにする" else "オフにする") { set(if (m < 0) 7 * 60 else -1); changed() },
                    LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(8) })
            })
        }
        timeRow("前の日の夜", { store.eveningMinutes }) { store.eveningMinutes = it }
        timeRow("当日の朝", { store.morningMinutes }) { store.morningMinutes = it }
        t.addView(text("※電池にやさしい方法で知らせるので、数分ずれることがあります", 12f, sub = true).apply { setPadding(0, px(6), 0, 0) })
    }

    private fun changed() {
        GarbageAlarm.schedule(this)
        render()
    }

    private fun editRule(index: Int?, rule: GarbageRule?) {
        var name = rule?.name ?: GarbageRule.PRESETS.first().first
        var emoji = rule?.emoji ?: GarbageRule.PRESETS.first().second
        val days = (rule?.days ?: emptySet()).toMutableSet()
        val weeks = (rule?.weeks ?: emptySet()).toMutableSet()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(8), px(20), 0) }
        val custom = EditText(this).apply { hint = "種類の名前"; setText(name); textSize = 15f }

        fun chips(options: List<String>, isOn: (Int) -> Boolean, toggle: (Int) -> Unit): FlowLayout {
            val f = FlowLayout(this).apply { setPadding(0, px(6), 0, px(6)) }
            fun draw() {
                f.removeAllViews()
                options.forEachIndexed { i, o ->
                    f.addView(text(o, 14f).apply {
                        setBackgroundResource(if (isOn(i)) R.drawable.chip_custom_bg else R.drawable.chip_bg)
                        setPadding(px(12), px(6), px(12), px(6))
                        setOnClickListener { toggle(i); draw() }
                    })
                }
            }
            draw()
            return f
        }
        box.addView(text("種類", 13f, sub = true))
        box.addView(chips(GarbageRule.PRESETS.map { "${it.second}${it.first}" }, { GarbageRule.PRESETS[it].first == name }) { i ->
            name = GarbageRule.PRESETS[i].first; emoji = GarbageRule.PRESETS[i].second; custom.setText(name)
        })
        box.addView(custom)
        box.addView(text("曜日", 13f, sub = true).apply { setPadding(0, px(8), 0, 0) })
        box.addView(chips(GarbageRule.DAY_NAMES, { (it + 1) in days }) { i -> if (!days.remove(i + 1)) days.add(i + 1) })
        box.addView(text("第何週？（何も選ばなければ毎週）", 13f, sub = true).apply { setPadding(0, px(8), 0, 0) })
        box.addView(chips((1..5).map { "第$it" }, { (it + 1) in weeks }) { i -> if (!weeks.remove(i + 1)) weeks.add(i + 1) })

        AlertDialog.Builder(this)
            .setTitle(if (rule == null) "ゴミの日を追加" else "ゴミの日を変える")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("保存") { _, _ ->
                val n = custom.text.toString().trim().ifEmpty { name }
                if (days.isEmpty()) return@setPositiveButton
                val e = GarbageRule.PRESETS.firstOrNull { it.first == n }?.second ?: if (n == name) emoji else "🗑️"
                val newRule = GarbageRule(n, e, days.toSet(), weeks.toSet())
                val list = store.rules().toMutableList()
                if (index == null) list += newRule else list[index] = newRule
                store.save(list)
                Prefs(this).careGarbage = true
                changed()
            }
            .setNegativeButton("やめる", null)
            .show()
    }

    // ---------- 部品 ----------

    private fun card(): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_bg)
            setPadding(px(16), px(14), px(16), px(14))
        }
        col.addView(c, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) })
        return c
    }

    private fun text(t: String, size: Float, bold: Boolean = false, sub: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(getColor(if (sub) R.color.ink_sub else R.color.ink)); if (bold) paint.isFakeBoldText = true
    }

    private fun button(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t; textSize = 14f; gravity = Gravity.CENTER
        setTextColor(getColor(R.color.accent)); paint.isFakeBoldText = true
        setBackgroundResource(R.drawable.button_sub_bg)
        setPadding(px(12), px(6), px(12), px(6))
        setOnClickListener { onClick() }
    }
}
