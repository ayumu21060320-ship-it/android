package com.mochimate.maid

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
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
        setupProfile()
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
        renderWeight()
        renderMeals()
        refreshGoalText()
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

    private fun renderWeight() {
        val ws = WeightStore(this)
        val box = findViewById<LinearLayout>(R.id.weightCard)
        box.removeAllViews()
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            text = "⚖️ 体重"; textSize = 16f; setTextColor(getColor(R.color.ink)); paint.isFakeBoldText = true
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val last = ws.latest()
        head.addView(TextView(this).apply {
            text = last?.let { "${it.second} kg" } ?: "未記録"
            textSize = 22f; setTextColor(getColor(R.color.ink)); paint.isFakeBoldText = true
        })
        box.addView(head)
        val diff = ws.diffFromPrevious()
        val t = ws.target
        box.addView(TextView(this).apply {
            text = buildString {
                if (diff != null) append(if (diff <= 0) "前回より ${"%.1f".format(-diff)}kg 減" else "前回より ${"%.1f".format(diff)}kg 増")
                if (t != null && last != null) {
                    if (isNotEmpty()) append("　")
                    append("目標 ${t}kg まで あと${"%.1f".format(kotlin.math.abs(last.second - t))}kg")
                }
                if (isEmpty()) append("毎日はかると、変化がグラフでわかります")
            }
            textSize = 13f; setTextColor(getColor(R.color.ink_sub)); setPadding(0, px(2), 0, 0)
        })
        val chart = WeightChartView(this).apply {
            set(ws.all(), t, ws.prediction(), 30)
            setOnClickListener { startActivity(Intent(this@CalorieActivity, WeightActivity::class.java)) }
        }
        box.addView(chart, LinearLayout.LayoutParams(-1, px(150)).apply { topMargin = px(6) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun btn(label: String, onClick: () -> Unit) = TextView(this).apply {
            text = label; textSize = 14f; gravity = Gravity.CENTER
            setTextColor(getColor(R.color.accent)); paint.isFakeBoldText = true
            setBackgroundResource(R.drawable.button_sub_bg)
            setOnClickListener { onClick() }
        }
        row.addView(btn("今日の体重を記録") {
            WeightDialogs.record(this) {
                refresh()
                mochi.react()
                say.text = WeightStore(this).line(prefs.masterName)
                findViewById<EditText>(R.id.weight).setText(CalorieStore(this).weightKg.toString())
            }
        }, LinearLayout.LayoutParams(0, px(42), 1f))
        row.addView(btn("グラフ・目標 ›") {
            startActivity(Intent(this@CalorieActivity, WeightActivity::class.java))
        }, LinearLayout.LayoutParams(0, px(42), 1f).apply { marginStart = px(8) })
        box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8) })
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

    private fun setupProfile() {
        findViewById<RadioGroup>(R.id.sex).apply {
            check(if (store.isMale) R.id.male else R.id.female)
            setOnCheckedChangeListener { _, id -> store.isMale = id == R.id.male; refresh() }
        }
        findViewById<RadioGroup>(R.id.activity).apply {
            check(when (store.activity) { 0 -> R.id.actLow; 2 -> R.id.actHigh; else -> R.id.actMid })
            setOnCheckedChangeListener { _, id ->
                store.activity = when (id) { R.id.actLow -> 0; R.id.actHigh -> 2; else -> 1 }
                refresh()
            }
        }
        findViewById<RadioGroup>(R.id.plan).apply {
            check(when (store.plan) { -1 -> R.id.planLose; 1 -> R.id.planGain; else -> R.id.planKeep })
            setOnCheckedChangeListener { _, id ->
                store.plan = when (id) { R.id.planLose -> -1; R.id.planGain -> 1; else -> 0 }
                refresh()
            }
        }
        bindNumber(R.id.age, store.age.toString()) { v -> v.toIntOrNull()?.takeIf { it in 5..120 }?.let { store.age = it } }
        bindNumber(R.id.height, trim(store.heightCm)) { v -> v.toFloatOrNull()?.takeIf { it in 80f..250f }?.let { store.heightCm = it } }
        bindNumber(R.id.weight, trim(store.weightKg)) { v -> v.toFloatOrNull()?.takeIf { it in 20f..300f }?.let { store.weightKg = it } }
    }

    private fun trim(f: Float) = if (f % 1f == 0f) f.toInt().toString() else f.toString()

    private fun bindNumber(id: Int, initial: String, onValid: (String) -> Unit) {
        findViewById<EditText>(id).apply {
            setText(initial)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    onValid(s?.toString().orEmpty())
                    val t = store.total()
                    findViewById<RingView>(R.id.ring).set(t.kcal, store.goal)
                    renderPfc(t)
                    refreshGoalText()
                }
            })
        }
    }

    private fun refreshGoalText() {
        findViewById<TextView>(R.id.goalText).text =
            "1日の目標: ${store.goal} kcal（基礎代謝 ${store.bmr.toInt()} kcal）\n" +
            "たんぱく質 ${store.goalP.roundToInt()}g・脂質 ${store.goalF.roundToInt()}g・炭水化物 ${store.goalC.roundToInt()}g"
    }
}
