package com.mochimate.maid

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

/** タブの画面づくりで使う小さな部品 */
open class TabUi(protected val a: Activity) {
    protected val d = a.resources.displayMetrics.density
    protected fun px(v: Int) = (v * d).toInt()
    protected val ink get() = a.getColor(R.color.ink)
    protected val sub get() = a.getColor(R.color.ink_sub)

    /** スクロールする縦並び(下は呼び鈴とかぶらないように余白) */
    protected fun page(): Pair<ScrollView, LinearLayout> {
        val col = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(2), px(16), px(104))
        }
        val sv = ScrollView(a).apply { addView(col); isFillViewport = true }
        return sv to col
    }

    protected fun card(parent: LinearLayout, onClick: (() -> Unit)? = null, bg: Int = R.drawable.card_bg, top: Int = 12): LinearLayout {
        val c = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(bg)
            setPadding(px(16), px(14), px(16), px(14))
            onClick?.let { f -> setOnClickListener { f() } }
        }
        parent.addView(c, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(top) })
        return c
    }

    /** カードの見出し(左にタイトル、右に「○○ ›」) */
    protected fun header(parent: LinearLayout, title: String, link: String? = null): LinearLayout {
        val r = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        r.addView(text(title, 14f, sub, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        link?.let { r.addView(text(it, 13f, a.getColor(R.color.accent), bold = true)) }
        parent.addView(r)
        return r
    }

    protected fun text(t: String, size: Float, color: Int = ink, bold: Boolean = false) = TextView(a).apply {
        text = t; textSize = size; setTextColor(color); if (bold) paint.isFakeBoldText = true
    }

    /** 「62.4 kg」のように、数字を大きく単位を小さく */
    protected fun big(value: String, unit: String, color: Int = ink) = TextView(a).apply {
        val s = android.text.SpannableString("$value $unit")
        s.setSpan(android.text.style.RelativeSizeSpan(0.5f), value.length, s.length, 0)
        text = s; textSize = 26f; setTextColor(color); paint.isFakeBoldText = true
    }

    protected fun bar(ratio: Double, color: String) = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000
        progress = (ratio * 1000).toInt().coerceIn(0, 1000)
        progressDrawable = a.getDrawable(R.drawable.progress_kcal)?.mutate()
        progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(color))
    }

    protected fun open(cls: Class<*>) = a.startActivity(Intent(a, cls))
}

// ======================== 🏠 ホーム ========================

class HomeTab(a: Activity, private val goSettings: () -> Unit) : TabUi(a) {
    val view: View
    private val col: LinearLayout
    private val prefs = Prefs(a)

    init {
        val (sv, c) = page()
        view = sv
        col = c
    }

    fun refresh() {
        col.removeAllViews()
        hero()
        grid()
        careChips()
    }

