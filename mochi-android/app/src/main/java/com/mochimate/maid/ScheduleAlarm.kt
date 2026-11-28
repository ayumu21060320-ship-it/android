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

/** 予定の前にリリが知らせる。次に知らせる予定の時刻にアラームを1つだけ予約しておく */
object ScheduleAlarm {

    private const val CHANNEL = "mochi_schedule"

    fun schedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(c, 60, Intent(c, ScheduleReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(pi)
        val store = ScheduleStore(c)
        val now = LocalDateTime.now()
        val next = store.all().mapNotNull { p -> p.remindAt()?.takeIf { it.isAfter(now.minusMinutes(1)) && !store.wasReminded(p) } }.minOrNull() ?: return
        val ms = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms.coerceAtLeast(System.currentTimeMillis() + 1_000), pi)
    }

    /** 知らせる時刻をすぎた予定を、まとめて知らせる */
    fun fire(c: Context) {
        val store = ScheduleStore(c)
        val now = LocalDateTime.now()
        val due = store.all().filter { p ->
            val at = p.remindAt() ?: return@filter false
            !store.wasReminded(p) && !at.isAfter(now.plusMinutes(1)) && at.isAfter(now.minusHours(3))
        }
        for (p in due) {
            store.markReminded(p)
            deliver(c, p)
        }
        schedule(c)
    }

    private fun deliver(c: Context, p: Plan) {
        val name = Prefs(c).masterName
        val line = when {
            p.time == null -> "${name}、今日は「${p.title}」の日ですよ！"
            p.date == LocalDate.now() && p.remindBefore >= 60 -> "${name}、${p.timeText()}から「${p.title}」ですよ。忘れないでくださいね！"
            else -> "${name}、${p.timeText()}から「${p.title}」ですよ！そろそろ準備しましょう"
        }
        val shown = OverlayService.showCard(
            CardSpec(icon = null, appLabel = "", title = "📅 ${p.timeText()}　${p.title}", body = p.memo.ifBlank { null }, header = line, timeoutMs = 30_000),
        )
        if (shown) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "予定のお知らせ", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(c, 61, Intent(c, ScheduleActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching {
            nm.notify((p.id % 100000).toInt() + 1000, Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("📅 ${p.timeText()} ${p.title}")
                .setContentText(line)
                .setStyle(Notification.BigTextStyle().bigText(line))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build())
        }
    }
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = ScheduleAlarm.fire(context)
}
