package com.mochimate.maid

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.abs
import kotlin.random.Random

/**
 * ホーム画面の上にもちもちを浮かべるサービス。
 * 通知を出しながら動く「フォアグラウンドサービス」なので、アプリを閉じても消されにくい。
 *
 * もちもち本体と吹き出しは別々の小さなウィンドウにしている。
 * 吹き出しはタッチを素通りさせるので、下にあるアプリのアイコンの邪魔をしない。
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private var mochi: MochiView? = null
    private lateinit var mochiParams: WindowManager.LayoutParams
    private var bubble: BubbleView? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var bubbleShown = false
    private var talkCount = 0

    private val hideBubble = Runnable { setBubbleVisible(false) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (mochi == null) showMochi()
        isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        bubble?.let { if (bubbleShown) runCatching { wm.removeView(it) } }
        mochi?.let { runCatching { wm.removeView(it) } }
        bubble = null
        mochi = null
        super.onDestroy()
    }

    // ---------- 通知 ----------

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "もちもち常駐中", NotificationManager.IMPORTANCE_MIN).apply {
                description = "もちもちがホーム画面にいる間に表示されます"
                setShowBadge(false)
            },
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("もちもちがおそばにいます")
            .setContentText("タップで設定を開きます")
            .setContentIntent(openApp)
            .addAction(Notification.Action.Builder(null, "おやすみさせる", stop).build())
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ---------- もちもち本体 ----------

    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }

    private fun baseParams(touchable: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    @SuppressLint("ClickableViewAccessibility")
    private fun showMochi() {
        val view = MochiView(this)
        val (sw, sh) = screenSize()
        val size = (96 * resources.displayMetrics.density).toInt()

        mochiParams = baseParams(touchable = true).apply {
            x = if (prefs.overlayX >= 0) prefs.overlayX else sw - size - (16 * resources.displayMetrics.density).toInt()
            y = if (prefs.overlayY >= 0) prefs.overlayY else (sh * 0.55f).toInt()
            x = x.coerceIn(0, (sw - size).coerceAtLeast(0))
            y = y.coerceIn(0, (sh - size).coerceAtLeast(0))
        }
        view.setOnTouchListener(DragTouchListener())
        wm.addView(view, mochiParams)
        mochi = view

        bubble = BubbleView(this)
        bubbleParams = baseParams(touchable = false)

        // 登場のあいさつ
        handler.postDelayed({ talk(MochiLines.greeting(prefs.masterName)) }, 400)
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private val slop = ViewConfiguration.get(this@OverlayService).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longPressed = false
        private val longPress = Runnable {
            longPressed = true
            openSettings()
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = mochiParams.x; startY = mochiParams.y
                    dragging = false; longPressed = false
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong() + 200)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        val (sw, sh) = screenSize()
                        mochiParams.x = (startX + dx).toInt().coerceIn(0, (sw - v.width).coerceAtLeast(0))
                        mochiParams.y = (startY + dy).toInt().coerceIn(0, (sh - v.height).coerceAtLeast(0))
                        wm.updateViewLayout(v, mochiParams)
                        if (bubbleShown) positionBubble()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    if (dragging) {
                        prefs.overlayX = mochiParams.x
                        prefs.overlayY = mochiParams.y
                    } else if (!longPressed) {
                        onTapped()
                    }
                }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
            }
            return true
        }
    }

    private fun onTapped() {
        mochi?.react()
        val name = prefs.masterName
        talkCount++
        val line = when {
            talkCount % 4 == 1 -> MochiLines.greeting(name)
            Random.nextInt(4) == 0 -> MochiLines.comingSoon(prefs) ?: MochiLines.random(name)
            else -> MochiLines.random(name)
        }
        talk(line)
    }

    private fun openSettings() {
        mochi?.react()
        talk(MochiLines.openSettings(prefs.masterName))
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    // ---------- 吹き出し ----------

    private fun talk(text: String) {
        val b = bubble ?: return
        b.text = text
        setBubbleVisible(true)
        handler.removeCallbacks(hideBubble)
        handler.postDelayed(hideBubble, 6000)
    }

    private fun setBubbleVisible(visible: Boolean) {
        val b = bubble ?: return
        if (visible) {
            positionBubble()
            if (bubbleShown) wm.updateViewLayout(b, bubbleParams)
            else { wm.addView(b, bubbleParams); bubbleShown = true }
        } else if (bubbleShown) {
            runCatching { wm.removeView(b) }
            bubbleShown = false
        }
    }

    /** もちもちの頭の上(入りきらなければ足元)に吹き出しを置く */
    private fun positionBubble() {
        val b = bubble ?: return
        val m = mochi ?: return
        val (sw, sh) = screenSize()
        val mochiW = m.width.takeIf { it > 0 } ?: m.measuredWidth
        val mochiH = m.height.takeIf { it > 0 } ?: m.measuredHeight

        b.tailOnTop = false
        b.measure(
            View.MeasureSpec.makeMeasureSpec(sw, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val bw = b.measuredWidth
        val bh = b.measuredHeight

        var y = mochiParams.y - bh + (mochiH * 0.18f).toInt()
        if (y < 0) {
            b.tailOnTop = true
            y = mochiParams.y + mochiH - (mochiH * 0.05f).toInt()
        }
        bubbleParams.x = (mochiParams.x + mochiW / 2 - bw / 2).coerceIn(0, (sw - bw).coerceAtLeast(0))
        bubbleParams.y = y.coerceIn(0, (sh - bh).coerceAtLeast(0))
        b.tailX = (mochiParams.x + mochiW / 2 - bubbleParams.x).toFloat()
        if (bubbleShown) wm.updateViewLayout(b, bubbleParams)
    }

    companion object {
        private const val CHANNEL_ID = "mochi_overlay"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.mochimate.maid.STOP"

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
