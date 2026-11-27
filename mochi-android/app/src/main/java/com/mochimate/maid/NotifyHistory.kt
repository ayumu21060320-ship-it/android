package com.mochimate.maid

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** メイドさんが知らせた通知の履歴の1件 */
data class HistoryItem(
    val time: Long,
    val pkg: String,
    val label: String,
    /** "pay" 支払い / "alarm" アラーム / "notify" ふつうの通知 */
    val kind: String,
    /** 表示した見出し。メッセージ系のアプリは null(名前も中身も残さない) */
    val title: String?,
)

/**
 * 通知の履歴(最新100件)。メイドさんが元の通知を消した時に、あとから見返せるようにする。
 * スマホの中だけに保存する。
 */
class NotifyHistory(context: Context) {
    private val sp = context.getSharedPreferences("mochi_history", Context.MODE_PRIVATE)

    fun all(): List<HistoryItem> {
        val arr = runCatching { JSONArray(sp.getString("items", "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            HistoryItem(o.getLong("time"), o.getString("pkg"), o.getString("label"), o.getString("kind"),
                o.optString("title").ifEmpty { null })
        }
    }

    fun add(item: HistoryItem) {
        val list = (listOf(item) + all()).take(MAX)
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("time", it.time).put("pkg", it.pkg).put("label", it.label)
                .put("kind", it.kind).put("title", it.title ?: ""))
        }
        sp.edit().putString("items", arr.toString()).apply()
    }

    fun clear() = sp.edit().remove("items").apply()

    companion object {
        private const val MAX = 100
    }
}
