package com.mochimate.maid

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ごはんの記録画面(手入力 + よく食べるものをワンタップ) */
class CalorieActivity : Activity() {

    private lateinit var store: CalorieStore
    private lateinit var prefs: Prefs
    private lateinit var mochi: MochiView
    private lateinit var say: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calorie)
        store = CalorieStore(this)
        prefs = Prefs(this)
        mochi = findViewById(R.id.mochi)
        say = findViewById(R.id.say)
        mochi.setOnClickListener { mochi.react(); say.text = store.line(prefs.masterName) }

        setupManualInput()
        setupProfile()
        refresh()
        say.text = store.line(prefs.masterName)
    }

    private fun setupManualInput() {
        val name = findViewById<EditText>(R.id.foodName)
        val kcal = findViewById<EditText>(R.id.foodKcal)
        val fav = findViewById<CheckBox>(R.id.saveFavorite)
        findViewById<Button>(R.id.addFood).setOnClickListener {
            val n = name.text.toString().trim()
            val k = kcal.text.toString().toIntOrNull()
            when {
                n.isEmpty() -> { say.text = "料理の名前を教えてください！"; name.requestFocus() }
                k == null || k <= 0 -> { say.text = "カロリーも入れてくださいね。だいたいで大丈夫ですよ！"; kcal.requestFocus() }
                else -> {
                    if (fav.isChecked) store.addFavorite(n, k)
                    record(n, k)
                    name.setText(""); kcal.setText(""); fav.isChecked = false
                    getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(name.windowToken, 0)
                }
            }
        }
    }

    private fun record(name: String, kcal: Int) {
        store.add(name, kcal)
        mochi.react()
        refresh()
        say.text = "${name}（${kcal}kcal）を記録しました！\n" + store.line(prefs.masterName)
    }

    private fun refresh() {
        refreshGoalOnly()
        renderPresets()
        renderEntries()
    }

    private fun renderPresets() {
        val box = findViewById<FlowLayout>(R.id.presets)
        box.removeAllViews()
        val d = resources.displayMetrics.density
        for (p in store.presets()) {
            box.addView(TextView(this).apply {
                text = (if (p.custom) "⭐ " else "") + "${p.name}  ${p.kcal}"
                textSize = 14f
                setTextColor(getColor(R.color.ink))
                setBackgroundResource(if (p.custom) R.drawable.chip_custom_bg else R.drawable.chip_bg)
                setPadding((12 * d).toInt(), (8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
                setOnClickListener { record(p.name, p.kcal) }
                if (p.custom) setOnLongClickListener {
                    store.removeFavorite(p.name)
                    say.text = "「${p.name}」をよく食べるものから外しました。"
                    refresh()
                    true
                }
            })
        }
    }

    private fun renderEntries() {
        val box = findViewById<LinearLayout>(R.id.entries)
        box.removeAllViews()
        val entries = store.todayEntries()
        val d = resources.displayMetrics.density
        if (entries.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "まだ何も記録されていません"
                setTextColor(getColor(R.color.ink_sub))
                textSize = 14f
                setPadding(0, (8 * d).toInt(), 0, 0)
            })
            return
        }
        val fmt = SimpleDateFormat("H:mm", Locale.JAPAN)
        entries.forEachIndexed { i, e ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (6 * d).toInt(), 0, (6 * d).toInt())
            }
            row.addView(TextView(this).apply {
                text = fmt.format(Date(e.time))
                setTextColor(getColor(R.color.ink_sub))
                textSize = 13f
                minWidth = (44 * d).toInt()
            })
            row.addView(TextView(this).apply {
                text = e.name
                setTextColor(getColor(R.color.ink))
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = "${e.kcal} kcal"
                setTextColor(getColor(R.color.ink))
                textSize = 15f
            })
            row.addView(TextView(this).apply {
                text = "✕"
                setTextColor(getColor(R.color.ink_sub))
                textSize = 16f
                setPadding((14 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
                contentDescription = "${e.name}を消す"
                setOnClickListener {
                    store.remove(i)
                    say.text = "「${e.name}」を消しました。"
                    refresh()
                }
            })
            box.addView(row)
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
                    refreshGoalOnly()
                }
            })
        }
    }

    /** 数字の入力中はリスト全体を作り直さず、目標の表示だけ更新する */
    private fun refreshGoalOnly() {
        val total = store.todayTotal()
        val goal = store.goal
        findViewById<TextView>(R.id.total).text = "$total / ${goal} kcal"
        findViewById<ProgressBar>(R.id.progress).progress =
            if (goal > 0) (total * 1000 / goal).coerceIn(0, 1000) else 0
        val left = goal - total
        findViewById<TextView>(R.id.left).text =
            if (left >= 0) "あと ${left} kcal" else "${-left} kcal オーバー"
        findViewById<TextView>(R.id.goalText).text =
            "1日の目標: ${goal} kcal（基礎代謝 ${store.bmr.toInt()} kcal）"
    }
}
