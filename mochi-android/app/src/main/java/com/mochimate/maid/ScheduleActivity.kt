package com.mochimate.maid

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** 予定(メイドアプリの中だけ) */
class ScheduleActivity : Activity() {

    private lateinit var store: ScheduleStore
    private lateinit var col: LinearLayout
    private val d by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ScheduleStore(this)
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(16), px(20), px(24)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(getColor(R.color.bg)); fitsSystemWindows = true; addView(col) })
        render()
    }

    private fun render() {
        col.removeAllViews()
        col.addView(text("📅 予定", 20f, bold = true))
        col.addView(button("＋ 予定を追加") { edit(null) }, LinearLayout.LayoutParams(-1, px(46)).apply { topMargin = px(10) })

        val upcoming = store.upcoming()
        if (upcoming.isEmpty()) {
            card().addView(text("これからの予定はありません", 14f, sub = true))
        }
        upcoming.groupBy { it.date }.forEach { (date, plans) ->
            val c = card()
            c.addView(text(dayLabel(date), 14f, sub = true, bold = true))
            plans.forEach { p ->
                c.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, px(8), 0, px(4))
                    addView(text(p.timeText(), 15f, bold = true), LinearLayout.LayoutParams(px(64), -2))
                    addView(LinearLayout(this@ScheduleActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(text(p.title, 16f))
                        val sub = listOfNotNull(
                            p.memo.ifBlank { null },
                            if (p.remindBefore < 0) "お知らせなし" else if (p.time == null) "朝8時にお知らせ" else "${p.remindBefore}分前にお知らせ",
                        ).joinToString("・")
                        addView(text(sub, 12f, sub = true))
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    setOnClickListener { edit(p) }
                })
            }
        }
    }

    private fun dayLabel(date: LocalDate): String {
        val today = LocalDate.now()
        val w = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.JAPAN)
        val base = "${date.monthValue}月${date.dayOfMonth}日($w)"
        return when (date) {
            today -> "今日　$base"
            today.plusDays(1) -> "明日　$base"
            else -> base
        }
    }

    private fun edit(old: Plan?) {
        var date = old?.date ?: LocalDate.now()
        var time: LocalTime? = old?.time ?: LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
        if (old != null) time = old.time
        var remind = old?.remindBefore ?: 30

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(8), px(20), 0) }
        val title = EditText(this).apply { hint = "予定（例: 面接）"; setText(old?.title ?: ""); textSize = 16f; setSingleLine() }
        box.addView(title)
        val dateBtn = button("") {}
        val timeBtn = button("") {}
        val allDay = CheckBox(this).apply { text = "終日"; isChecked = time == null }
        fun refresh() {
            dateBtn.text = dayLabel(date)
            timeBtn.text = time?.let { "%d:%02d".format(it.hour, it.minute) } ?: "時間なし"
            timeBtn.alpha = if (allDay.isChecked) 0.4f else 1f
        }
        dateBtn.setOnClickListener {
            DatePickerDialog(this, { _, y, m, dd -> date = LocalDate.of(y, m + 1, dd); refresh() }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }
        timeBtn.setOnClickListener {
            if (allDay.isChecked) return@setOnClickListener
            val t = time ?: LocalTime.of(9, 0)
            TimePickerDialog(this, { _, h, m -> time = LocalTime.of(h, m); refresh() }, t.hour, t.minute, true).show()
        }
        allDay.setOnCheckedChangeListener { _, c -> time = if (c) null else (time ?: LocalTime.of(9, 0)); refresh() }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, px(8), 0, 0) }
        row.addView(dateBtn, LinearLayout.LayoutParams(0, px(42), 1f))
        row.addView(timeBtn, LinearLayout.LayoutParams(px(96), px(42)).apply { marginStart = px(8) })
        box.addView(row)
        box.addView(allDay)

        box.addView(text("リリが知らせるのは", 13f, sub = true).apply { setPadding(0, px(6), 0, 0) })
        val opts = listOf(-1 to "なし", 0 to "その時間", 10 to "10分前", 30 to "30分前", 60 to "1時間前", 180 to "3時間前")
        val chips = FlowLayout(this).apply { setPadding(0, px(4), 0, 0) }
        fun drawChips() {
            chips.removeAllViews()
            opts.forEach { (v, l) ->
                chips.addView(text(l, 14f).apply {
                    setBackgroundResource(if (v == remind) R.drawable.chip_custom_bg else R.drawable.chip_bg)
                    setPadding(px(12), px(6), px(12), px(6))
                    setOnClickListener { remind = v; drawChips() }
                })
            }
        }
        drawChips()
        box.addView(chips)
        val memo = EditText(this).apply { hint = "メモ（場所・持ち物など）"; setText(old?.memo ?: ""); textSize = 14f }
        box.addView(memo)
        refresh()

        val dlg = AlertDialog.Builder(this)
            .setTitle(if (old == null) "予定を追加" else "予定を変える")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("保存") { _, _ ->
                val t = title.text.toString().trim()
                if (t.isEmpty()) return@setPositiveButton
                store.put(Plan(old?.id ?: System.currentTimeMillis(), t, date, if (allDay.isChecked) null else time, remind, memo.text.toString().trim()))
                Prefs(this).careSchedule = true
                ScheduleAlarm.schedule(this)
                render()
            }
            .setNegativeButton("やめる", null)
        if (old != null) dlg.setNeutralButton("削除") { _, _ -> store.remove(old.id); ScheduleAlarm.schedule(this); render() }
        dlg.show()
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

    private fun button(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t; textSize = 14f; gravity = Gravity.CENTER
        setTextColor(getColor(R.color.accent)); paint.isFakeBoldText = true
        setBackgroundResource(R.drawable.button_sub_bg)
        setOnClickListener { onClick() }
    }
}
