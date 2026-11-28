package com.mochimate.maid

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** やること1つ */
data class Todo(val id: Long, val text: String, val date: LocalDate, val doneAt: Long? = null) {
    val done get() = doneAt != null
}

/**
 * やることリスト。終わらなかったものは次の日に自動で繰り越す。スマホの中だけに保存する。
 */
class TodoStore(context: Context) {
    private val sp = context.getSharedPreferences("mochi_todo", Context.MODE_PRIVATE)

    fun all(): List<Todo> {
        val arr = runCatching { JSONArray(sp.getString("list", "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Todo(o.getLong("id"), o.getString("t"), LocalDate.parse(o.getString("d")), o.optLong("done", 0L).takeIf { v -> v > 0 })
        }
    }

    private fun save(list: List<Todo>) {
        val arr = JSONArray()
        // 終わったものは30日分だけ残す
        val keep = LocalDate.now().minusDays(30)
        list.filter { !it.done || it.date >= keep }.forEach {
            arr.put(JSONObject().put("id", it.id).put("t", it.text).put("d", it.date.toString()).put("done", it.doneAt ?: 0L))
        }
        sp.edit().putString("list", arr.toString()).apply()
    }

    /** 今日のやること(昨日までの残りも今日に繰り越して入れる) */
    fun today(): List<Todo> {
        val now = LocalDate.now()
        val list = all()
        val carried = list.map { if (!it.done && it.date < now) it.copy(date = now) else it }
        if (carried != list) save(carried)
        return carried.filter { it.date == now }.sortedWith(compareBy({ it.done }, { it.id }))
    }

    fun add(text: String, date: LocalDate = LocalDate.now()) {
        save(all() + Todo(System.currentTimeMillis(), text.trim(), date))
    }

    fun toggle(id: Long) = save(all().map { if (it.id == id) it.copy(doneAt = if (it.done) null else System.currentTimeMillis()) else it })

    fun remove(id: Long) = save(all().filter { it.id != id })

    /** 夜のお知らせを出した日 */
    var lastNudge: String?
        get() = sp.getString("nudge", null)
        set(v) = sp.edit().putString("nudge", v).apply()
}
