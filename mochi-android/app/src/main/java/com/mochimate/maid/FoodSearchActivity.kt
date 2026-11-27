package com.mochimate.maid

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** 食べたものを探して記録する画面(成分表から検索・最近食べたもの・マイ食品・手入力) */
class FoodSearchActivity : Activity() {

    private lateinit var store: CalorieStore
    private var meal = Meal.now()
    private lateinit var results: LinearLayout
    private lateinit var mealChips: FlowLayout
    private val d by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * d).toInt()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CalorieStore(this)
        intent.getStringExtra(EXTRA_MEAL)?.let { runCatching { meal = Meal.valueOf(it) } }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(16), px(20), px(20))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.bg))
            fitsSystemWindows = true
            addView(root)
        })

        root.addView(TextView(this).apply {
            text = "食べたものを追加"
            textSize = 20f; setTextColor(getColor(R.color.ink)); paint.isFakeBoldText = true
        })
        mealChips = FlowLayout(this).apply { setPadding(0, px(8), 0, px(4)) }
        root.addView(mealChips)
        renderMealChips()

        val search = EditText(this).apply {
            hint = "🔍 食べ物の名前（例: ごはん、鶏むね、バナナ）"
            textSize = 16f
            setTextColor(getColor(R.color.ink))
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = scheduleSearch(s?.toString().orEmpty())
            })
        }
        root.addView(search)

        root.addView(TextView(this).apply {
            text = "✏️ 見つからない時は手入力で追加"
            textSize = 14f
            setTextColor(getColor(R.color.accent))
            paint.isFakeBoldText = true
            setPadding(0, px(10), 0, px(6))
            setOnClickListener { manualDialog() }
        })

        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(results)
        root.addView(TextView(this).apply {
            text = FoodDb.SOURCE
            textSize = 11f; setTextColor(getColor(R.color.ink_sub)); setPadding(0, px(16), 0, 0)
        })

        // データの読み込みは裏で(初回だけ少し時間がかかる)
        io.execute { FoodDb.load(this); main.post { showSuggestions() } }
    }

    private fun renderMealChips() {
        mealChips.removeAllViews()
        for (m in Meal.values()) {
            mealChips.addView(TextView(this).apply {
                text = "${m.emoji} ${m.label}"
                textSize = 14f
                setTextColor(getColor(R.color.ink))
                setBackgroundResource(if (m == meal) R.drawable.chip_custom_bg else R.drawable.chip_bg)
                setPadding(px(12), px(6), px(12), px(6))
                setOnClickListener { meal = m; renderMealChips() }
            })
        }
    }

    private fun scheduleSearch(q: String) {
        pending?.let { main.removeCallbacks(it) }
        val r = Runnable {
            if (q.isBlank()) { showSuggestions(); return@Runnable }
            io.execute {
                val hits = FoodDb.search(this, q)
                main.post { showHits(q, hits) }
            }
        }
        pending = r
        main.postDelayed(r, 200)
    }

    /** 何も入力していない時: マイ食品・最近食べたもの・よく食べるもの */
    private fun showSuggestions() {
        results.removeAllViews()
        val mine = store.presets()
        if (mine.isNotEmpty()) {
            section("⭐ マイ食品（長押しで削除）")
            for (p in mine) {
                results.addView(row(p.name, "${p.kcal} kcal / 1回分", onLong = {
                    store.removeFavorite(p.name); showSuggestions()
                }) {
                    addEntry(FoodEntry(p.name, p.kcal, System.currentTimeMillis(), meal, 0.0, p.p, p.f, p.c))
                })
            }
        }
        val recent = store.recent()
        if (recent.isNotEmpty()) {
            section("🕘 最近食べたもの")
            for (e in recent) {
                results.addView(row(e.name, "${e.kcal} kcal" + if (e.grams > 0) "（${e.grams.roundToInt()}g）" else "") {
                    val food = e.foodId?.let { FoodDb.byId(this, it) }
                    if (food != null) amountDialog(food, null, e.grams, e.name)
                    else addEntry(e.copy(time = System.currentTimeMillis(), meal = meal))
                })
            }
        }
        section("🍙 よく食べるもの")
        for (a in FoodDb.ALIASES) {
            val f = FoodDb.byId(this, a.foodId) ?: continue
            val n = FoodDb.scaled(f, a.grams.toDouble())
            results.addView(row(a.words.first(), "${n.kcal} kcal / ${a.unit}") { amountDialog(f, a) })
        }
    }

    private fun showHits(q: String, hits: List<FoodHit>) {
        results.removeAllViews()
        if (hits.isEmpty()) {
            section("「$q」は見つかりませんでした。ひらがなや別の言い方で探すか、手入力で追加してね")
            return
        }
        for (h in hits) {
            val sub = h.alias?.let { a -> "${FoodDb.scaled(h.food, a.grams.toDouble()).kcal} kcal / ${a.unit}（${a.grams}g）" }
                ?: "${h.food.kcal.roundToInt()} kcal / 100g"
            results.addView(row(h.alias?.words?.first() ?: h.food.displayName, sub) { amountDialog(h.food, h.alias) })
        }
    }

    // ---------- 量を決める ----------

    private fun amountDialog(food: Food, alias: FoodAlias?, startGrams: Double? = null, label: String? = null) {
        val name = label ?: alias?.words?.first() ?: food.displayName
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(8), px(20), 0)
        }
        box.addView(TextView(this).apply {
            text = food.displayName; textSize = 12f; setTextColor(getColor(R.color.ink_sub))
        })
        val grams = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText((startGrams ?: alias?.grams?.toDouble() ?: 100.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() })
            textSize = 20f
            gravity = Gravity.END
        }
        val gramRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        gramRow.addView(grams, LinearLayout.LayoutParams(0, -2, 1f))
        gramRow.addView(TextView(this).apply { text = " g"; textSize = 18f; setTextColor(getColor(R.color.ink)) })
        box.addView(gramRow)

        val chips = FlowLayout(this).apply { setPadding(0, px(4), 0, px(4)) }
        val options = buildList {
            if (alias != null) {
                for (m in listOf(0.5, 1.0, 1.5, 2.0)) {
                    val label2 = if (m == 1.0) alias.unit else "${alias.unit}×${if (m % 1.0 == 0.0) m.toInt() else m}"
                    add(label2 to alias.grams * m)
                }
            }
            for (g in listOf(50, 100, 150, 200)) add("${g}g" to g.toDouble())
        }
        for ((l, g) in options) {
            chips.addView(TextView(this).apply {
                text = l; textSize = 13f; setTextColor(getColor(R.color.ink))
                setBackgroundResource(R.drawable.chip_bg)
                setPadding(px(10), px(5), px(10), px(5))
                setOnClickListener { grams.setText(if (g % 1.0 == 0.0) g.toInt().toString() else g.toString()) }
            })
        }
        box.addView(chips)

        val preview = TextView(this).apply { textSize = 15f; setTextColor(getColor(R.color.ink)); setPadding(0, px(6), 0, 0) }
        box.addView(preview)
        fun update() {
            val g = grams.text.toString().toDoubleOrNull() ?: 0.0
            val n = FoodDb.scaled(food, g)
            preview.text = "${n.kcal} kcal\nたんぱく質 ${"%.1f".format(n.p)}g・脂質 ${"%.1f".format(n.f)}g・炭水化物 ${"%.1f".format(n.c)}g"
        }
        grams.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = update()
        })
        update()

        AlertDialog.Builder(this)
            .setTitle(name)
            .setView(box)
            .setPositiveButton("${meal.label}に追加") { _, _ ->
                val g = grams.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                if (g <= 0) return@setPositiveButton
                val n = FoodDb.scaled(food, g)
                addEntry(FoodEntry(name, n.kcal, System.currentTimeMillis(), meal, g, n.p, n.f, n.c, food.id))
            }
            .setNegativeButton("やめる", null)
            .show()
    }

    // ---------- 手入力 ----------

    private fun manualDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(8), px(20), 0)
        }
        fun field(hint: String, number: Boolean): EditText = EditText(this).apply {
            this.hint = hint
            inputType = if (number) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT
            box.addView(this)
        }
        val name = field("料理名（例: ○○の幕の内弁当）", false)
        val kcal = field("カロリー（kcal）", true)
        box.addView(TextView(this).apply {
            text = "わかれば（パッケージの栄養成分表示を見てね）"
            textSize = 12f; setTextColor(getColor(R.color.ink_sub)); setPadding(0, px(6), 0, 0)
        })
        val p = field("たんぱく質（g）", true)
        val f = field("脂質（g）", true)
        val c = field("炭水化物（g）", true)
        val fav = CheckBox(this).apply { text = "⭐ マイ食品に登録する"; isChecked = true }
        box.addView(fav)

        AlertDialog.Builder(this)
            .setTitle("手入力で追加")
            .setView(box)
            .setPositiveButton("${meal.label}に追加") { _, _ ->
                val n = name.text.toString().trim()
                val k = kcal.text.toString().toDoubleOrNull()?.roundToInt()
                if (n.isEmpty() || k == null || k <= 0) return@setPositiveButton
                val pp = p.text.toString().toDoubleOrNull() ?: 0.0
                val ff = f.text.toString().toDoubleOrNull() ?: 0.0
                val cc = c.text.toString().toDoubleOrNull() ?: 0.0
                if (fav.isChecked) store.addFavorite(n, k, pp, ff, cc)
                addEntry(FoodEntry(n, k, System.currentTimeMillis(), meal, 0.0, pp, ff, cc))
            }
            .setNegativeButton("やめる", null)
            .show()
    }

    private fun addEntry(e: FoodEntry) {
        store.add(e)
        lastAdded = "${e.name}（${e.kcal}kcal）"
        finish()
    }

    // ---------- 部品 ----------

    private fun section(t: String) {
        results.addView(TextView(this).apply {
            text = t; textSize = 13f; setTextColor(getColor(R.color.ink_sub)); setPadding(0, px(14), 0, px(4))
        })
    }

    private fun row(title: String, sub: String, onLong: (() -> Unit)? = null, onClick: () -> Unit): View {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_bg)
            setPadding(px(14), px(10), px(14), px(10))
            setOnClickListener { onClick() }
            if (onLong != null) setOnLongClickListener { onLong(); true }
        }
        r.addView(TextView(this).apply { text = title; textSize = 15f; setTextColor(getColor(R.color.ink)) })
        r.addView(TextView(this).apply { text = sub; textSize = 12f; setTextColor(getColor(R.color.ink_sub)) })
        r.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(6) }
        return r
    }

    companion object {
        const val EXTRA_MEAL = "meal"

        @Volatile private var lastAdded: String? = null

        /** 記録画面に戻った時に「○○を記録しました」と言うため */
        fun consumeLastAdded(): String? = lastAdded.also { lastAdded = null }
    }
}
