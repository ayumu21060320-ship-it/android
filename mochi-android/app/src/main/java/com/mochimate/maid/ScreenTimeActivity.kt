package com.mochimate.maid

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/** スマホの使いすぎチェック */
class ScreenTimeActivity : Activity() {

    private lateinit var col: LinearLayout
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
        col.addView(text("📱 スマホの使いすぎチェック", 20f, bold = true))

        if (!ScreenTime.granted(this)) {
            val c = card()
            c.addView(text("どのアプリを何分使ったかを見るために、「使用状況へのアクセス」の許可が必要です。", 14f))
            c.addView(text("ボタンを押すと設定が開くので、「もちもちメイド」をオンにして戻ってきてね。使った時間はスマホの中だけで数えて、どこにも送りません。", 13f, sub = true).apply { setPadding(0, px(6), 0, 0) })
            c.addView(button("許可しにいく") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, LinearLayout.LayoutParams(-1, px(46)).apply { topMargin = px(10) })
            return
        }

        val usage = ScreenTime.today(this)
        val total = usage.sumOf { it.second }
        val tl = ScreenTime.totalLimit(this)
        val sum = card()
        sum.addView(text("今日の合計", 13f, sub = true))
        sum.addView(text(ScreenTime.hm(total), 30f, bold = true))
        sum.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text(if (tl > 0) "1日の上限 ${ScreenTime.hm(tl * 60_000L)}" else "1日の上限なし", 14f, sub = true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(button("上限を決める") { pickLimit("スマホ全体", tl) { ScreenTime.setTotalLimit(this@ScreenTimeActivity, it); render() } })
        })

        val list = card()
        list.addView(text("アプリごと（タップで上限を決める）", 13f, sub = true))
        if (usage.isEmpty()) list.addView(text("まだ1分以上使ったアプリはありません", 14f, sub = true).apply { setPadding(0, px(8), 0, 0) })
        val limits = ScreenTime.limits(this)
        val maxMs = usage.maxOfOrNull { it.second } ?: 1L
        usage.take(15).forEach { (pkg, ms) ->
            val lim = limits[pkg] ?: 0
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, px(8), 0, px(4))
                setOnClickListener { pickLimit(ScreenTime.label(this@ScreenTimeActivity, pkg), lim) { ScreenTime.setLimit(this@ScreenTimeActivity, pkg, it); render() } }
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(ImageView(this).apply { setImageDrawable(runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()) }, LinearLayout.LayoutParams(px(26), px(26)))
            top.addView(text(ScreenTime.label(this, pkg), 15f).apply { setPadding(px(8), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
            val over = lim > 0 && ms >= lim * 60_000L
            top.addView(text(ScreenTime.hm(ms) + if (lim > 0) " / ${ScreenTime.hm(lim * 60_000L)}" else "", 14f, bold = over).apply {
                if (over) setTextColor(getColor(R.color.accent))
            })
            row.addView(top)
            row.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progress = (ms * 1000 / maxMs).toInt()
                progressDrawable = getDrawable(R.drawable.progress_kcal)
            }, LinearLayout.LayoutParams(-1, px(8)).apply { topMargin = px(4) })
            list.addView(row)
        }
    }

    private fun pickLimit(title: String, current: Int, done: (Int) -> Unit) {
        val options = listOf(0, 15, 30, 60, 90, 120, 180, 240)
        val labels = options.map { if (it == 0) "上限なし" else ScreenTime.hm(it * 60_000L) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("$title の1日の上限")
            .setSingleChoiceItems(labels, options.indexOf(current).coerceAtLeast(0)) { dlg, i -> done(options[i]); dlg.dismiss() }
            .setNegativeButton("やめる", null)
            .show()
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
        setPadding(px(12), px(8), px(12), px(8))
        setOnClickListener { onClick() }
    }
}