    private fun hero() {
        val c = card(col, bg = R.drawable.hero_bg, top = 4)
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val maid = MochiView(a)
        row.addView(maid, LinearLayout.LayoutParams(px(92), px(117)))
        val right = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val bubble = text(heroLine(), 14f).apply {
            setBackgroundResource(R.drawable.bubble_card_bg)
            setPadding(px(12), px(10), px(12), px(10))
            setLineSpacing(0f, 1.15f)
        }
        right.addView(bubble)
        val running = OverlayService.isRunning
        right.addView(text(if (running) "● ホーム画面にいます ｜ おやすみさせる" else "○ おやすみ中 ｜ ホーム画面に呼ぶ", 12f,
            Color.parseColor(if (running) "#2E8B57" else "#857A8C"), bold = true).apply {
            setBackgroundResource(if (running) R.drawable.pill_on_bg else R.drawable.pill_off_bg)
            setPadding(px(10), px(6), px(10), px(6))
            setOnClickListener { toggleOverlay() }
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(8) })
        row.addView(right, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(4) })
        c.addView(row)
        maid.setOnClickListener {
            maid.react()
            bubble.text = MochiLines.random(prefs.masterName)
        }
    }

    private fun heroLine(): String {
        val name = prefs.masterName
        val w = if (prefs.careWeather) WeatherRepository.cachedToday(prefs) else null
        val weather = w?.let { "\n今日は${it.label} ${it.temp.roundToInt()}℃です。" + if (it.isRainy || it.rainChance >= 50) "傘を持っていってくださいね☂️" else "" } ?: ""
        return MochiLines.greeting(name) + weather
    }

    private fun toggleOverlay() {
        if (OverlayService.isRunning) {
            OverlayService.stop(a)
            refresh()
        } else if (!Settings.canDrawOverlays(a)) {
            goSettings()
        } else {
            OverlayService.start(a)
            a.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun grid() {
        val cal = CalorieStore(a)
        val t = cal.total()
        val ws = WeightStore(a)
        val money = MoneyStore(a)
        val score = DietScore.of(cal, t)

        fun row(): LinearLayout {
            val r = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            col.addView(r, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
            return r
        }
        fun tile(r: LinearLayout, first: Boolean, onClick: () -> Unit): LinearLayout {
            val c = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.card_bg)
                setPadding(px(12), px(12), px(12), px(12))
                minimumHeight = px(150)
                setOnClickListener { onClick() }
            }
            r.addView(c, LinearLayout.LayoutParams(0, -1, 1f).apply { if (!first) marginStart = px(10) })
            return c
        }

        val r1 = row()
        tile(r1, true) { open(CalorieActivity::class.java) }.apply {
            header(this, "🍙 カロリー", "›")
            addView(RingView(a).apply { compact = true; set(t.kcal, cal.goal) },
                LinearLayout.LayoutParams(px(88), px(88)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = px(4) })
            val left = cal.goal - t.kcal
            addView(text(if (left >= 0) "あと $left kcal" else "${-left} kcal オーバー", 12f, sub).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(-1, -2))
        }
        tile(r1, false) { open(WeightActivity::class.java) }.apply {
            header(this, "⚖️ 体重", "›")
            val last = ws.latest()
            addView(if (last != null) big(last.second.toString(), "kg") else text("未記録", 18f, sub, bold = true))
            val diff = ws.diffFromPrevious()
            addView(text(when {
                diff == null -> "毎日はかろう"
                diff <= 0 -> "▼${"%.1f".format(-diff)}kg 前回より"
                else -> "▲${"%.1f".format(diff)}kg 前回より"
            }, 12f, if (diff != null && diff <= 0) Color.parseColor("#2EA36B") else sub, bold = diff != null))
            addView(SparkView(a).apply { set(ws.all().takeLast(7).map { it.second }) }, LinearLayout.LayoutParams(-1, px(40)).apply { topMargin = px(6) })
        }

        val r2 = row()
        tile(r2, true) { open(MoneyActivity::class.java) }.apply {
            header(this, "💰 今月のお金", "›")
            val total = money.monthTotal()
            addView(big("%,d".format(total), "円"))
            addView(text("予算 ${MoneyStore.yen(money.monthlyBudget)}", 12f, sub))
            addView(bar(total.toDouble() / money.monthlyBudget.coerceAtLeast(1), "#5B9BD5"), LinearLayout.LayoutParams(-1, px(8)).apply { topMargin = px(8) })
            val c = Calendar.getInstance()
            val daysLeft = c.getActualMaximum(Calendar.DAY_OF_MONTH) - c.get(Calendar.DAY_OF_MONTH) + 1
            val left = money.monthlyBudget - total
            addView(text(if (left >= 0) "1日 ${MoneyStore.yen(left / daysLeft)}までOK" else "予算オーバー中", 12f, sub).apply { setPadding(0, px(6), 0, 0) })
        }
        tile(r2, false) { open(CalorieActivity::class.java) }.apply {
            header(this, "📝 今日の点数", "›")
            addView(if (score != null) big(score.total.toString(), "点") else text("－", 22f, sub, bold = true))
            addView(text(score?.rank ?: "ごはんを記録すると点数が出ます", 12f, sub))
            cal.pfcAdvice()?.let { addView(text(it, 12f, sub).apply { setPadding(0, px(6), 0, 0) }) }
        }
    }

    private fun careChips() {
        val c = card(col)
        header(c, "🎀 メイドさんにおまかせ中")
        val flow = FlowLayout(a).apply { setPadding(0, px(8), 0, 0) }
        c.addView(flow)
        fun chip(label: String, on: Boolean, onClick: () -> Unit) {
            flow.addView(text(label, 13f, if (on) Color.WHITE else ink, bold = on).apply {
                setBackgroundResource(if (on) R.drawable.chip_on_bg else R.drawable.chip_bg)
                setPadding(px(12), px(7), px(12), px(7))
                setOnClickListener { onClick() }
            })
        }
        chip("☀️ 天気", prefs.careWeather) { prefs.careWeather = !prefs.careWeather; refresh() }
        chip("🍙 健康", prefs.careCalorie) { prefs.careCalorie = !prefs.careCalorie; refresh() }
        chip("💰 お金", prefs.careMoney) { prefs.careMoney = !prefs.careMoney; refresh() }
        chip("🏠 おかえり", prefs.homeEnabled) { goSettings() }
        chip("🔔 通知", prefs.announceApps.isNotEmpty() || prefs.paymentApps.isNotEmpty()) { open(IntegrationActivity::class.java) }
        chip("🔒 ロック画面", prefs.lockScreenMaid) { prefs.lockScreenMaid = !prefs.lockScreenMaid; refresh() }
        chip("🎈 ふわふわ", prefs.driftEnabled) { prefs.driftEnabled = !prefs.driftEnabled; refresh() }
        c.addView(text("タップでオン・オフ（🏠と🔔は設定が開きます）", 11f, sub).apply { setPadding(0, px(8), 0, 0) })
    }
}

