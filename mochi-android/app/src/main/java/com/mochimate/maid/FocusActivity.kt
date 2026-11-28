package com.mochimate.maid

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate

/** 集中タイマーの画面 */
class FocusActivity : Activity() {

    private lateinit var store: FocusStore
    private lateinit var col: LinearLayout
    private lateinit var clock: TextView
    private lateinit var say: TextView
    private lateinit var maid: MochiView
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }
    private var wasRunning = false
    private val d by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FocusStore(this)
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(16), px(20), px(24)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(getColor(R.color.bg)); fitsSystemWindows = true; addView(col) })
    }

    override fun onResume() {
        super.onResume()
        render()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun render() {
        col.removeAllViews()
        col.addView(text("⏱️ 集中タイマー", 20f, bold = true))

        // タイマー
        val c = card()
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        maid = MochiView(this)
        head.addView(maid, LinearLayout.LayoutParams(px(64), px(82)))
        say = text("", 14f).apply {
            setBackgroundResource(R.drawable.bubble_card_bg)
            setPadding(px(12), px(8), px(12), px(8))
        }
        head.addView(say, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(6) })
        c.addView(head)
        clock = text("", 54f, bold = true).apply { gravity = Gravity.CENTER; setPadding(0, px(8), 0, px(4)) }
        c.addView(clock, LinearLayout.LayoutParams(-1, -2))

        if (FocusService.running) {
            c.addView(text("「${FocusService.label}」に集中中", 14f, sub = true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2))
            c.addView(button("やめる") { FocusService.stop(this); handler.postDelayed({ render() }, 400) }, LinearLayout.LayoutParams(-1, px(48)).apply { topMargin = px(10) })
        } else {
            val label = EditText(this).apply { setText(store.lastLabel); hint = "なにに集中する？"; textSize = 16f; setSingleLine() }
            c.addView(text("なにをする？", 13f, sub = true))
            val labels = FlowLayout(this).apply { setPadding(0, px(4), 0, 0) }
            for (l in listOf("勉強", "SPI", "作業", "読書", "掃除")) {
                labels.addView(chip(l, false) { label.setText(l) })
            }
            c.addView(labels)
            c.addView(label)
            var minutes = store.lastMinutes
            c.addView(text("何分？", 13f, sub = true).apply { setPadding(0, px(8), 0, 0) })
            val mins = FlowLayout(this).apply { setPadding(0, px(4), 0, 0) }
            fun drawMins() {
                mins.removeAllViews()
                for (m in listOf(15, 25, 45, 60, 90)) mins.addView(chip("${m}分", m == minutes) { minutes = m; drawMins(); updateClock(m) })
            }
            drawMins()
            c.addView(mins)
            c.addView(button("はじめる") {
                val l = label.text.toString().trim().ifEmpty { "集中" }
                store.lastLabel = l
                store.lastMinutes = minutes
                FocusService.start(this, minutes, l)
                maid.react()
                handler.postDelayed({ render() }, 300)
            }.apply {
                setBackgroundResource(R.drawable.button_bg)
                setTextColor(0xFFFFFFFF.toInt())
            }, LinearLayout.LayoutParams(-1, px(52)).apply { topMargin = px(12) })
            updateClock(minutes)
        }

        // 記録
        val s = card()
        val todayMin = store.minutesOn(LocalDate.now())
        val week = store.lastWeek()
        s.addView(text("今日 ${FocusStore.hm(todayMin)}　この1週間 ${FocusStore.hm(week.sumOf { it.second })}", 15f, bold = true))
        s.addView(WeekBarsView(this).apply { set(week) }, LinearLayout.LayoutParams(-1, px(140)).apply { topMargin = px(8) })
        val recent = store.all().takeLast(5).reversed()
        if (recent.isNotEmpty()) {
            s.addView(text("最近の記録", 13f, sub = true).apply { setPadding(0, px(10), 0, 0) })
            recent.forEach { r ->
                s.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, px(4), 0, 0)
                    addView(text("${r.date.monthValue}/${r.date.dayOfMonth}　${r.label}", 14f), LinearLayout.LayoutParams(0, -2, 1f))
                    addView(text(FocusStore.hm(r.minutes), 14f, sub = true))
                })
            }
        }
        wasRunning = FocusService.running
        updateClock()
    }

    private fun updateClock(previewMinutes: Int? = null) {
        if (!::clock.isInitialized) return
        if (FocusService.running) {
            val s = (FocusService.remainingMs() / 1000).toInt()
            clock.text = "%d:%02d".format(s / 60, s % 60)
            say.text = when {
                s > FocusService.totalMinutes * 60 - 10 -> "よーし、がんばりましょう！わたしは静かにしてますね"
                s < 60 -> "あとちょっとです！"
                else -> "がんばってますね…（そーっと見守り中）"
            }
        } else {
            val m = previewMinutes ?: store.lastMinutes
            clock.text = "%d:00".format(m)
            say.text = "${Prefs(this).masterName}、いっしょにがんばりましょ！始めたら、わたしは静かにしてますね"
            if (wasRunning) { wasRunning = false; render() } // 終わったら記録を更新
        }
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

    private fun chip(t: String, on: Boolean, onClick: () -> Unit) = text(t, 14f).apply {
        setBackgroundResource(if (on) R.drawable.chip_custom_bg else R.drawable.chip_bg)
        setPadding(px(12), px(6), px(12), px(6))
        setOnClickListener { onClick() }
    }

    private fun button(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t; textSize = 16f; gravity = Gravity.CENTER
        setTextColor(getColor(R.color.accent)); paint.isFakeBoldText = true
        setBackgroundResource(R.drawable.button_sub_bg)
        setOnClickListener { onClick() }
    }
}
