package com.mochimate.maid

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import kotlin.math.roundToInt

/** からだの設定(目標カロリーと PFC の計算に使う情報) */
class BodySettingsActivity : Activity() {

    private lateinit var store: CalorieStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_body)
        store = CalorieStore(this)

        findViewById<RadioGroup>(R.id.sex).apply {
            check(if (store.isMale) R.id.male else R.id.female)
            setOnCheckedChangeListener { _, id -> store.isMale = id == R.id.male; refreshGoalText() }
        }
        findViewById<RadioGroup>(R.id.activity).apply {
            check(when (store.activity) { 0 -> R.id.actLow; 2 -> R.id.actHigh; else -> R.id.actMid })
            setOnCheckedChangeListener { _, id ->
                store.activity = when (id) { R.id.actLow -> 0; R.id.actHigh -> 2; else -> 1 }
                refreshGoalText()
            }
        }
        findViewById<RadioGroup>(R.id.plan).apply {
            check(when (store.plan) { -1 -> R.id.planLose; 1 -> R.id.planGain; else -> R.id.planKeep })
            setOnCheckedChangeListener { _, id ->
                store.plan = when (id) { R.id.planLose -> -1; R.id.planGain -> 1; else -> 0 }
                refreshGoalText()
            }
        }
        bindNumber(R.id.age, store.age.toString()) { v -> v.toIntOrNull()?.takeIf { it in 5..120 }?.let { store.age = it } }
        bindNumber(R.id.height, trim(store.heightCm)) { v -> v.toFloatOrNull()?.takeIf { it in 80f..250f }?.let { store.heightCm = it } }
        val latest = WeightStore(this).latest()?.second?.toFloat() ?: store.weightKg
        bindNumber(R.id.weight, trim(latest)) { v -> v.toFloatOrNull()?.takeIf { it in 20f..300f }?.let { store.weightKg = it } }
        refreshGoalText()
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