// ======================== 🩺 健康 ========================

class HealthTab(a: Activity) : TabUi(a) {
    val view: View
    private val col: LinearLayout

    init {
        val (sv, c) = page()
        view = sv
        col = c
    }

    fun refresh() {
        col.removeAllViews()
        calorie()
        weight()
        score()
        condition()
        card(col, { open(BodySettingsActivity::class.java) }).apply {
            header(this, "⚙️ からだの設定", "›")
            addView(text("年齢・身長・ふだんの動き・目標（1日 ${CalorieStore(a).goal} kcal）", 13f, sub).apply { setPadding(0, px(4), 0, 0) })
        }
    }

    private fun calorie() {
        val cal = CalorieStore(a)
        val t = cal.total()
        val c = card(col, { open(CalorieActivity::class.java) }, top = 4)
        header(c, "🍙 今日のカロリー", "食事を記録 ›")
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(RingView(a).apply { set(t.kcal, cal.goal) }, LinearLayout.LayoutParams(px(120), px(120)))
        val pfc = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        fun line(label: String, v: Double, goal: Double, color: String) {
            val l = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, px(6), 0, 0) }
            l.addView(text(label, 12f), LinearLayout.LayoutParams(0, -2, 1f))
            l.addView(text("${v.roundToInt()}/${goal.roundToInt()}g", 12f, sub))
            pfc.addView(l)
            pfc.addView(bar(v / goal, color), LinearLayout.LayoutParams(-1, px(8)).apply { topMargin = px(3) })
        }
        line("たんぱく質", t.p, cal.goalP, "#E0566E")
        line("脂質", t.f, cal.goalF, "#F2B84B")
        line("炭水化物", t.c, cal.goalC, "#5B9BD5")
        row.addView(pfc, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(14) })
        c.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(6) })
    }

    private fun weight() {
        val ws = WeightStore(a)
        val c = card(col, { open(WeightActivity::class.java) })
        header(c, "⚖️ 体重", "グラフ ›")
        val last = ws.latest()
        val target = ws.target
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        val left = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        when {
            last == null -> left.addView(text("まだ記録がありません", 15f, sub, bold = true))
            target != null -> {
                left.addView(text("目標 ${target}kg まであと", 12f, sub))
                left.addView(big("%.1f".format(abs(last.second - target)), "kg"))
                left.addView(text("いま ${last.second}kg", 12f, sub))
            }
            else -> {
                left.addView(text("いまの体重", 12f, sub))
                left.addView(big(last.second.toString(), "kg"))
                left.addView(text("目標を決めると予測が出ます", 12f, sub))
            }
        }
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(SparkView(a).apply { set(ws.all().takeLast(14).map { it.second }) }, LinearLayout.LayoutParams(px(160), px(64)))
        c.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(4) })
        ws.prediction()?.let { p ->
            c.addView(text("このペースだと ${p.monthValue}月${p.dayOfMonth}日ごろ目標に届きそう", 12f, sub).apply {
                gravity = Gravity.END; setPadding(0, px(6), 0, 0)
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun score() {
        val cal = CalorieStore(a)
        val s = DietScore.of(cal)
        val c = card(col, { open(CalorieActivity::class.java) })
        val h = header(c, "📝 今日の点数")
        h.addView(if (s != null) big(s.total.toString(), "点", a.getColor(R.color.accent)) else text("－", 22f, sub, bold = true))
        c.addView(text(
            if (s == null) "ごはんを記録すると、カロリーと栄養バランスから点数をつけます"
            else "${s.rank}　カロリー ${s.kcal}/40・たんぱく質 ${s.p}/20・脂質 ${s.f}/20・炭水化物 ${s.c}/20",
            12f, sub,
        ).apply { setPadding(0, px(4), 0, 0) })
    }

    private fun condition() {
        val store = ConditionStore(a)
        val today = store.get()
        val yesterday = store.get(LocalDate.now().minusDays(1))
        val c = card(col, { ConditionStore.dialog(a) { refresh() } })
        header(c, "😊 きょうの調子", "記録 ›")
        fun row(label: String, y: Int?, t: Int?) {
            val r = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, px(8), 0, 0) }
            r.addView(text(label, 15f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
            fun face(f: String, l: String) = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                addView(text(f, 26f))
                addView(text(l, 10f, sub))
            }
            r.addView(face(ConditionStore.face(y), "昨日"))
            r.addView(text(" › ", 16f, sub))
            r.addView(face(ConditionStore.face(t), "今日"))
            c.addView(r)
        }
        row("体調", yesterday.body, today.body)
        row("気分", yesterday.mood, today.mood)
    }
}

// ======================== 💰 お金 ========================

class MoneyTab(a: Activity) : TabUi(a) {
    val view: View
    private val col: LinearLayout

    init {
        val (sv, c) = page()
        view = sv
        col = c
    }

    fun refresh() {
        col.removeAllViews()
        val m = MoneyStore(a)
        val total = m.monthTotal()
        val month = Calendar.getInstance().get(Calendar.MONTH) + 1
        val c = card(col, { open(MoneyActivity::class.java) }, top = 4)
        header(c, "💰 ${month}月に使ったお金", "家計簿 ›")
        c.addView(big("%,d".format(total), "円"))
        c.addView(text("予算 ${MoneyStore.yen(m.monthlyBudget)}　今日 ${MoneyStore.yen(m.todayTotal())}", 12f, sub))
        c.addView(bar(total.toDouble() / m.monthlyBudget.coerceAtLeast(1), "#5B9BD5"), LinearLayout.LayoutParams(-1, px(10)).apply { topMargin = px(8) })
        val left = m.monthlyBudget - total
        c.addView(text(if (left >= 0) "残り ${MoneyStore.yen(left)}" else "${MoneyStore.yen(-left)} オーバー", 13f, sub).apply { setPadding(0, px(6), 0, 0) })

        val cats = m.byCategory()
        if (cats.isNotEmpty()) {
            val b = card(col)
            header(b, "分類ごと")
            for ((name, v) in cats) {
                val r = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, px(6), 0, 0) }
                r.addView(text(name, 14f), LinearLayout.LayoutParams(px(70), -2))
                r.addView(bar(v.toDouble() / total.coerceAtLeast(1), "#E8738C"), LinearLayout.LayoutParams(0, px(8), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
                r.addView(text(MoneyStore.yen(v), 13f, sub).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(px(90), -2))
                b.addView(r)
            }
        }
        card(col, { open(IntegrationActivity::class.java) }).apply {
            header(this, "🔔 支払いの自動記録", "設定 ›")
            addView(text("PayPay などの支払い通知を、メイドさんが家計簿につけてくれます", 12f, sub).apply { setPadding(0, px(4), 0, 0) })
        }
    }
}
