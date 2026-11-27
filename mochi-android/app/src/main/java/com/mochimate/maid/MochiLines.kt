package com.mochimate.maid

import java.util.Calendar
import kotlin.random.Random

/** もちもちがタップされた時にしゃべるセリフ。 */
object MochiLines {

    fun greeting(name: String): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..9 -> pick(
                "おはようございます、${name}！今日も一日がんばりましょうね♪",
                "${name}、おはようございます！朝ごはんはちゃんと食べましたか？",
            )
            in 10..11 -> pick(
                "${name}、午前中おつかれさまです！",
                "もうすぐお昼ですね、${name}。",
            )
            in 12..13 -> pick(
                "お昼の時間ですよ、${name}！しっかり食べてくださいね。",
                "${name}、お昼ごはんは何にしますか？",
            )
            in 14..17 -> pick(
                "${name}、ちょっと休憩しませんか？",
                "午後もがんばってますね、${name}！えらいです♪",
            )
            in 18..21 -> pick(
                "おかえりなさいませ、${name}！今日もおつかれさまでした。",
                "${name}、晩ごはんの時間ですよ〜。",
            )
            else -> pick(
                "${name}、もう遅い時間ですよ…そろそろ寝ませんか？",
                "夜ふかしはほどほどにしてくださいね、${name}。",
            )
        }
    }

    fun random(name: String): String = pick(
        "${name}、お水飲んでますか？",
        "えへへ、呼びましたか？",
        "なにかお手伝いできることはありますか、${name}？",
        "${name}のこと、ちゃんと見守ってますからね！",
        "もちもち…はっ、寝てないです！",
        "肩の力、抜いていきましょ〜。",
    )

    /** おうちに帰ってきた時のセリフ。spentToday があれば今日使ったお金にもふれる */
    fun welcomeHome(name: String, spentToday: Int?): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val head = when (hour) {
            in 5..10 -> pick("おかえりなさいませ、${name}！朝からおでかけ、おつかれさまです。", "あれ、もう帰ってきたんですか？おかえりなさいませ、${name}！")
            in 11..16 -> pick("おかえりなさいませ、${name}！外は暑かったですか？寒かったですか？", "おかえりなさいませ！お昼はちゃんと食べましたか、${name}？")
            in 17..21 -> pick("おかえりなさいませ、${name}！今日も一日おつかれさまでした♪", "おかえりなさいませ！${name}が帰ってくるの、待ってましたよ〜！")
            else -> pick("おかえりなさいませ、${name}…こんな遅くまでおつかれさまです。ゆっくり休んでくださいね。", "${name}、おかえりなさいませ！夜道は大丈夫でしたか？")
        }
        val money = spentToday?.takeIf { it > 0 }?.let { "\n今日は${MoneyStore.yen(it)}使いましたね。" } ?: ""
        return head + money
    }

    /** おでかけする時のセリフ。天気がわかれば傘のことも言う */
    fun goodbye(name: String, w: Weather?): String {
        val head = pick("いってらっしゃいませ、${name}！", "${name}、いってらっしゃいませ！気をつけてくださいね。", "いってらっしゃいませ！お帰りをお待ちしてますね♪")
        val tail = when {
            w == null -> ""
            w.isRainy || w.rainChance >= 50 -> "\n今日は雨が降りそうなので、傘を忘れないでくださいね☂️"
            w.isSnowy -> "\n雪が降るかもしれません。足元に気をつけて！"
            w.max >= 30 -> "\n今日は暑いので、お水を持っていってくださいね。"
            w.min <= 5 -> "\n今日は冷えるので、あったかくしてくださいね。"
            else -> ""
        }
        return head + tail
    }

    /** 壁に強くぶつかった時のセリフ */
    fun ouch(name: String): String = pick(
        "きゃっ！",
        "わわっ、目がまわります〜！",
        "いたた…${name}、らんぼうはダメですよ！",
        "ぽよんっ！",
        "もう〜、${name}ったら！",
    )

    /** 長押しでアプリを開く時のセリフ */
    fun openSettings(name: String) = "設定を開きますね、${name}！"

    private fun pick(vararg lines: String) = lines[Random.nextInt(lines.size)]
}
