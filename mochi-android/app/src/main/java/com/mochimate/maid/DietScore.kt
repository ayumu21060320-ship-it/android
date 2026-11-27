package com.mochimate.maid

import kotlin.math.max
import kotlin.math.roundToInt

/** 1日の食事の点数(100点満点)。カロリー40点 + たんぱく質・脂質・炭水化物 各20点 */
data class DietScore(val total: Int, val kcal: Int, val p: Int, val f: Int, val c: Int) {

    val rank: String
        get() = when {
            total >= 85 -> "すばらしい！"
            total >= 70 -> "いい感じ！"
            total >= 50 -> "まずまず"
            else -> "明日がんばろう"
        }

    companion object {
        /** 何も記録がない日は null */
        fun of(store: CalorieStore, t: DayTotal = store.total()): DietScore? =
            compute(t, store.goal, store.goalP, store.goalF, store.goalC)

        /** 点数の計算そのもの(テストしやすいように分けてある) */
        fun compute(t: DayTotal, goal: Int, goalP: Double, goalF: Double, goalC: Double): DietScore? {
            if (t.kcal == 0 || goal <= 0) return null
            val r = t.kcal.toDouble() / goal
            val kcalDev = when { r < 0.9 -> 0.9 - r; r > 1.05 -> r - 1.05; else -> 0.0 }
            val k = (40 * max(0.0, 1 - kcalDev / 0.4)).roundToInt()
            fun part(v: Double, goal: Double): Int {
                if (goal <= 0) return 20
                val q = v / goal
                val dev = when { q < 0.8 -> 0.8 - q; q > 1.2 -> q - 1.2; else -> 0.0 }
                return (20 * max(0.0, 1 - dev / 0.6)).roundToInt()
            }
            val p = part(t.p, goalP)
            val f = part(t.f, goalF)
            val c = part(t.c, goalC)
            return DietScore(k + p + f + c, k, p, f, c)
        }

        /** 夜のふり返りのセリフ */
        fun review(store: CalorieStore, name: String): String {
            val t = store.total()
            val s = of(store, t) ?: return "${name}、今日はごはんの記録がありませんでした…明日はぜひ教えてくださいね。"
            val r = t.kcal.toDouble() / store.goal
            val weakest = listOf(
                "kcal" to s.kcal / 40.0, "p" to s.p / 20.0, "f" to s.f / 20.0, "c" to s.c / 20.0,
            ).minBy { it.second }
            val advice = when {
                weakest.second >= 0.9 -> "バランスもばっちりでした。この調子でいきましょう♪"
                weakest.first == "kcal" && r > 1 -> "カロリーが少しオーバーでした。明日は腹八分目を意識してみましょう。"
                weakest.first == "kcal" -> "カロリーが少なめでした。食べなさすぎも体に良くないですよ。"
                weakest.first == "p" && t.p < store.goalP -> "たんぱく質が足りませんでした。明日は卵や納豆、お肉やお魚を足してみましょう！"
                weakest.first == "p" -> "たんぱく質がちょっと多めでした。"
                weakest.first == "f" && t.f > store.goalF -> "脂質が多めでした。揚げ物を1品減らすだけでも変わりますよ。"
                weakest.first == "f" -> "脂質がかなり少なめでした。少しは油もとって大丈夫ですよ。"
                weakest.first == "c" && t.c > store.goalC -> "炭水化物が多めでした。ごはんやパンを少し控えめにしてみましょう。"
                else -> "炭水化物が少なめでした。エネルギー不足に気をつけてくださいね。"
            }
            return "${name}、今日のごはんのふり返りです！\n${t.kcal}kcalで、点数は${s.total}点。${s.rank}\n$advice"
        }
    }
}
