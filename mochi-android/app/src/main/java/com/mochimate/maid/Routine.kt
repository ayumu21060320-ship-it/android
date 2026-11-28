package com.mochimate.maid

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 1日の生活リズム(0時からの分。null はわからない) */
data class DayRhythm(val wake: Int?, val leave: Int?, val back: Int?, val sleep: Int?) {
    /** 睡眠時間(分) */
    val sleepMinutes: Int?
        get() = if (wake != null && sleep != null) ((wake - sleep) + 1440) % 1440 else null
}

/**
 * ふだんの生活リズムを、スマホの記録から読み取る。
 * - 起きる/寝る：画面をつけた・消した記録(使用状況へのアクセスが必要)の、夜のいちばん長い空白
 * - 出かける/帰る：おうちの Wi-Fi・位置情報の記録(このアプリが残したもの)
 * 記録はスマホの中だけで使う。手で直した値があれば、そちらを使う。
 */
object Routine {

    private fun sp(c: Context) = c.getSharedPreferences("mochi_routine", Context.MODE_PRIVATE)

    // ---------- おうちの出入りの記録 ----------

    fun logHome(c: Context, entered: Boolean) {
        val arr = runCatching { JSONArray(sp(c).getString("home_log", "[]")) }.getOrElse { JSONArray() }
        val keepFrom = System.currentTimeMillis() - 30L * 86_400_000L
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.getLong("t") >= keepFrom) out.put(o)
        }
        out.put(JSONObject().put("t", System.currentTimeMillis()).put("in", entered))
        sp(c).edit().putString("home_log", out.toString()).apply()
    }

    private fun homeLog(c: Context): List<Pair<LocalDateTime, Boolean>> {
        val arr = runCatching { JSONArray(sp(c).getString("home_log", "[]")) }.getOrElse { JSONArray() }
        val z = ZoneId.systemDefault()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            LocalDateTime.ofInstant(Instant.ofEpochMilli(o.getLong("t")), z) to o.getBoolean("in")
        }
    }

    // ---------- 読み取り ----------

    /** 過去 days 日ぶんの、1日ごとのリズム(新しい順) */
    fun history(c: Context, days: Int = 14): Map<LocalDate, DayRhythm> {
        val nights = sleepNights(c, days)
        val log = homeLog(c)
        val result = LinkedHashMap<LocalDate, DayRhythm>()
        for (i in 0 until days) {
            val date = LocalDate.now().minusDays(i.toLong())
            val wake = nights[date]?.second
            val sleep = nights[date.plusDays(1)]?.first
            val dayLog = log.filter { it.first.toLocalDate() == date }
            val leave = dayLog.firstOrNull { !it.second }?.first?.let { it.hour * 60 + it.minute }
            val back = dayLog.lastOrNull { it.second }?.first?.let { it.hour * 60 + it.minute }?.takeIf { b -> leave == null || b > leave }
            if (wake != null || sleep != null || leave != null || back != null) {
                result[date] = DayRhythm(wake, leave, back, sleep)
            }
        }
        return result
    }

    /**
     * 夜ごとに「寝た時刻(前日の夜)」と「起きた時刻」を出す。キーは起きた日。
     * 前の日の18時〜その日の14時の間で、画面が消えていたいちばん長い時間(3時間以上)を睡眠とみなす。
     */
    private fun sleepNights(c: Context, days: Int): Map<LocalDate, Pair<Int, Int>> {
        if (!ScreenTime.granted(c)) return emptyMap()
        val usm = c.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val z = ZoneId.systemDefault()
        val start = LocalDate.now().minusDays(days.toLong() + 1).atStartOfDay(z).toInstant().toEpochMilli()
        val end = System.currentTimeMillis()
        val ev = usm.queryEvents(start, end)
        // 画面が消えていた区間を集める
        val offs = ArrayList<Pair<Long, Long>>()
        var offSince: Long? = null
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> if (offSince == null) offSince = e.timeStamp
                UsageEvents.Event.SCREEN_INTERACTIVE -> offSince?.let { offs += it to e.timeStamp; offSince = null }
            }
        }
        val out = HashMap<LocalDate, Pair<Int, Int>>()
        for (i in 0..days) {
            val day = LocalDate.now().minusDays(i.toLong())
            val winStart = day.minusDays(1).atTime(18, 0).atZone(z).toInstant().toEpochMilli()
            val winEnd = day.atTime(14, 0).atZone(z).toInstant().toEpochMilli()
            val best = offs.filter { it.first in winStart..winEnd }
                .map { it.first to minOf(it.second, winEnd) }
                .maxByOrNull { it.second - it.first } ?: continue
            if (best.second - best.first < 3 * 3_600_000L) continue
            val s = LocalDateTime.ofInstant(Instant.ofEpochMilli(best.first), z)
            val w = LocalDateTime.ofInstant(Instant.ofEpochMilli(best.second), z)
            out[day] = (s.hour * 60 + s.minute) to (w.hour * 60 + w.minute)
        }
        return out
    }

    /** 平日・休日それぞれの「ふだん」(真ん中の値)。手で直した値があればそれを優先 */
    fun usual(c: Context, weekend: Boolean): DayRhythm {
        val days = history(c).filter { (d, _) -> isWeekend(d) == weekend }.values
        fun med(xs: List<Int>, nightShift: Boolean = false): Int? {
            if (xs.isEmpty()) return null
            // 寝る時刻は0時をまたぐので、正午を起点にして並べる
            val adj = xs.map { if (nightShift && it < 720) it + 1440 else it }.sorted()
            return adj[adj.size / 2] % 1440
        }
        val auto = DayRhythm(
            med(days.mapNotNull { it.wake }),
            med(days.mapNotNull { it.leave }),
            med(days.mapNotNull { it.back }),
            med(days.mapNotNull { it.sleep }, nightShift = true),
        )
        val p = if (weekend) "we_" else "wd_"
        fun manual(k: String) = sp(c).getInt(p + k, -1).takeIf { it >= 0 }
        fun seed(k: String) = sp(c).getInt("seed_$k", -1).takeIf { it >= 0 }
        return DayRhythm(
            manual("wake") ?: auto.wake ?: seed("wake"),
            manual("leave") ?: auto.leave,
            manual("back") ?: auto.back,
            manual("sleep") ?: auto.sleep ?: seed("sleep"),
        )
    }

    /** はじめの会話で聞いた「だいたいの時間」。記録がたまるまでの仮の値 */
    fun setSeed(c: Context, key: String, minutes: Int) = sp(c).edit().putInt("seed_$key", minutes).apply()

    /** 手で直す(-1 で自動に戻す) */
    fun setManual(c: Context, weekend: Boolean, key: String, minutes: Int) {
        sp(c).edit().putInt((if (weekend) "we_" else "wd_") + key, minutes).apply()
    }

    fun isWeekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

    fun today(c: Context) = usual(c, isWeekend(LocalDate.now()))

    fun hhmm(m: Int?) = m?.let { "%d:%02d".format(it / 60, it % 60) } ?: "−"

    // ---------- リリが使う ----------

    /** 今日、朝のあいさつをしたか */
    fun greetedToday(c: Context) = sp(c).getString("greeted", null) == LocalDate.now().toString()
    fun markGreeted(c: Context) = sp(c).edit().putString("greeted", LocalDate.now().toString()).apply()

    /** 今日、寝る時間の声かけをしたか */
    fun bedNudgedToday(c: Context) = sp(c).getString("bed", null) == LocalDate.now().toString()
    fun markBedNudged(c: Context) = sp(c).edit().putString("bed", LocalDate.now().toString()).apply()
}
