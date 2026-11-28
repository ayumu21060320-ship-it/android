package com.mochimate.maid

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.roundToInt

/** ホーム画面のメイドさんの吹き出しに入れる、小さなグラフたち */
object BubbleVisuals {

    private fun px(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    private fun bar(c: Context, ratio: Double, color: String) =
        ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = (ratio * 1000).toInt().coerceIn(0, 1000)
            progressDrawable = c.getDrawable(R.drawable.progress_kcal)?.mutate()
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(color))
        }

    private fun label(c: Context, t: String, size: Float = 11f, color: Int = c.getColor(R.color.ink_sub)) =
        TextView(c).apply { text = t; textSize = size; setTextColor(color) }

    /** 今日のカロリー: リング + PFC のバー */
    fun calorie(c: Context): View {
        val cal = CalorieStore(c)
        val t = cal.total()
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(RingView(c).apply { compact = true; set(t.kcal, cal.goal) }, LinearLayout.LayoutParams(px(c, 74), px(c, 74)))
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        fun line(name: String, v: Double, goal: Double, color: String) {
            val l = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, px(c, 3), 0, 0) }
            l.addView(label(c, name), LinearLayout.LayoutParams(0, -2, 1f))
            l.addView(label(c, "${v.roundToInt()}/${goal.roundToInt()}g"))
            col.addView(l)
            col.addView(bar(c, v / goal, color), LinearLayout.LayoutParams(-1, px(c, 6)).apply { topMargin = px(c, 2) })
        }
        line("たんぱく質", t.p, cal.goalP, "#E0566E")
        line("脂質", t.f, cal.goalF, "#F2B84B")
        line("炭水化物", t.c, cal.goalC, "#5B9BD5")
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(c, 10) })
        return row
    }

    /** 体重: 最近2週間のグラフ */
    fun weight(c: Context): View {
        val ws = WeightStore(c)
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        col.addView(SparkView(c).apply { set(ws.all().takeLast(14).map { it.second }) }, LinearLayout.LayoutParams(-1, px(c, 56)))
        val pts = ws.all().takeLast(14)
        if (pts.size >= 2) {
            val r = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            r.addView(label(c, "${pts.first().first.monthValue}/${pts.first().first.dayOfMonth}"), LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(label(c, "${pts.last().first.monthValue}/${pts.last().first.dayOfMonth}"))
            col.addView(r)
        }
        return col
    }

    /** 今月のお金: 予算のバー */
    fun money(c: Context): View {
        val m = MoneyStore(c)
        val total = m.monthTotal()
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val r = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        r.addView(label(c, MoneyStore.yen(total), 15f, c.getColor(R.color.ink)), LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(label(c, "予算 ${MoneyStore.yen(m.monthlyBudget)}"))
        col.addView(r)
        col.addView(bar(c, total.toDouble() / m.monthlyBudget.coerceAtLeast(1), "#5B9BD5"), LinearLayout.LayoutParams(-1, px(c, 8)).apply { topMargin = px(c, 4) })
        return col
    }

    /** きょうの調子: 昨日と今日の顔 */
    fun condition(c: Context): View {
        val s = ConditionStore(c)
        val t = s.get()
        val y = s.get(java.time.LocalDate.now().minusDays(1))
        return label(c, "体調 ${ConditionStore.face(y.body)} › ${ConditionStore.face(t.body)}　　気分 ${ConditionStore.face(y.mood)} › ${ConditionStore.face(t.mood)}", 15f, c.getColor(R.color.ink))
    }
}
