package com.mochimate.maid

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 食事の区分 */
enum class Meal(val label: String, val emoji: String) {
    BREAKFAST("朝ごはん", "🌅"), LUNCH("昼ごはん", "☀️"), DINNER("夜ごはん", "🌙"), SNACK("間食", "🍪");

    companion object {
        /** 今の時間に合いそうな区分 */
        fun now(): Meal = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 4..10 -> BREAKFAST
            in 11..15 -> LUNCH
            in 17..23 -> DINNER
            else -> SNACK
        }
    }
}

/** 1品ぶんの記録 */
data class FoodEntry(
    val name: String,
    val kcal: Int,
    val time: Long,
    val meal: Meal = Meal.SNACK,
    val grams: Double = 0.0,
    val p: Double = 0.0,
    val f: Double = 0.0,
    val c: Double = 0.0,
    /** 成分表の食品番号(成分表から選んだ時だけ) */
    val foodId: String? = null,
)

/** 自分で登録した「マイ食品」(1回分の栄養) */
data class FoodPreset(val name: String, val kcal: Int, val custom: Boolean, val p: Double = 0.0, val f: Double = 0.0, val c: Double = 0.0)

/** 1日の合計 */
data class DayTotal(val kcal: Int, val p: Double, val f: Double, val c: Double)

class CalorieStore(context: Context) {

    private val sp = context.getSharedPreferences("mochi_calorie", Context.MODE_PRIVATE)

    // ---------- 体のデータ ----------

    var isMale: Boolean
        get() = sp.getBoolean("male", true)
        set(v) = sp.edit().putBoolean("male", v).apply()
    var age: Int
        get() = sp.getInt("age", 20)
        set(v) = sp.edit().putInt("age", v).apply()
    var heightCm: Float
        get() = sp.getFloat("height", 170f)
        set(v) = sp.edit().putFloat("height", v).apply()
    var weightKg: Float
        get() = sp.getFloat("weight", 60f)
        set(v) = sp.edit().putFloat("weight", v).apply()

    /** 0=あまり動かない 1=ふつう 2=よく動く */
    var activity: Int
        get() = sp.getInt("activity", 1)
        set(v) = sp.edit().putInt("activity", v).apply()

    /** -1=減らしたい 0=キープ 1=増やしたい */
    var plan: Int
        get() = sp.getInt("plan", 0)
        set(v) = sp.edit().putInt("plan", v).apply()

    /** 基礎代謝(Mifflin-St Jeor式) */
    val bmr: Double
        get() = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age + if (isMale) 5.0 else -161.0

    /** 1日の目標カロリー */
    val goal: Int
        get() {
            val factor = when (activity) { 0 -> 1.2; 2 -> 1.725; else -> 1.55 }
            val adjust = when (plan) { -1 -> -400; 1 -> 300; else -> 0 }
            return ((bmr * factor + adjust) / 10).roundToInt() * 10
        }

    /** 1日の目標 PFC(g)。たんぱく質20%・脂質25%・炭水化物55% のバランスで計算 */
    val goalP: Double get() = goal * 0.20 / 4
    val goalF: Double get() = goal * 0.25 / 9
    val goalC: Double get() = goal * 0.55 / 4

    // ---------- 毎日の記録(日付ごとに保存。グラフ用に残しておく) ----------

    private fun dayKey(date: Date = Date()) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

    init {
        // 前の版(今日の分だけ保存していた形式)からの引っ越し
        val oldDate = sp.getString("log_date", null)
        val old = sp.getString("log", null)
        if (oldDate != null && old != null && !sp.contains("day_$oldDate")) {
            sp.edit().putString("day_$oldDate", old).remove("log").remove("log_date").apply()
        }
    }

