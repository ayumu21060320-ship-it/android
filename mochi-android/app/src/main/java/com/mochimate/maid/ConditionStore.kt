package com.mochimate.maid

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.time.LocalDate

/** きょうの調子(体調・気分を5段階で)。スマホの中だけに保存する */
class ConditionStore(context: Context) {

    private val sp = context.getSharedPreferences("mochi_condition", Context.MODE_PRIVATE)

    data class Day(val body: Int?, val mood: Int?)

    fun get(date: LocalDate = LocalDate.now()): Day {
        val o = runCatching { JSONObject(sp.getString(date.toString(), "{}")) }.getOrElse { JSONObject() }
        return Day(o.optInt("body", 0).takeIf { it in 1..5 }, o.optInt("mood", 0).takeIf { it in 1..5 })
    }

    fun set(body: Int?, mood: Int?, date: LocalDate = LocalDate.now()) {
        val o = JSONObject()
        body?.let { o.put("body", it) }
        mood?.let { o.put("mood", it) }
        sp.edit().putString(date.toString(), o.toString()).apply()
    }

    companion object {
        /** 1(つらい)〜5(元気) の顔 */
        val FACES = listOf("😫", "😟", "😐", "🙂", "😄")
        fun face(v: Int?) = v?.let { FACES[it - 1] } ?: "・"

        /** メイドさんのひと言 */
        fun reply(name: String, body: Int?, mood: Int?): String {
            val low = listOfNotNull(body, mood).minOrNull() ?: return "教えてくれてありがとうございます！"
            return when {
                low <= 1 -> "${name}、だいぶつらそうですね…無理しないで、今日はゆっくり休みましょう。わたしがついてますからね。"
                low == 2 -> "ちょっとお疲れ気味ですね。今日は早めに寝ましょう、${name}。"
                low == 3 -> "ふつうの日、ですね。なにかいいことがあるといいですね♪"
                else -> "元気そうでよかったです、${name}！今日もいい一日になりそうですね♪"
            }
        }

        /** 体調と気分を選ぶダイアログ */
        fun dialog(a: Activity, done: (String) -> Unit) {
            val store = ConditionStore(a)
            val now = store.get()
            var body = now.body
            var mood = now.mood
            val dp = a.resources.displayMetrics.density
            val box = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), 0)
            }
            fun row(label: String, initial: Int?, onPick: (Int) -> Unit) {
                box.addView(TextView(a).apply {
                    text = label; textSize = 15f; setTextColor(a.getColor(R.color.ink)); setPadding(0, (8 * dp).toInt(), 0, (4 * dp).toInt())
                })
                val faces = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
                val views = ArrayList<TextView>()
                FACES.forEachIndexed { i, f ->
                    val tv = TextView(a).apply {
                        text = f; textSize = 30f; gravity = Gravity.CENTER
                        alpha = if (initial == i + 1) 1f else 0.35f
                        setOnClickListener {
                            onPick(i + 1)
                            views.forEachIndexed { j, v -> v.alpha = if (j == i) 1f else 0.35f }
                        }
                    }
                    views += tv
                    faces.addView(tv, LinearLayout.LayoutParams(0, (52 * dp).toInt(), 1f))
                }
                box.addView(faces)
            }
            row("体調はどうですか？", body) { body = it }
            row("気分はどうですか？", mood) { mood = it }
            AlertDialog.Builder(a)
                .setTitle("きょうの調子")
                .setView(box)
                .setPositiveButton("記録する") { _, _ ->
                    store.set(body, mood)
                    done(reply(Prefs(a).masterName, body, mood))
                }
                .setNegativeButton("やめる", null)
                .show()
        }
    }
}
