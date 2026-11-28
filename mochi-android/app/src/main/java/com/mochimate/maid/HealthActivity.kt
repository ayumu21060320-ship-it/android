package com.mochimate.maid

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

/** 健康管理の入り口: カロリー・体重・からだの設定へのカード */
class HealthActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var mochi: MochiView
    private lateinit var say: TextView
    private lateinit var cards: LinearLayout
    private val d by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(16), px(20), px(20))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.bg))
            fitsSystemWindows = true
            addView(root)
        })
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        mochi = MochiView(this)
        head.addView(mochi, LinearLayout.LayoutParams(px(64), px(82)))
        say = TextView(this).apply { textSize = 15f; setTextColor(getColor(R.color.ink)); setPadding(px(8), 0, 0, 0) }
        head.addView(say, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(head)
        root.addView(TextView(this).apply {
            text = "🩺 健康管理"; textSize = 20f; setTextColor(getColor(R.color.ink)); paint.isFakeBoldText = true
            setPadding(0, px(8), 0, 0)
        })
        cards = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(cards)
        mochi.setOnClickListener { mochi.react(); say.text = CalorieStore(this).line(prefs.masterName) }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        cards.removeAllViews()
        val cal = CalorieStore(this)
        val ws = WeightStore(this)
        say.text = cal.line(prefs.masterName)

        // 🍙 カロリー
        val t = cal.total()
        val score = DietScore.of(cal, t)
        card("🍙 カロリー", "${t.kcal} / ${cal.goal} kcal",
            "今日の点数 ${score?.let { "${it.total}点" } ?: "－"}　食事を記録する ›",
            progress = t.kcal.toDouble() / cal.goal) {
            startActivity(Intent(this, CalorieActivity::class.java))
        }

        // ⚖️ 体重
        val last = ws.latest()
        val diff = ws.diffFromPrevious()
        val target = ws.target
        val sub = buildString {
            if (diff != null) append(if (diff <= 0) "前回より ${"%.1f".format(-diff)}kg 減" else "前回より ${"%.1f".format(diff)}kg 増")
            if (target != null && last != null) {
                if (isNotEmpty()) append("　")
                append("目標まで あと${"%.1f".format(abs(last.second - target))}kg")
            }
            if (isEmpty()) append("記録してグラフで見る ›")
        }
        card("⚖️ 体重", last?.let { "${it.second} kg" } ?: "未記録", sub) {
            startActivity(Intent(this, WeightActivity::class.java))
        }

        // ⚙️ からだの設定
        card("⚙️ からだの設定", "", "年齢・身長・ふだんの動き・目標（1日 ${cal.goal} kcal）›") {
            startActivity(Intent(this, BodySettingsActivity::class.java))
        }
    }

    private fun card(title: String, value: String, sub: String, progress: Double? = null, onClick: () -> Unit) {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_bg)
            setPadding(px(16), px(14), px(16), px(14))
            setOnClickListener { onClick() }
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            text = title; textSize = 17f; setTextColor(getColor(R.color.ink)); paint.isFakeBoldText = true
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (value.isNotEmpty()) head.addView(TextView(this).apply {
            text = value; textSize = 20f; setTextColor(getColor(R.color.ink)); paint.isFakeBoldText = true
        })
        c.addView(head)
        if (progress != null) {
            c.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                this.progress = (progress * 1000).toInt().coerceIn(0, 1000)
                progressDrawable = getDrawable(R.drawable.progress_kcal)
            }, LinearLayout.LayoutParams(-1, px(10)).apply { topMargin = px(8) })
        }
        c.addView(TextView(this).apply {
            text = sub; textSize = 13f; setTextColor(getColor(R.color.ink_sub)); setPadding(0, px(6), 0, 0)
        })
        cards.addView(c, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) })
    }
}
