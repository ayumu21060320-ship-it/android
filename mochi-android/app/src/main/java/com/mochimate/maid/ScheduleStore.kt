package com.mochimate.maid

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 予定1つ。
 * @param time 開始時刻(null なら終日)
 * @param remindBefore 何分前に知らせるか(-1 なら知らせない)
 */
data class Plan(
    val id: Long,
    val title: String,
    val date: LocalDate,
    val time: LocalTime?,
    val remindBefore: Int,
    val memo: String = "",
) {
    /** 知らせる時刻(終日の予定はその日の朝8時) */
    fun remindAt(): LocalDateTime? {
        if (remindBefore < 0) return null
        return if (time == null) date.atTime(8, 0) else date.atTime(time).minusMinutes(remindBefore.toLong())
    }

    fun timeText() = time?.let { "%d:%02d".format(it.hour, it.minute) } ?: "終日"
}

/** 予定(メイドアプリの中だけで管理する。スマホの外には出さない) */
class ScheduleStore(context: Context) {
    private val sp = context.getSharedPreferences("mochi_schedule", Context.MODE_PRIVATE)

    fun all(): List<Plan> {
        val arr = runCatching { JSONArray(sp.getString("plans", "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Plan(
                o.getLong("id"), o.getString("title"), LocalDate.parse(o.getString("date")),
                o.optString("time").ifEmpty { null }?.let { t -> LocalTime.parse(t) },
                o.optInt("remind", 30), o.optString("memo"),
            )
        }.sortedWith(compareBy({ it.date }, { it.time ?: LocalTime.MIN }))
    }

    private fun save(list: List<Plan>) {
        val keep = LocalDate.now().minusDays(60) // 2か月前より古い予定は消す
        val arr = JSONArray()
        list.filter { it.date >= keep }.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("title", it.title).put("date", it.date.toString())
                    .put("time", it.time?.toString() ?: "").put("remind", it.remindBefore).put("memo", it.memo),
            )
        }
        sp.edit().putString("plans", arr.toString()).apply()
    }

    fun put(p: Plan) = save(all().filter { it.id != p.id } + p)
    fun remove(id: Long) = save(all().filter { it.id != id })
    fun on(date: LocalDate) = all().filter { it.date == date }

    /** これから(今日の、まだ始まっていないものもふくむ) */
    fun upcoming(): List<Plan> {
        val now = LocalDateTime.now()
        return all().filter { it.date > now.toLocalDate() || (it.date == now.toLocalDate() && (it.time == null || it.time >= now.toLocalTime())) }
    }

    /** 知らせ済みの予定(id と知らせた時刻) */
    fun wasReminded(p: Plan) = sp.getString("reminded_${p.id}", null) == p.remindAt()?.toString()
    fun markReminded(p: Plan) = sp.edit().putString("reminded_${p.id}", p.remindAt()?.toString()).apply()
}
