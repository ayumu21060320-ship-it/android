package com.mochimate.maid

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** リリとの会話の1つ */
data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val time: Long,
    /** -1 = 👎(いまいち) 0 = なし */
    val feedback: Int = 0,
)

/**
 * リリとの会話の記録と「ご主人様ノート」(リリが覚えていること)。
 * どちらもスマホの中だけに保存する。
 */
class ChatStore(context: Context) {

    private val sp = context.getSharedPreferences("mochi_chat", Context.MODE_PRIVATE)

    fun messages(): List<ChatMessage> {
        val arr = runCatching { JSONArray(sp.getString("log", "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            ChatMessage(o.getBoolean("u"), o.getString("t"), o.getLong("at"), o.optInt("fb", 0))
        }
    }

    private fun save(list: List<ChatMessage>) {
        val arr = JSONArray()
        list.takeLast(MAX).forEach {
            arr.put(JSONObject().put("u", it.fromUser).put("t", it.text).put("at", it.time).put("fb", it.feedback))
        }
        sp.edit().putString("log", arr.toString()).apply()
    }

    fun add(m: ChatMessage) {
        save(messages() + m)
        if (m.fromUser) {
            val today = LocalDate.now().toString()
            val count = if (sp.getString("count_day", null) == today) sp.getInt("count", 0) else 0
            sp.edit().putString("count_day", today).putInt("count", count + 1)
                .putInt("since_note", sp.getInt("since_note", 0) + 1).apply()
            if (sp.getString("first_day", null) == null) sp.edit().putString("first_day", today).apply()
            val days = sp.getStringSet("talk_days", emptySet())!!.toMutableSet().apply { add(today) }
            sp.edit().putStringSet("talk_days", days).apply()
        }
    }

    fun setFeedback(time: Long, value: Int) = save(messages().map { if (it.time == time) it.copy(feedback = value) else it })

    fun clear() = sp.edit().remove("log").apply()

    /** 今日ご主人様が話しかけた回数(使いすぎ防止) */
    fun todayCount(): Int = if (sp.getString("count_day", null) == LocalDate.now().toString()) sp.getInt("count", 0) else 0

    /** これまでに話した日数(仲良し度のもと) */
    fun talkDays(): Int = sp.getStringSet("talk_days", emptySet())!!.size

    // ---------- ご主人様ノート ----------

    var notebook: String
        get() = sp.getString("notebook", "")!!
        set(v) = sp.edit().putString("notebook", v.trim()).apply()

    /** ノートを書き直してから、ご主人様が話しかけた回数 */
    var sinceNote: Int
        get() = sp.getInt("since_note", 0)
        set(v) = sp.edit().putInt("since_note", v).apply()

    companion object {
        private const val MAX = 300
        const val DAILY_LIMIT = 300
    }
}
