package com.mochimate.maid

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * ゴミの日のお知らせを、前の日の夜と当日の朝に出す。
 * 時間ぴったりでなくてよい(電池にやさしい、少し遅れてもよいアラームを使う)。
 */
object GarbageAlarm {

    private const val CHANNEL = "mochi_garbage"
    private const val EXTRA_KIND = "kind" // "evening" か "morning"

    /** 次のお知らせを予約し直す(ルールや時間を変えた時・再起動のあとに呼ぶ) */
    fun schedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val store = GarbageStore(c)
        for ((kind, minutes) in listOf("evening" to store.eveningMinutes, "morning" to store.morningMinutes)) {
            val pi = pending(c, kind)
            am.cancel(pi)
            if (minutes < 0 || store.rules().isEmpty() || !Prefs(c).careGarbage) continue
            var at = LocalDateTime.now().withHour(minutes / 60).withMinute(minutes % 60).withSecond(0).withNano(0)
            if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
            val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            // 正確な時間のアラームは特別な許可がいるので、少しのずれは許す方法で予約する
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    private fun pending(c: Context, kind: String): PendingIntent = PendingIntent.getBroadcast(
        c, if (kind == "evening") 30 else 31,
        Intent(c, GarbageReceiver::class.java).putExtra(EXTRA_KIND, kind),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** 予約した時間になった */
    fun fire(c: Context, kind: String) {
        val store = GarbageStore(c)
        val prefs = Prefs(c)
        val name = prefs.masterName
        if (kind == "evening") {
            val rules = store.on(LocalDate.now().plusDays(1))
            if (rules.isNotEmpty()) deliver(c, "${name}、明日は${GarbageStore.names(rules)}の日ですよ！\n今のうちにまとめておきましょうか？", rules)
        } else {
            val rules = store.on(LocalDate.now())
            if (rules.isNotEmpty() && store.doneDate != LocalDate.now().toString()) {
                deliver(c, "おはようございます！今日は${GarbageStore.names(rules)}の日ですよ〜。出し忘れないでくださいね！", rules)
            }
        }
        schedule(c) // 次の日の分を予約
    }

    private fun deliver(c: Context, line: String, rules: List<GarbageRule>) {
        val shown = OverlayService.showCard(
            CardSpec(
                icon = null, appLabel = "", title = rules.joinToString("　") { "${it.emoji} ${it.name}" }, body = null,
                header = line,
                actions = listOf("出した！" to {
                    GarbageStore(c).doneDate = LocalDate.now().toString()
                    OverlayService.say("えらいです！ありがとうございます♪")
                }),
                timeoutMs = 20_000,
            ),
        )
        if (shown) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "ゴミの日", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            c, 32, Intent(c, GarbageActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching {
            nm.notify(
                12,
                Notification.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("🗑️ ゴミの日")
                    .setContentText(line)
                    .setStyle(Notification.BigTextStyle().bigText(line))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
}

class GarbageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GarbageAlarm.fire(context, intent.getStringExtra("kind") ?: "morning")
    }
}
