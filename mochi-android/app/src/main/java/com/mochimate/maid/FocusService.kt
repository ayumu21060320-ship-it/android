package com.mochimate.maid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.time.LocalDate
import kotlin.math.max

/**
 * 集中タイマー。通知にのこり時間を出しながら動き、時間になったらリリがほめてくれる。
 * アプリを閉じても止まらないよう、フォアグラウンドサービスで動く。
 */
class FocusService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val done = Runnable { finish(completed = true) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { finish(completed = false); return START_NOT_STICKY }
            else -> {
                val seconds = intent?.getIntExtra(EXTRA_SECONDS, 25 * 60) ?: (25 * 60)
                totalSeconds = seconds
                startedAt = System.currentTimeMillis()
                endAt = startedAt + seconds * 1000L
                running = true
                goForeground()
                handler.removeCallbacks(done)
                handler.postDelayed(done, endAt - System.currentTimeMillis())
                OverlayService.focusChanged()
            }
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RUN, "集中タイマー（動いている間）", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 40, Intent(this, FocusActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 41, Intent(this, FocusService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, CH_RUN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⏱️ 集中タイマー")
            .setContentText("リリが静かに見守っています")
            .setWhen(endAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "やめる", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(N_RUN, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(N_RUN, n)
        }
    }

    private fun finish(completed: Boolean) {
        handler.removeCallbacks(done)
        if (running) {
            val secs = if (completed) totalSeconds else ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
            // 記録は分単位(30秒以上なら切り上げ)
            val mins = (secs + 30) / 60
            if (mins >= 1) FocusStore(this).add(FocusSession(LocalDate.now(), mins, "", System.currentTimeMillis()))
            running = false
            OverlayService.focusChanged()
            praise(this, completed, secs)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(done)
        running = false
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SECONDS = "seconds"
        private const val ACTION_STOP = "com.mochimate.maid.FOCUS_STOP"
        private const val CH_RUN = "mochi_focus_run"
        private const val CH_DONE = "mochi_focus_done"
        private const val N_RUN = 20
        private const val N_DONE = 21

        @Volatile var running = false
            private set
        @Volatile var endAt = 0L
            private set
        @Volatile var startedAt = 0L
            private set
        @Volatile var totalSeconds = 0
            private set

        fun remainingMs() = if (running) max(0L, endAt - System.currentTimeMillis()) else 0L

        fun start(c: Context, seconds: Int) {
            c.startForegroundService(Intent(c, FocusService::class.java).putExtra(EXTRA_SECONDS, seconds))
        }

        /** 「1時間5分」「3分20秒」のような書き方 */
        fun hms(sec: Int): String {
            val h = sec / 3600
            val m = sec % 3600 / 60
            val s = sec % 60
            return buildString {
                if (h > 0) append("${h}時間")
                if (m > 0) append("${m}分")
                if (s > 0 && h == 0) append("${s}秒")
                if (isEmpty()) append("0秒")
            }
        }

        fun stop(c: Context) {
            c.startService(Intent(c, FocusService::class.java).setAction(ACTION_STOP))
        }

        /** 終わった時にリリがほめる(ホーム画面にいれば吹き出し、いなければ通知) */
        private fun praise(c: Context, completed: Boolean, secs: Int) {
            val name = Prefs(c).masterName
            val today = FocusStore(c).minutesOn(LocalDate.now())
            val line = if (completed) {
                "${name}、おつかれさまでした！${hms(secs)}がんばりましたね♪\n今日はぜんぶで${FocusStore.hm(today)}です。ちょっと休憩しましょ！"
            } else if (secs >= 60) {
                "途中まででも${hms(secs)}がんばりましたね。えらいです！"
            } else {
                "またいつでも始めてくださいね。わたし、見守ってますから！"
            }
            if (OverlayService.showCard(CardSpec(icon = null, appLabel = "", title = "", body = null, header = line, timeoutMs = 15_000))) return
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_DONE, "集中タイマー（終わった時）", NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(c, 42, Intent(c, FocusActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            runCatching {
                nm.notify(N_DONE, Notification.Builder(c, CH_DONE)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(if (completed) "⏱️ 集中おわり！" else "⏱️ 集中タイマー")
                    .setContentText(line)
                    .setStyle(Notification.BigTextStyle().bigText(line))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build())
            }
        }
    }
}