    fun entries(date: Date = Date()): List<FoodEntry> {
        val arr = runCatching { JSONArray(sp.getString("day_" + dayKey(date), "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            FoodEntry(
                name = o.getString("name"),
                kcal = o.getInt("kcal"),
                time = o.optLong("time"),
                meal = runCatching { Meal.valueOf(o.optString("meal")) }.getOrDefault(Meal.SNACK),
                grams = o.optDouble("g", 0.0),
                p = o.optDouble("p", 0.0),
                f = o.optDouble("f", 0.0),
                c = o.optDouble("c", 0.0),
                foodId = o.optString("id").ifEmpty { null },
            )
        }
    }

    fun todayEntries() = entries()

    private fun save(list: List<FoodEntry>, date: Date = Date()) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("name", it.name).put("kcal", it.kcal).put("time", it.time)
                    .put("meal", it.meal.name).put("g", it.grams).put("p", it.p).put("f", it.f).put("c", it.c)
                    .put("id", it.foodId ?: ""),
            )
        }
        sp.edit().putString("day_" + dayKey(date), arr.toString()).apply()
    }

    fun add(entry: FoodEntry) = save(todayEntries() + entry)

    /** 前の版との互換用(名前とカロリーだけ) */
    fun add(name: String, kcal: Int) = add(FoodEntry(name, kcal, System.currentTimeMillis(), Meal.now()))

    fun remove(index: Int) {
        val list = todayEntries().toMutableList()
        if (index in list.indices) { list.removeAt(index); save(list) }
    }

    fun removeByTime(time: Long) = save(todayEntries().filter { it.time != time })

    fun todayTotal(): Int = todayEntries().sumOf { it.kcal }

    fun total(date: Date = Date()): DayTotal {
        val e = entries(date)
        return DayTotal(e.sumOf { it.kcal }, e.sumOf { it.p }, e.sumOf { it.f }, e.sumOf { it.c })
    }

    /** 最近(7日以内)に食べたもの。新しい順、同じ名前は1つにまとめる */
    fun recent(limit: Int = 20): List<FoodEntry> {
        val cal = Calendar.getInstance()
        val all = ArrayList<FoodEntry>()
        repeat(7) {
            all += entries(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return all.sortedByDescending { it.time }.distinctBy { it.name }.take(limit)
    }

    // ---------- マイ食品 ----------

    fun presets(): List<FoodPreset> = customPresets()

    private fun customPresets(): List<FoodPreset> {
        val arr = runCatching { JSONArray(sp.getString("favorites", "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            FoodPreset(o.getString("name"), o.getInt("kcal"), true, o.optDouble("p", 0.0), o.optDouble("f", 0.0), o.optDouble("c", 0.0))
        }
    }

    private fun saveCustom(list: List<FoodPreset>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("kcal", it.kcal).put("p", it.p).put("f", it.f).put("c", it.c)) }
        sp.edit().putString("favorites", arr.toString()).apply()
    }

    fun addFavorite(name: String, kcal: Int, p: Double = 0.0, f: Double = 0.0, c: Double = 0.0) {
        saveCustom(listOf(FoodPreset(name, kcal, true, p, f, c)) + customPresets().filter { it.name != name })
    }

    fun removeFavorite(name: String) {
        saveCustom(customPresets().filter { it.name != name })
    }

    // ---------- もちもちのセリフ ----------

    fun line(name: String): String {
        val total = todayTotal()
        val left = goal - total
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            total == 0 && hour >= 10 ->
                "${name}、今日はまだごはんが記録されてませんよ？ちゃんと食べましたか？"
            total == 0 ->
                "今日の目標は${goal}kcalです。食べたら教えてくださいね、${name}！"
            left < -300 ->
                "今日は${total}kcalで、目標より${-left}kcal多めです…明日はちょっと控えめにしましょうね。"
            left < 0 ->
                "今日は${total}kcal、目標ぴったりくらいです！おやつはがまんですよ〜。"
            left <= 300 ->
                "今日は${total}kcal食べました。あと${left}kcalなので、軽めのものにしましょうね。"
            else ->
                "今日は${total}kcal食べました。あと${left}kcalですよ、${name}！"
        } + (pfcAdvice()?.let { "\n$it" } ?: "")
    }

    /** 栄養バランスのひと言(夕方以降、記録がある時だけ) */
    fun pfcAdvice(): String? {
        val t = total()
        if (t.kcal == 0 || Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 15) return null
        return when {
            t.p < goalP * 0.6 -> "たんぱく質が少なめです。卵や納豆、鶏むね肉なんてどうですか？"
            t.f > goalF * 1.2 -> "脂質がちょっと多めかも…揚げ物はほどほどにしましょうね。"
            t.c > goalC * 1.2 -> "炭水化物が多めです。次はおかず中心にしてみましょう！"
            else -> null
        }
    }

}
