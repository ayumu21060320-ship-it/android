package com.mochimate.maid

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 家計簿の1件 */
data class MoneyEntry(
    val amount: Int,
    val category: String,
    val memo: String,
    val time: Long,
    /** 通知から自動で記録したなら、そのアプリの名前 */
    val autoFrom: String?,
)

/** 家計簿のデータ置き場。月ごとに記録して、スマホの中だけに保存する。 */
class MoneyStore(context: Context) {

    private val sp = context.getSharedPreferences("mochi_money", Context.MODE_PRIVATE)

    var monthlyBudget: Int
        get() = sp.getInt("budget", 50000)
        set(v) = sp.edit().putInt("budget", v).apply()

    private fun monthKey(time: Long = System.currentTimeMillis()) =
        SimpleDateFormat("yyyy-MM", Locale.US).format(Date(time))

    fun monthEntries(): List<MoneyEntry> {
        val arr = runCatching { JSONArray(sp.getString("m_" + monthKey(), "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            MoneyEntry(
                o.getInt("amount"), o.optString("cat", "その他"), o.optString("memo"),
                o.optLong("time"), o.optString("auto").ifEmpty { null },
            )
        }
    }

    private fun save(list: List<MoneyEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("amount", it.amount).put("cat", it.category).put("memo", it.memo)
                    .put("time", it.time).put("auto", it.autoFrom ?: ""),
            )
        }
        sp.edit().putString("m_" + monthKey(), arr.toString()).apply()
    }

    fun add(entry: MoneyEntry) = save(monthEntries() + entry)

    fun remove(index: Int) {
        val list = monthEntries().toMutableList()
        if (index in list.indices) { list.removeAt(index); save(list) }
    }

    /** 記録した時刻で1件を探して、分類を変える(通知カードから) */
    fun setCategory(time: Long, category: String) {
        save(monthEntries().map { if (it.time == time) it.copy(category = category) else it })
    }

    /** 記録した時刻で1件を消す(通知カードの「取り消し」) */
    fun removeByTime(time: Long) {
        save(monthEntries().filter { it.time != time })
    }

    fun monthTotal() = monthEntries().sumOf { it.amount }

    fun todayTotal(): Int {
        val c = Calendar.getInstance()
        val today = c.get(Calendar.DAY_OF_YEAR)
        return monthEntries().filter {
            c.timeInMillis = it.time; c.get(Calendar.DAY_OF_YEAR) == today
        }.sumOf { it.amount }
    }

    fun byCategory(): List<Pair<String, Int>> =
        monthEntries().groupBy { it.category }.map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }

    /** 同じ支払いの通知が2回来た時に、二重に記録しないためのチェック */
    fun isDuplicate(amount: Int, autoFrom: String, withinMs: Long = 3 * 60_000L): Boolean {
        val now = System.currentTimeMillis()
        return monthEntries().any { it.autoFrom == autoFrom && it.amount == amount && now - it.time < withinMs }
    }

    fun line(name: String): String {
        val total = monthTotal()
        val left = monthlyBudget - total
        val c = Calendar.getInstance()
        val daysLeft = c.getActualMaximum(Calendar.DAY_OF_MONTH) - c.get(Calendar.DAY_OF_MONTH) + 1
        return when {
            total == 0 -> "今月はまだお金を使っていませんね。予算は${yen(monthlyBudget)}です、${name}！"
            left < 0 -> "今月は${yen(total)}で、予算を${yen(-left)}オーバーしてます…しばらく節約しましょうね。"
            left < monthlyBudget / 10 -> "今月は${yen(total)}使いました。残り${yen(left)}なので、ちょっと気をつけましょう！"
            else -> "今月は${yen(total)}使いました。あと${yen(left)}、1日${yen(left / daysLeft)}くらいなら大丈夫ですよ。"
        }
    }

    companion object {
        val CATEGORIES = listOf("食費", "日用品", "交通", "趣味", "交際", "その他")

        fun yen(v: Int): String = NumberFormat.getNumberInstance(Locale.JAPAN).format(v) + "円"

        /** お店の名前から、だいたいの分類を当てる */
        fun guessCategory(store: String?): String {
            val s = store ?: return "その他"
            return when {
                Regex("セブン|ファミ|ローソン|ミニストップ|デイリー|マクド|モス|すき家|吉野家|松屋|スタバ|スターバックス|ドトール|タリーズ|ガスト|サイゼ|コメダ|ケンタ|KFC|丸亀|スーパー|イオン|西友|ライフ|まいばすけっと|業務|食").containsMatchIn(s) -> "食費"
                Regex("JR|駅|Suica|PASMO|ICOCA|メトロ|バス|タクシー|GO|ENEOS|出光|コスモ|ガソリン|交通").containsMatchIn(s) -> "交通"
                Regex("ドラッグ|マツキヨ|ウエルシア|スギ|ツルハ|サンドラッグ|ダイソー|セリア|キャンドゥ|ニトリ|無印|カインズ|コーナン|ホームセンター").containsMatchIn(s) -> "日用品"
                Regex("Steam|Nintendo|任天堂|PlayStation|Google Play|App Store|アニメイト|ゲーム|映画|TOHO|カラオケ|ブック|書店|タワレコ").containsMatchIn(s) -> "趣味"
                else -> "その他"
            }
        }
    }
}
