package com.mochimate.maid

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.format.TextStyle
import java.util.Locale

/** ふだんの生活リズム(24時間の円グラフ) */
class RoutineActivity : Activity() {

    private lateinit var col: LinearLayout
    private var weekend = Routine.isWeekend(java.time.LocalDate.now())
    private val d by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(16), px(20), px(24)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(getColor(R.color.bg)); fitsSystemWindows = true; addView(col) })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        col.removeAllViews()
        col.addView(text("🕰️ 生活リズム", 20f, bold = true))
        col.addView(text("スマホの記録から、リリがふだんの1日を読み取りました。記録はこのスマホの中だけで使います。", 13f, sub = true).apply { setPadding(0, px(4), 0, 0) })

        if (!ScreenTime.granted(this)) {
            val c = card()
            c.addView(text("起きる・寝る時間を読み取るには、「使用状況へのアクセス」の許可が必要です。", 14f))
            c.addView(button("許可しにいく") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, LinearLayout.LayoutParams(-1, px(44)).apply { topMargin = px(8) })
        }

        val c = card()
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((label, we) in listOf("平日" to false, "休日" to true)) {
            tabs.addView(text(label, 15f, bold = we == weekend).apply {
                gravity = Gravity.CENTER
                setBackgroundResource(if (we == weekend) R.drawable.chip_custom_bg else R.drawable.chip_bg)
                setPadding(0, px(8), 0, px(8))
                setOnClickListener { weekend = we; render() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (we) marginStart = px(8) })
        }
        c.addView(tabs)
        val r = Routine.usual(this, weekend)
        c.addView(RoutineClockView(this).apply { set(r, if (weekend) "休日" else "平日") }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8) })
        c.addView(text("■紺 寝ている　■ピンク 家にいる　■緑 外にいる", 12f, sub = true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2))

        // ふだんの時刻(タップで直せる)
        val t = card()
        t.addView(text("ふだんの時間（タップで直せます）", 13f, sub = true))
        for ((key, label, v) in listOf(
            Triple("wake", "☀️ 起きる", r.wake), Triple("leave", "🚪 家を出る", r.leave),
            Triple("back", "🏠 帰ってくる", r.back), Triple("sleep", "🌙 寝る", r.sleep),
        )) {
            t.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(8), 0, px(4))
                addView(text(label, 15f), LinearLayout.LayoutParams(0, -2, 1f))
                addView(text(Routine.hhmm(v), 18f, bold = true))
                setOnClickListener {
                    val cur = v ?: 7 * 60
                    TimePickerDialog(this@RoutineActivity, { _, h, m -> Routine.setManual(this@RoutineActivity, weekend, key, h * 60 + m); render() }, cur / 60, cur % 60, true).show()
                }
            })
        }
        t.addView(text("自動に戻す", 13f).apply {
            setTextColor(getColor(R.color.accent))
            setPadding(0, px(8), 0, 0)
            setOnClickListener {
                for (k in listOf("wake", "leave", "back", "sleep")) Routine.setManual(this@RoutineActivity, weekend, k, -1)
                render()
            }
        })

        // 最近の記録
        val h = Routine.history(this)
        if (h.isNotEmpty()) {
            val l = card()
            l.addView(text("最近の記録", 13f, sub = true))
            h.entries.take(10).forEach { (date, x) ->
                val w = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.JAPAN)
                l.addView(text("${date.monthValue}/${date.dayOfMonth}($w)　☀️${Routine.hhmm(x.wake)}  🚪${Routine.hhmm(x.leave)}  🏠${Routine.hhmm(x.back)}  🌙${Routine.hhmm(x.sleep)}", 13f).apply { setPadding(0, px(5), 0, 0) })
            }
        }
    }

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
        setOnClickListener { onClick() }
    }
}
