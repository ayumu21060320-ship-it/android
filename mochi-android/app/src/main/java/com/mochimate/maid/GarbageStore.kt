package com.mochimate.maid

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * ゴミ出しのルール1つ。
 * @param days 出せる曜日(DayOfWeek の 1=月 … 7=日)
 * @param weeks 第何週か(1〜5)。空なら毎週
 */
data class GarbageRule(val name: String, val emoji: String, val days: Set<Int>, val weeks: Set<Int>) {

    fun matches(date: LocalDate): Boolean {
        if (date.dayOfWeek.value !in days) return false
        if (weeks.isEmpty()) return true
        // その月の「第何〇曜日」か(1日〜7日が第1)
        val nth = (date.dayOfMonth - 1) / 7 + 1
        return nth in weeks
    }

    /** 「月・木」「第2・第4 水」のような説明 */
    fun describe(): String {
        val d = days.sorted().joinToString("・") { DAY_NAMES[it - 1] }
        return if (weeks.isEmpty()) "毎週 $d" else "第${weeks.sorted().joinToString("・第")} $d"
    }

    companion object {
        val DAY_NAMES = listOf("月", "火", "水", "木", "金", "土", "日")

        /** よくあるゴミの種類 */
        val PRESETS = listOf(
            "燃えるゴミ" to "🔥", "燃えないゴミ" to "🧱", "プラスチック" to "🧴", "ペットボトル" to "🍶",
            "びん・缶" to "🥫", "資源ゴミ" to "♻️", "古紙" to "📰", "粗大ゴミ" to "🛋️",
        )
    }
}

/** ゴミ出しのルールと、お知らせの時間 */
class GarbageStore(context: Context) {

    private val sp = context.getSharedPreferences("mochi_garbage", Context.MODE_PRIVATE)

    fun rules(): List<GarbageRule> {
        val arr = runCatching { JSONArray(sp.getString("rules", "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            GarbageRule(
                o.getString("name"), o.optString("emoji", "🗑️"),
                o.getJSONArray("days").let { a -> (0 until a.length()).map { i -> a.getInt(i) }.toSet() },
                o.optJSONArray("weeks")?.let { a -> (0 until a.length()).map { i -> a.getInt(i) }.toSet() } ?: emptySet(),
            )
        }
    }

    fun save(rules: List<GarbageRule>) {
        val arr = JSONArray()
        rules.forEach {
            arr.put(
                JSONObject().put("name", it.name).put("emoji", it.emoji)
                    .put("days", JSONArray(it.days.sorted())).put("weeks", JSONArray(it.weeks.sorted())),
            )
        }
        sp.edit().putString("rules", arr.toString()).apply()
    }

    fun on(date: LocalDate): List<GarbageRule> = rules().filter { it.matches(date) }

    /** 前の日の夜に知らせる時間(分。-1 で知らせない) 初期値 20:00 */
    var eveningMinutes: Int
        get() = sp.getInt("evening", 20 * 60)
        set(v) = sp.edit().putInt("evening", v).apply()

    /** 当日の朝に知らせる時間(分。-1 で知らせない) 初期値 7:00 */
    var morningMinutes: Int
        get() = sp.getInt("morning", 7 * 60)
        set(v) = sp.edit().putInt("morning", v).apply()

    /** 「出した！」を押した日(その日の朝のお知らせはもう出さない) */
    var doneDate: String?
        get() = sp.getString("done", null)
        set(v) = sp.edit().putString("done", v).apply()

    companion object {
        fun names(rules: List<GarbageRule>) = rules.joinToString("と") { it.name }
        fun hhmm(m: Int) = "%d:%02d".format(m / 60, m % 60)
        @Suppress("unused") val ALL_DAYS = DayOfWeek.values().map { it.value }
    }
}
