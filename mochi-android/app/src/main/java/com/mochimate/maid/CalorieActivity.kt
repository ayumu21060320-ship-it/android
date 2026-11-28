package com.mochimate.maid

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** ごはんの記録画面: 今日のリングと PFC、朝・昼・夜・間食ごとの記録 */
class CalorieActivity : Activity() {

    private lateinit var store: CalorieStore
    private lateinit var prefs: Prefs
    private lateinit var mochi: MochiView
    private lateinit var say: TextView
    private val d by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calorie)
        store = CalorieStore(this)
        prefs = Prefs(this)
        mochi = findViewById(R.id.mochi)
        say = findViewById(R.id.say)
        mochi.setOnClickListener { mochi.react(); say.text = store.line(prefs.masterName) }
        findViewById<TextView>(R.id.source).text = FoodDb.SOURCE
        say.text = store.line(prefs.masterName)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        FoodSearchActivity.consumeLastAdded()?.let {
            mochi.react()
            say.text = "${it}を記録しました！\n" + store.line(prefs.masterName)
        }
    }

    private fun refresh() {
        val t = store.total()
        findViewById<TextView>(R.id.dateLabel).text = SimpleDateFormat("M月d日(E)", Locale.JAPAN).format(Date()) + " のカロリー"
        findViewById<RingView>(R.id.ring).set(t.kcal, store.goal)
        renderPfc(t)
        renderScore(t)
        renderMeals()
    }

    private fun renderScore(t: DayTotal) {
        val box = findViewById<LinearLayout>(R.id.scoreCard)
        box.removeAllViews()
        val s = DietScore.of(store, t)
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            text = "📝 今日の点数"; textSize = 16f; setTextColor(getColor(R.color.ink)); paint.isFakeBoldText = true
        }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(TextView(this).apply {
            text = s?.let { "${it.total}点" } ?: "－"
            textSize = 26f; setTextColor(getColor(R.color.accent)); paint.isFakeBoldText = true
        })
        box.addView(head)
        box.addView(TextView(this).apply {
            text = if (s == null) "記録すると、カロリーと栄養バランスから点数をつけます"
            else "${s.rank}　カロリー ${s.kcal}/40・たんぱく質 ${s.p}/20・脂質 ${s.f}/20・炭水化物 ${s.c}/20"
            textSize = 13f; setTextColor(getColor(R.color.ink_sub)); setPadding(0, px(4), 0, 0)
        })
        if (s != null) {
            box.addView(TextView(this).apply {
                text = "メイドさんのふり返りを聞く ›"
                textSize = 14f; setTextColor(getColor(R.color.accent)); paint.isFakeBoldText = true
                setPadding(0, px(8), 0, 0)
                setOnClickListener { mochi.react(); say.text = DietScore.review(store, prefs.masterName) }
            })
        }
    }

    private fun renderPfc(t: DayTotal) {
        val box = findViewById<LinearLayout>(R.id.pfc)
        box.removeAllViews()
        fun row(label: String, color: String, value: Double, goal: Double) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(3), 0, px(3))
            }
            r.addView(TextView(this).apply {
                text = label; textSize = 13f; setTextColor(getColor(R.color.ink)); minWidth = px(86)
            })
            r.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progress = if (goal > 0) (value / goal * 1000).toInt().coerceIn(0, 1000) else 0
                progressDrawable = getDrawable(R.drawable.progress_kcal)?.mutate()
                progressTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(color))
            }, LinearLayout.LayoutParams(0, px(10), 1f))
            r.addView(TextView(this).apply {
                text = "${value.roundToInt()} / ${goal.roundToInt()} g"
                textSize = 12f; setTextColor(getColor(R.color.ink_sub)); gravity = Gravity.END; minWidth = px(84)
            })
            box.addView(r)
        }
        row("たんぱく質", "#E0566E", t.p, store.goalP)
        row("脂質", "#F2B84B", t.f, store.goalF)
        row("炭水化物", "#5B9BD5", t.c, store.goalC)
        box.addView(TextView(this).apply {
            text = "⚙️ 目標を変える（からだの設定）›"
            textSize = 13f
            setTextColor(getColor(R.color.accent))
            gravity = Gravity.END
            setPadding(0, px(8), 0, 0)
            setOnClickListener { startActivity(Intent(this@CalorieActivity, BodySettingsActivity::class.java)) }
        })
    }

    private fun renderMeals() {
        val box = findViewById<LinearLayout>(R.id.meals)
        box.removeAllViews()
        val entries = store.todayEntries().withIndex().toList()
        for (meal in Meal.values()) {
            val list = entries.filter { it.value.meal == meal }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.card_bg)
                setPadding(px(16), px(12), px(16), px(12))
            }
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(TextView(this).apply {
                text = "${meal.emoji} ${meal.label}"
                textSize = 16f; setTextColor(getColor(R.color.ink)); paint.isFakeBoldText = true
            }, LinearLayout.LayoutParams(0, -2, 1f))
            head.addView(TextView(this).apply {
                text = "${list.sumOf { it.value.kcal }} kcal"
                textSize = 14f; setTextColor(getColor(R.color.ink_sub))
            })
            head.addView(TextView(this).apply {
                text = "＋ 追加"
                textSize = 14f
                setTextColor(getColor(R.color.accent))
                paint.isFakeBoldText = true
                setBackgroundResource(R.drawable.button_sub_bg)
                setPadding(px(12), px(5), px(12), px(5))
                setOnClickListener {
                    startActivity(Intent(this@CalorieActivity, FoodSearchActivity::class.java).putExtra(FoodSearchActivity.EXTRA_MEAL, meal.name))
                }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = px(10) })
            card.addView(head)
            for ((i, e) in list) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, px(6), 0, 0)
                }
                row.addView(TextView(this).apply {
                    text = e.name + if (e.grams > 0) "  ${e.grams.roundToInt()}g" else ""
                    textSize = 14f; setTextColor(getColor(R.color.ink))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(TextView(this).apply {
                    text = "${e.kcal} kcal"; textSize = 14f; setTextColor(getColor(R.color.ink))
                })
                row.addView(TextView(this).apply {
                    text = "✕"; textSize = 15f; setTextColor(getColor(R.color.ink_sub))
                    setPadding(px(12), px(2), px(2), px(2))
                    contentDescription = "${e.name}を消す"
                    setOnClickListener {
                        store.remove(i)
                        say.text = "「${e.name}」を消しました。"
                        refresh()
                    }
                })
                card.addView(row)
            }
            box.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) })
        }
    }

}
