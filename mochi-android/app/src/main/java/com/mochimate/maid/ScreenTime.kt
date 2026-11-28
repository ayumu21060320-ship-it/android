package com.mochimate.maid

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** 今日どのアプリを何分使ったか、と使いすぎのお知らせ */
object ScreenTime {

    private const val CHANNEL = "mochi_screen_time"

    /** 「使用状況へのアクセス」が許可されているか */
    fun granted(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** 今日(0時から今まで)、アプリごとに前に出ていた時間(ミリ秒)。多い順 */
    fun today(c: Context): List<Pair<String, Long>> {
        if (!granted(c)) return emptyList()
        val usm = c.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(start, end)
        val opened = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> opened[e.packageName] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    opened.remove(e.packageName)?.let { s -> total[e.packageName] = (total[e.packageName] ?: 0L) + (e.timeStamp - s) }
                }
            }
        }
        // 今も開いているアプリ
        opened.forEach { (pkg, s) -> total[pkg] = (total[pkg] ?: 0L) + (end - s) }
        val home = homePackages(c)
        return total.filterKeys { it != c.packageName && it !in home }
            .filterValues { it >= 60_000L }
            .toList().sortedByDescending { it.second }
    }

    private fun homePackages(c: Context): Set<String> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        return c.packageManager.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.toSet()
    }

    fun label(c: Context, pkg: String): String = runCatching {
        @Suppress("DEPRECATION")
        c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun hm(ms: Long): String {
        val m = (ms / 60_000L).toInt()
        return if (m >= 60) "${m / 60}時間${if (m % 60 > 0) "${m % 60}分" else ""}" else "${m}分"
    }

    // ---------- 上限 ----------

    private fun sp(c: Context) = c.getSharedPreferences("mochi_screen", Context.MODE_PRIVATE)

    /** アプリごとの1日の上限(分)。0 なら上限なし */
    fun limits(c: Context): Map<String, Int> {
        val o = runCatching { JSONObject(sp(c).getString("limits", "{}")) }.getOrElse { JSONObject() }
        return o.keys().asSequence().associateWith { o.getInt(it) }.filterValues { it > 0 }
    }

    fun setLimit(c: Context, pkg: String, minutes: Int) {
        val o = runCatching { JSONObject(sp(c).getString("limits", "{}")) }.getOrElse { JSONObject() }
        if (minutes <= 0) o.remove(pkg) else o.put(pkg, minutes)
        sp(c).edit().putString("limits", o.toString()).apply()
    }

    /** スマホ全体の1日の上限(分)。0 なら上限なし */
    fun totalLimit(c: Context) = sp(c).getInt("total", 0)
    fun setTotalLimit(c: Context, minutes: Int) = sp(c).edit().putInt("total", minutes).apply()

    /** 今日すでに注意したもの(同じことを何度も言わない)。「あと15分」を押したら時刻をのばす */
    private fun warnedKey() = "warned_" + LocalDate.now()
    private fun warnedUntil(c: Context, key: String) = sp(c).getLong(warnedKey() + key, 0L)
    private fun setWarned(c: Context, key: String, until: Long) = sp(c).edit().putLong(warnedKey() + key, until).apply()

    /** 上限をこえていたらリリが注意する(定期的に呼ぶ) */
    fun check(c: Context) {
        if (!Prefs(c).careScreenTime || !granted(c)) return
        val usage = today(c)
        val now = System.currentTimeMillis()
        val name = Prefs(c).masterName
        val total = usage.sumOf { it.second }
        val tl = totalLimit(c)
        if (tl > 0 && total >= tl * 60_000L && now >= warnedUntil(c, "*")) {
            warn(c, "*", "${name}、今日はスマホを${hm(total)}も使ってますよ…目、つかれてませんか？ちょっと休憩しましょ？", null)
            return
        }
        val limits = limits(c)
        for ((pkg, ms) in usage) {
            val lim = limits[pkg] ?: continue
            if (ms >= lim * 60_000L && now >= warnedUntil(c, pkg)) {
                val app = label(c, pkg)
                warn(c, pkg, "${name}、今日は${app}を${hm(ms)}使ってますよ。決めた${hm(lim * 60_000L)}をこえちゃいました…そろそろやめません？", pkg)
                return
            }
        }
    }

    private fun warn(c: Context, key: String, line: String, pkg: String?) {
        // 一度言ったら、今日はもう言わない(「あと15分」なら15分後にもう一度)
        setWarned(c, key, Long.MAX_VALUE)
        val icon = pkg?.let { runCatching { c.packageManager.getApplicationIcon(it) }.getOrNull() }
        val shown = OverlayService.showCard(
            CardSpec(
                icon = icon, appLabel = pkg?.let { label(c, it) } ?: "スマホの使いすぎ", title = "", body = null, header = line,
                actions = listOf(
                    "わかった" to { OverlayService.say("えらいです！目を休めてくださいね♪") },
                    "あと15分だけ" to {
                        setWarned(c, key, System.currentTimeMillis() + 15 * 60_000L)
                        OverlayService.say("しょうがないですね…15分だけですよ？")
                    },
                ),
                timeoutMs = 20_000,
            ),
        )
        if (shown) return
        // 動画やゲームでメイドさんが隠れている時は、通知で知らせる
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "スマホの使いすぎ", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(c, 50, Intent(c, ScreenTimeActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching {
            nm.notify(13, Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("📱 使いすぎ注意")
                .setContentText(line)
                .setStyle(Notification.BigTextStyle().bigText(line))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build())
        }
    }
}
