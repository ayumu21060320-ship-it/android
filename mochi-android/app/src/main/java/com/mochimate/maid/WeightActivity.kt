package com.mochimate.maid

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate
import kotlin.math.abs

/** 体重のグラフ・目標・記録の一覧 */
class WeightActivity : Activity() {

    private lateinit var store: WeightStore
    private lateinit var chart: WeightChartView
    private lateinit var summary: TextView
    private lateinit var history: LinearLayout
    private lateinit var ranges: FlowLayout
    private var days = 30
    private val d by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = WeightStore(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(16), px(20), px(20))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.bg))
            fitsSystemWindows = true
            addView(root)
        })
        root.addView(TextView(this).apply {
            text = "⚖️ 体重"; textSize = 20f; setTextColor(getColor(R.color.ink)); paint.isFakeBoldText = true
        })

        val card = card(root)
        ranges = FlowLayout(this)
        card.addView(ranges)
        renderRanges()
        chart = WeightChartView(this)
        card.addView(chart, LinearLayout.LayoutParams(-1, px(240)).apply { topMargin = px(8) })
        summary = TextView(this).apply { textSize = 14f; setTextColor(getColor(R.color.ink)); setPadding(0, px(8), 0, 0) }
        card.addView(summary)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(button("今日の体重を記録") { WeightDialogs.record(this) { refresh() } }, LinearLayout.LayoutParams(0, px(44), 1f))
        buttons.addView(button("目標を決める") { WeightDialogs.target(this) { refresh() } },
            LinearLayout.LayoutParams(0, px(44), 1f).apply { marginStart = px(8) })
        root.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) })

        val list = card(root)
        list.addView(TextView(this).apply {
            text = "記録（長押しで削除）"; textSize = 15f; setTextColor(getColor(R.color.ink)); paint.isFakeBoldText = true
        })
        history = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list.addView(history)
        refresh()
    }

    private fun renderRanges() {
        ranges.removeAllViews()
        for ((label, n) in listOf("1週間" to 7, "1か月" to 30, "3か月" to 90, "全部" to 0)) {
            ranges.addView(TextView(this).apply {
                text = label; textSize = 13f; setTextColor(getColor(R.color.ink))
                setBackgroundResource(if (n == days) R.drawable.chip_custom_bg else R.drawable.chip_bg)
                setPadding(px(12), px(5), px(12), px(5))
                setOnClickListener { days = n; renderRanges(); refresh() }
            })
        }
    }

    private fun refresh() {
        val all = store.all()
        chart.set(all, store.target, store.prediction(), days)
        val last = all.lastOrNull()
        val start = store.startWeight()
        val t = store.target
        summary.text = buildString {
            if (last == null) { append("まだ記録がありません"); return@buildString }
            append("いま ${last.second}kg")
            if (start != null && start.first != last.first) {
                val diff = last.second - start.second
                val days = last.first.toEpochDay() - start.first.toEpochDay()
                append("\n始めてから${days}日で ${if (diff <= 0) "${"%.1f".format(-diff)}kg 減" else "${"%.1f".format(diff)}kg 増"}")
            }
            if (t != null) {
                append("\n目標 ${t}kg まで あと${"%.1f".format(abs(last.second - t))}kg")
                store.prediction()?.let { append("（このペースだと ${it.monthValue}月${it.dayOfMonth}日ごろ）") }
            }
        }
        history.removeAllViews()
        for ((date, kg) in all.reversed()) {
            history.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(6), 0, px(6))
                addView(TextView(this@WeightActivity).apply {
                    text = "${date.monthValue}/${date.dayOfMonth}"; textSize = 14f; setTextColor(getColor(R.color.ink_sub))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(TextView(this@WeightActivity).apply { text = "$kg kg"; textSize = 15f; setTextColor(getColor(R.color.ink)) })
                setOnLongClickListener {
                    AlertDialog.Builder(this@WeightActivity)
                        .setMessage("${date.monthValue}/${date.dayOfMonth} の記録を消しますか？")
                        .setPositiveButton("消す") { _, _ -> store.remove(date); refresh() }
                        .setNegativeButton("やめる", null).show()
                    true
                }
            })
        }
    }

    private fun card(root: LinearLayout): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_bg)
            setPadding(px(16), px(14), px(16), px(14))
        }
        root.addView(c, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) })
        return c
    }

    private fun button(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t; textSize = 14f; gravity = Gravity.CENTER
        setTextColor(getColor(R.color.accent)); paint.isFakeBoldText = true
        setBackgroundResource(R.drawable.button_sub_bg)
        setOnClickListener { onClick() }
    }
}

/** 体重の記録・目標のダイアログ(記録画面と体重画面で共通) */
object WeightDialogs {

    fun record(a: Activity, done: () -> Unit) {
        val store = WeightStore(a)
        val input = EditText(a).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "例: 62.4"
            textSize = 22f
            gravity = Gravity.END
            store.latest()?.let { setText(it.second.toString()) }
            selectAll()
        }
        val box = LinearLayout(a).apply { setPadding((20 * a.resources.displayMetrics.density).toInt(), 0, (20 * a.resources.displayMetrics.density).toInt(), 0); addView(input, LinearLayout.LayoutParams(-1, -2)) }
        AlertDialog.Builder(a)
            .setTitle("今日の体重（kg）")
            .setView(box)
            .setPositiveButton("記録する") { _, _ ->
                val kg = input.text.toString().toDoubleOrNull()?.takeIf { it in 20.0..300.0 } ?: return@setPositiveButton
                store.set(LocalDate.now(), Math.round(kg * 10) / 10.0)
                done()
            }
            .setNegativeButton("やめる", null)
            .show()
    }

    fun target(a: Activity, done: () -> Unit) {
        val store = WeightStore(a)
        val input = EditText(a).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "例: 58"
            textSize = 22f
            gravity = Gravity.END
            store.target?.let { setText(it.toString()) }
        }
        val box = LinearLayout(a).apply { setPadding((20 * a.resources.displayMetrics.density).toInt(), 0, (20 * a.resources.displayMetrics.density).toInt(), 0); addView(input, LinearLayout.LayoutParams(-1, -2)) }
        AlertDialog.Builder(a)
            .setTitle("目標体重（kg）")
            .setMessage("目標に合わせて、1日の目標カロリーも自動で調整します。")
            .setView(box)
            .setPositiveButton("決める") { _, _ ->
                store.target = input.text.toString().toDoubleOrNull()?.takeIf { it in 20.0..300.0 }
                done()
            }
            .setNeutralButton("目標なし") { _, _ -> store.target = null; done() }
            .setNegativeButton("やめる", null)
            .show()
    }
}
