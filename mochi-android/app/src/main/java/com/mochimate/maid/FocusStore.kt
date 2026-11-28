package com.mochimate.maid

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** 集中した記録の1つ */
data class FocusSession(val date: LocalDate, val minutes: Int, val label: String, val end: Long)

/** 集中タイマーの記録(最新500件)。スマホの中だけに保存する */
class FocusStore(context: Context) {
    private val sp = context.getSharedPreferences("mochi_focus", Context.MODE_PRIVATE)

    fun all(): List<FocusSession> {
        val arr = runCatching { JSONArray(sp.getString("log", "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            FocusSession(LocalDate.parse(o.getString("d")), o.getInt("m"), o.optString("l"), o.optLong("e"))
        }
    }

    fun add(s: FocusSession) {
        val arr = JSONArray()
        (all() + s).takeLast(500).forEach {
            arr.put(JSONObject().put("d", it.date.toString()).put("m", it.minutes).put("l", it.label).put("e", it.end))
        }
        sp.edit().putString("log", arr.toString()).apply()
    }

    fun minutesOn(date: LocalDate) = all().filter { it.date == date }.sumOf { it.minutes }

    /** 今日をふくむ7日分(古い順) */
    fun lastWeek(): List<Pair<LocalDate, Int>> = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }.map { it to minutesOn(it) }

    var lastLabel: String
        get() = sp.getString("label", "勉強")!!
        set(v) = sp.edit().putString("label", v).apply()

    var lastMinutes: Int
        get() = sp.getInt("minutes", 25)
        set(v) = sp.edit().putInt("minutes", v).apply()

    companion object {
        fun hm(min: Int) = if (min >= 60) "${min / 60}時間${if (min % 60 > 0) "${min % 60}分" else ""}" else "${min}分"
    }
}
