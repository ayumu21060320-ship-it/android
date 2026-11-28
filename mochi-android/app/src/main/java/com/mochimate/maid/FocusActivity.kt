package com.mochimate.maid

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.NumberPicker
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
            c.addView(button("やめる") { FocusService.stop(this); handler.postDelayed({ render() }, 400) }, LinearLayout.LayoutParams(-1, px(48)).apply { topMargin = px(10) })
        } else {
            // 時計アプリのタイマーのように、時間・分・秒をくるくる回して選ぶ
            val last = store.lastSeconds
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            fun wheel(max: Int, value: Int, unit: String): NumberPicker {
                val p = NumberPicker(this).apply {
                    minValue = 0; maxValue = max; this.value = value; wrapSelectorWheel = true
                    setFormatter { "%02d".format(it) }
                    setOnValueChangedListener { _, _, _ -> updateClock(selectedSeconds()) }
                }
                row.addView(p, LinearLayout.LayoutParams(px(72), -2))
                row.addView(text(unit, 15f, sub = true).apply { setPadding(px(2), 0, px(10), 0) })
                return p
            }
            hours = wheel(23, last / 3600, "時間")
            mins = wheel(59, last % 3600 / 60, "分")
            secs = wheel(59, last % 60, "秒")
            c.addView(row, LinearLayout.LayoutParams(-1, -2))
            clock.visibility = android.view.View.GONE
            c.addView(button("はじめる") {
                val total = selectedSeconds()
                if (total <= 0) { say.text = "時間を決めてくださいね！"; return@button }
                store.lastSeconds = total
                FocusService.start(this, total)
                maid.react()
                handler.postDelayed({ render() }, 300)
            }.apply {
                setBackgroundResource(R.drawable.button_bg)
                setTextColor(0xFFFFFFFF.toInt())
            }, LinearLayout.LayoutParams(-1, px(52)).apply { topMargin = px(12) })
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
                    addView(text("${r.date.monthValue}/${r.date.dayOfMonth}", 14f), LinearLayout.LayoutParams(0, -2, 1f))
                    addView(text(FocusStore.hm(r.minutes), 14f, sub = true))
                })
            }
        }
        wasRunning = FocusService.running
        updateClock()
    }

    private var hours: NumberPicker? = null
    private var mins: NumberPicker? = null
    private var secs: NumberPicker? = null

    private fun selectedSeconds() = (hours?.value ?: 0) * 3600 + (mins?.value ?: 0) * 60 + (secs?.value ?: 0)

    private fun updateClock(@Suppress("UNUSED_PARAMETER") preview: Int? = null) {
        if (!::clock.isInitialized) return
        if (FocusService.running) {
            val s = (FocusService.remainingMs() / 1000).toInt()
            clock.text = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
            say.text = when {
                s > FocusService.totalSeconds - 10 -> "よーし、がんばりましょう！わたしは静かにしてますね"
                s < 60 -> "あとちょっとです！"
                else -> "がんばってますね…（そーっと見守り中）"
            }
        } else {
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
