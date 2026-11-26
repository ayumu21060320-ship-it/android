package com.mochimate.maid

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 家計簿の画面 */
class MoneyActivity : Activity() {

    private lateinit var store: MoneyStore
    private lateinit var prefs: Prefs
    private lateinit var mochi: MochiView
    private lateinit var say: TextView
    private var category = MoneyStore.CATEGORIES.first()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_money)
        store = MoneyStore(this)
        prefs = Prefs(this)
        mochi = findViewById(R.id.mochi)
        say = findViewById(R.id.say)
        mochi.setOnClickListener { mochi.react(); say.text = store.line(prefs.masterName) }

        renderCategories()

        val amount = findViewById<EditText>(R.id.amount)
        val memo = findViewById<EditText>(R.id.memo)
        findViewById<Button>(R.id.add).setOnClickListener {
            val a = amount.text.toString().toIntOrNull()
            if (a == null || a <= 0) {
                say.text = "金額を入れてくださいね！"
                amount.requestFocus()
                return@setOnClickListener
            }
            val m = memo.text.toString().trim()
            store.add(MoneyEntry(a, category, m, System.currentTimeMillis(), null))
            amount.setText(""); memo.setText("")
            getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(amount.windowToken, 0)
            mochi.react()
            refresh()
            say.text = "${category}で${MoneyStore.yen(a)}、記録しました！\n" + store.line(prefs.masterName)
        }

        findViewById<EditText>(R.id.budget).apply {
            setText(store.monthlyBudget.toString())
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    s?.toString()?.toIntOrNull()?.takeIf { it > 0 }?.let { store.monthlyBudget = it; refreshSummary() }
                }
            })
        }

        refresh()
        say.text = store.line(prefs.masterName)
    }

    override fun onResume() {
        super.onResume()
        refresh() // 通知から自動で記録されたものを反映
    }

    private fun renderCategories() {
        val box = findViewById<FlowLayout>(R.id.categories)
        box.removeAllViews()
        val d = resources.displayMetrics.density
        for (c in MoneyStore.CATEGORIES) {
            box.addView(TextView(this).apply {
                text = c
                textSize = 14f
                setTextColor(getColor(R.color.ink))
                setBackgroundResource(if (c == category) R.drawable.chip_custom_bg else R.drawable.chip_bg)
                setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
                setOnClickListener { category = c; renderCategories() }
            })
        }
    }

    private fun refresh() {
        refreshSummary()
        renderEntries()
    }

    private fun refreshSummary() {
        val total = store.monthTotal()
        val budget = store.monthlyBudget
        val month = Calendar.getInstance().get(Calendar.MONTH) + 1
        findViewById<TextView>(R.id.monthLabel).text = "${month}月に使ったお金（今日 ${MoneyStore.yen(store.todayTotal())}）"
        findViewById<TextView>(R.id.total).text = "${MoneyStore.yen(total)} / ${MoneyStore.yen(budget)}"
        findViewById<ProgressBar>(R.id.progress).progress = if (budget > 0) (total.toLong() * 1000 / budget).toInt().coerceIn(0, 1000) else 0
        val left = budget - total
        findViewById<TextView>(R.id.left).text = if (left >= 0) "残り ${MoneyStore.yen(left)}" else "${MoneyStore.yen(-left)} オーバー"
        findViewById<TextView>(R.id.breakdown).text =
            store.byCategory().joinToString("\n") { (c, v) -> "・$c  ${MoneyStore.yen(v)}" }
    }

    private fun renderEntries() {
        val box = findViewById<LinearLayout>(R.id.entries)
        box.removeAllViews()
        val d = resources.displayMetrics.density
        val entries = store.monthEntries()
        if (entries.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "まだ何も記録されていません"
                setTextColor(getColor(R.color.ink_sub))
                textSize = 14f
                setPadding(0, (8 * d).toInt(), 0, 0)
            })
            return
        }
        val fmt = SimpleDateFormat("M/d H:mm", Locale.JAPAN)
        // 新しいものを上に
        entries.withIndex().reversed().forEach { (i, e) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (6 * d).toInt(), 0, (6 * d).toInt())
            }
            row.addView(TextView(this).apply {
                text = fmt.format(Date(e.time))
                setTextColor(getColor(R.color.ink_sub))
                textSize = 12f
                minWidth = (70 * d).toInt()
            })
            row.addView(TextView(this).apply {
                text = (if (e.autoFrom != null) "🔔 " else "") + e.category + (if (e.memo.isNotBlank()) "・${e.memo}" else "")
                setTextColor(getColor(R.color.ink))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = MoneyStore.yen(e.amount)
                setTextColor(getColor(R.color.ink))
                textSize = 15f
            })
            row.addView(TextView(this).apply {
                text = "✕"
                setTextColor(getColor(R.color.ink_sub))
                textSize = 16f
                setPadding((14 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
                contentDescription = "この記録を消す"
                setOnClickListener {
                    store.remove(i)
                    say.text = "${MoneyStore.yen(e.amount)}の記録を消しました。"
                    refresh()
                }
            })
            box.addView(row)
        }
    }
}
