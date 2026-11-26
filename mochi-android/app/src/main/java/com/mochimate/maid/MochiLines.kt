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

    /** 長押しでアプリを開く時のセリフ */
    fun openSettings(name: String) = "設定を開きますね、${name}！"

    /** まだ作っていないお世話を選んでいる時のセリフ */
    fun comingSoon(prefs: Prefs): String? {
        val list = buildList {
            if (prefs.careCalorie) add("カロリー")
            if (prefs.careMoney) add("家計簿")
        }
        if (list.isEmpty()) return null
        return "${list.joinToString("と")}のお世話、いまお勉強中です！もう少し待っててくださいね。"
    }

    private fun pick(vararg lines: String) = lines[Random.nextInt(lines.size)]
}
