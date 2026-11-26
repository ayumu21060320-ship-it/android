package com.mochimate.maid

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 1品ぶんの記録 */
data class FoodEntry(val name: String, val kcal: Int, val time: Long)

/** よく食べるもの(ワンタップで記録できる) */
data class FoodPreset(val name: String, val kcal: Int, val custom: Boolean)

/**
 * カロリー管理のデータ置き場。
 * 体のデータ・今日の記録・よく食べるものを、すべてスマホの中だけに保存する。
 */
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

    // ---------- 今日の記録(日付が変わったら自動でリセット) ----------

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun todayEntries(): List<FoodEntry> {
        if (sp.getString("log_date", null) != today()) return emptyList()
        val arr = runCatching { JSONArray(sp.getString("log", "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            FoodEntry(o.getString("name"), o.getInt("kcal"), o.optLong("time"))
        }
    }

    private fun saveToday(list: List<FoodEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("kcal", it.kcal).put("time", it.time)) }
        sp.edit().putString("log_date", today()).putString("log", arr.toString()).apply()
    }

    fun add(name: String, kcal: Int) {
        saveToday(todayEntries() + FoodEntry(name, kcal, System.currentTimeMillis()))
    }

    fun remove(index: Int) {
        val list = todayEntries().toMutableList()
        if (index in list.indices) { list.removeAt(index); saveToday(list) }
    }

    fun todayTotal(): Int = todayEntries().sumOf { it.kcal }

    // ---------- よく食べるもの ----------

    fun presets(): List<FoodPreset> = customPresets() + BUILT_IN.map { FoodPreset(it.first, it.second, false) }

    private fun customPresets(): List<FoodPreset> {
        val arr = runCatching { JSONArray(sp.getString("favorites", "[]")) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            FoodPreset(o.getString("name"), o.getInt("kcal"), true)
        }
    }

    private fun saveCustom(list: List<FoodPreset>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("kcal", it.kcal)) }
        sp.edit().putString("favorites", arr.toString()).apply()
    }

    fun addFavorite(name: String, kcal: Int) {
        saveCustom(listOf(FoodPreset(name, kcal, true)) + customPresets().filter { it.name != name })
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
        }
    }

    companion object {
        /** よくあるメニューのだいたいのカロリー(目安) */
        val BUILT_IN = listOf(
            "ごはん(茶碗1杯)" to 250,
            "おにぎり" to 180,
            "食パン1枚" to 160,
            "味噌汁" to 40,
            "サラダ" to 80,
            "納豆" to 90,
            "ゆで卵" to 80,
            "ヨーグルト" to 60,
            "バナナ" to 90,
            "サンドイッチ" to 300,
            "コンビニ弁当" to 700,
            "牛丼(並)" to 650,
            "カレーライス" to 750,
            "ラーメン" to 550,
            "うどん" to 350,
            "パスタ" to 600,
            "唐揚げ定食" to 900,
            "ハンバーガー" to 400,
            "フライドポテト(M)" to 400,
            "カップ麺" to 350,
            "ポテトチップス(1袋)" to 330,
            "チョコ(1枚)" to 280,
            "アイス" to 200,
            "缶コーヒー(加糖)" to 70,
            "コーラ(500ml)" to 225,
            "ビール(350ml)" to 140,
        )
    }
}
