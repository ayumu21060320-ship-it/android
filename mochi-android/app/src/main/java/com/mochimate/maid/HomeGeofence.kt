package com.mochimate.maid

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/**
 * 「おうち」に着いた・出た、を見張る。
 * おうちの場所はスマホの中だけに保存して、どこにも送らない。
 */
object HomeGeofence {

    private const val ID = "home"
    private const val CHANNEL = "mochi_home"
    private const val COOLDOWN_MS = 15 * 60 * 1000L
    private const val SAME_STATE_MS = 3 * 60 * 60 * 1000L

    fun hasForeground(c: Context) =
        c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackground(c: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun pendingIntent(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 0, Intent(c, HomeGeofenceReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
    )

    /** 今いる場所を調べる(おうちの登録用) */
    @SuppressLint("MissingPermission")
    fun locate(c: Context, onResult: (Double, Double, Float) -> Unit, onError: (String) -> Unit) {
        if (!hasForeground(c)) return onError("位置情報が許可されていません")
        LocationServices.getFusedLocationProviderClient(c)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                if (loc == null) onError("場所がわかりませんでした。位置情報をオンにして、外が見える所で試してね")
                else onResult(loc.latitude, loc.longitude, loc.accuracy)
            }
            .addOnFailureListener { onError("場所を調べられませんでした") }
    }

    /** おうちの見張りを(もう一度)始める。再起動やアプリの更新のあとにも呼ぶ。 */
    @SuppressLint("MissingPermission")
    fun register(c: Context, onDone: ((Boolean) -> Unit)? = null) {
        val p = Prefs(c)
        val lat = p.homeLat
        val lon = p.homeLon
        if (!p.homeEnabled || lat == null || lon == null || !hasForeground(c) || !hasBackground(c)) {
            onDone?.invoke(false)
            return
        }
        val fence = Geofence.Builder()
            .setRequestId(ID)
            .setCircularRegion(lat, lon, p.homeRadius.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setNotificationResponsiveness(60_000)
            .build()
        // 登録した瞬間に「おかえり」と言わないよう、最初の判定はしない
        val req = GeofencingRequest.Builder().setInitialTrigger(0).addGeofence(fence).build()
        val client = LocationServices.getGeofencingClient(c)
        client.removeGeofences(listOf(ID)).addOnCompleteListener {
            client.addGeofences(req, pendingIntent(c))
                .addOnSuccessListener { onDone?.invoke(true) }
                .addOnFailureListener { onDone?.invoke(false) }
        }
    }

    fun unregister(c: Context) {
        LocationServices.getGeofencingClient(c).removeGeofences(listOf(ID))
    }

    /** おうちに着いた/出た時の処理(テストボタンからも呼ぶ) */
    /**
     * @param source "wifi"(家の Wi-Fi)か "gps"(位置情報)。Wi-Fi のほうが速くて確実なので、
     *   Wi-Fi で判定したあとに遅れて届いた位置情報の判定は無視する。
     */
    fun onTransition(c: Context, entered: Boolean, test: Boolean = false, source: String = "gps") {
        val p = Prefs(c)
        val now = System.currentTimeMillis()
        if (!test) {
            // 同じ「家にいる/いない」が続けて来たら言わない(Wi-Fi のあとの GPS や、GPS のぶれ)
            if (entered == p.lastHomeEntered && now - p.lastHomeEventAt < SAME_STATE_MS) return
            // GPS は短い間の行ったり来たりを無視する。Wi-Fi はすぐ反応してよい
            if (source == "gps" && now - p.lastHomeEventAt < COOLDOWN_MS && entered != p.lastHomeEntered &&
                p.lastHomeSource == "gps"
            ) return
            p.lastHomeEntered = entered
            p.lastHomeEventAt = now
            p.lastHomeSource = source
        }
        if (!entered && !p.homeSayGoodbye) return

        if (entered) {
            val line = MochiLines.welcomeHome(p.masterName, MoneyStore(c).todayTotal().takeIf { p.careMoney })
            deliver(c, line, "🏠 おかえりなさいませ")
        } else {
            // いってらっしゃい: 天気がわかれば傘のことも言う。
            // 外に出るとネットが使えないことがあるので、家にいる間に取っておいた今日の天気を使う
            val cached = WeatherRepository.cachedToday(p)
            if (cached != null) {
                deliver(c, MochiLines.goodbye(p.masterName, cached), "🚪 いってらっしゃいませ")
            } else {
                WeatherRepository.today(p) { w ->
                    deliver(c, MochiLines.goodbye(p.masterName, w), "🚪 いってらっしゃいませ")
                }
            }
        }
    }

    private fun deliver(c: Context, line: String, title: String) {
        if (OverlayService.isRunning) {
            OverlayService.say(line)
            return
        }
        // メイドさんがおやすみ中なら、通知で伝える
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "おかえり・いってらっしゃい", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            c, 4, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(line)
            .setStyle(Notification.BigTextStyle().bigText(line))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(11, n) }
    }
}

/** ジオフェンスの知らせを受け取る */
class HomeGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> HomeGeofence.onTransition(context, entered = true)
            Geofence.GEOFENCE_TRANSITION_EXIT -> HomeGeofence.onTransition(context, entered = false)
        }
    }
}

/** スマホの再起動やアプリの更新のあと、おうちの見張りをやり直す(再起動すると消えてしまうため) */
class HomeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                HomeGeofence.register(context)
                GarbageAlarm.schedule(context)
                ScheduleAlarm.schedule(context)
            }
        }
    }
}
