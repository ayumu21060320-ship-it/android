package com.mochimate.maid

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.abs

/** 体重の記録(1日1つ)と目標。スマホの中だけに保存する。 */
class WeightStore(context: Context) {

    private val sp = context.getSharedPreferences("mochi_weight", Context.MODE_PRIVATE)
    private val calorie = CalorieStore(context)

    /** 日付の古い順 */
    fun all(): List<Pair<LocalDate, Double>> {
        val o = runCatching { JSONObject(sp.getString("log", "{}")) }.getOrElse { JSONObject() }
        return o.keys().asSequence().mapNotNull { k ->
            runCatching { LocalDate.parse(k) to o.getDouble(k) }.getOrNull()
        }.sortedBy { it.first }.toList()
    }

    fun set(date: LocalDate, kg: Double) {
        val o = runCatching { JSONObject(sp.getString("log", "{}")) }.getOrElse { JSONObject() }
        o.put(date.toString(), kg)
        sp.edit().putString("log", o.toString()).apply()
        if (sp.getString("start", null) == null) sp.edit().putString("start", date.toString()).apply()
        // いちばん新しい体重を、目標カロリーの計算にも使う
        all().lastOrNull()?.let { calorie.weightKg = it.second.toFloat() }
    }

    fun remove(date: LocalDate) {
        val o = runCatching { JSONObject(sp.getString("log", "{}")) }.getOrElse { JSONObject() }
        o.remove(date.toString())
        sp.edit().putString("log", o.toString()).apply()
    }

    fun latest(): Pair<LocalDate, Double>? = all().lastOrNull()

    /** 1つ前の記録との差(kg) */
    fun diffFromPrevious(): Double? {
        val a = all()
        if (a.size < 2) return null
        return a[a.size - 1].second - a[a.size - 2].second
    }

    var target: Double?
        get() = sp.getString("target", null)?.toDoubleOrNull()
        set(v) {
            sp.edit().putString("target", v?.toString()).apply()
            // 目標に合わせて「減らしたい/増やしたい」も合わせる
            val now = latest()?.second
            if (v != null && now != null) calorie.plan = when {
                v < now - 0.5 -> -1
                v > now + 0.5 -> 1
                else -> 0
            }
        }

    /** 記録を始めた日の体重 */
    fun startWeight(): Pair<LocalDate, Double>? = all().firstOrNull()

    /**
     * 最近2週間のペースから、目標に届きそうな日を予想する。
     * 目標と逆に進んでいたり、記録が少なかったりしたら null。
     */
    fun prediction(): LocalDate? {
        val t = target ?: return null
        val pts = all()
        val last = pts.lastOrNull() ?: return null
        val recent = pts.filter { it.first >= last.first.minusDays(14) }
        if (recent.size < 3) return null
        val slope = slopePerDay(recent)
        val remain = t - last.second
        if (abs(remain) < 0.05) return last.first
        if (slope == 0.0 || remain / slope <= 0) return null
        val days = remain / slope
        if (days > 730) return null
        return last.first.plusDays(days.toLong())
    }

    /** 1日あたりの増減(最小二乗法の傾き) */
    fun slopePerDay(points: List<Pair<LocalDate, Double>> = all().takeLast(14)): Double {
        if (points.size < 2) return 0.0
        val x0 = points.first().first.toEpochDay()
        val xs = points.map { (it.first.toEpochDay() - x0).toDouble() }
        val ys = points.map { it.second }
        val mx = xs.average()
        val my = ys.average()
        val num = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        val den = xs.indices.sumOf { (xs[it] - mx) * (xs[it] - mx) }
        return if (den == 0.0) 0.0 else num / den
    }

    /** メイドさんのひと言 */
    fun line(name: String): String {
        val last = latest() ?: return "${name}、体重を記録してみませんか？毎日はかると、変化がわかりやすいですよ。"
        val diff = diffFromPrevious()
        val t = target
        val head = when {
            diff == null -> "体重を記録しました、${name}！これから一緒にがんばりましょうね。"
            diff <= -0.1 -> "前回より${"%.1f".format(-diff)}kg減ってます！いい調子ですよ♪"
            diff >= 0.1 -> "前回より${"%.1f".format(diff)}kg増えてます…でも1日の増減は水分のせいも多いので、気にしすぎないでくださいね。"
            else -> "前回とほとんど同じです。キープできててえらいです！"
        }
        val tail = if (t != null) {
            val remain = last.second - t
            val pred = prediction()
            when {
                abs(remain) < 0.1 -> "\n目標の${t}kgに届きました！おめでとうございます🎉"
                pred != null -> "\n目標まであと${"%.1f".format(abs(remain))}kg。このペースなら${pred.monthValue}月${pred.dayOfMonth}日ごろ届きそうです！"
                else -> "\n目標まであと${"%.1f".format(abs(remain))}kgです。"
            }
        } else ""
        return head + tail
    }
}
