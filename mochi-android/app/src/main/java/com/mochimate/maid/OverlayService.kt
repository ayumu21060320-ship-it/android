package com.mochimate.maid

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.BatteryManager
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
import android.view.Choreographer
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
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
    private var card: NotifyCard? = null

    // ボタンやチップ付きの吹き出し(支払い・アラーム・通知)
    private var rich: BubbleFrame? = null
    private var richSpec: CardSpec? = null
    private val richParams by lazy { baseParams(touchable = true) }
    private val richDismiss = Runnable { dismissRich() }
    private val alarmNudge = object : Runnable {
        override fun run() {
            if (rich == null) return
            mochi?.react()
            handler.postDelayed(this, 2500)
        }
    }

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
        MochiNotificationListener.ensureConnected(this)
        isRunning = true
        instance = this
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        if (instance === this) instance = null
        runCatching { unregisterReceiver(batteryReceiver) }
        runCatching { unregisterReceiver(screenReceiver) }
        card?.dismiss()
        dismissRich(resume = false)
        stopFling(save = true)
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
        val food = PendingIntent.getActivity(
            this, 2, Intent(this, CalorieActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("もちもちがおそばにいます")
            .setContentText("タップで設定を開きます")
            .setContentIntent(openApp)
            .apply {
                if (prefs.careCalorie) addAction(Notification.Action.Builder(null, "ごはんを記録", food).build())
            }
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
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val size = view.measuredWidth
        val sizeH = view.measuredHeight

        mochiParams = baseParams(touchable = true).apply {
            x = if (prefs.overlayX != Prefs.UNSET) prefs.overlayX else sw - size - (16 * resources.displayMetrics.density).toInt()
            y = if (prefs.overlayY != Prefs.UNSET) prefs.overlayY else (sh * 0.55f).toInt()
            x = x.coerceIn(-(size * MochiView.CONTENT_LEFT).toInt(), sw - (size * MochiView.CONTENT_RIGHT).toInt())
            y = y.coerceIn(-(sizeH * MochiView.CONTENT_TOP).toInt(), sh - (sizeH * MochiView.CONTENT_BOTTOM).toInt())
        }
        view.setOnTouchListener(DragTouchListener())
        wm.addView(view, mochiParams)
        mochi = view

        bubble = BubbleView(this)
        bubbleParams = baseParams(touchable = false)

        if (prefs.driftEnabled) handler.postDelayed({ if (mochi != null) startFling(0f, 0f) }, 1500)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        registerReceiver(batteryReceiver, filter)
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))

        // 登場のあいさつ。天気のお世話を選んでいれば、続けて今日の天気も伝える。
        handler.postDelayed({ talk(MochiLines.greeting(prefs.masterName)) }, 400)
        if (prefs.careWeather) {
            talkCount = 2 // あいさつと天気は済んだので、次のタップはその続きから
            handler.postDelayed({ if (mochi != null) talkWeather() }, 4500)
        }
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private val slop = ViewConfiguration.get(this@OverlayService).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longPressed = false
        private var tracker: VelocityTracker? = null
        private val longPress = Runnable {
            longPressed = true
            openSettings()
        }

        /** ウィンドウ自体が動くので、画面全体の座標(raw)で速さを測る */
        private fun track(e: MotionEvent) {
            val copy = MotionEvent.obtain(e)
            copy.setLocation(e.rawX, e.rawY)
            tracker?.addMovement(copy)
            copy.recycle()
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    stopFling(save = false) // 飛んでいる途中ならキャッチ
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain()
                    track(e)
                    downX = e.rawX; downY = e.rawY
                    startX = mochiParams.x; startY = mochiParams.y
                    dragging = false; longPressed = false
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong() + 200)
                }
                MotionEvent.ACTION_MOVE -> {
                    track(e)
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        val b = bounds()
                        mochiParams.x = (startX + dx).toInt().coerceIn(b.minX, b.maxX)
                        mochiParams.y = (startY + dy).toInt().coerceIn(b.minY, b.maxY)
                        wm.updateViewLayout(v, mochiParams)
                        if (bubbleShown) positionBubble()
                        positionRich()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    if (dragging) {
                        val t = tracker
                        track(e)
                        t?.computeCurrentVelocity(1000)
                        val vx = t?.xVelocity ?: 0f
                        val vy = t?.yVelocity ?: 0f
                        val d = resources.displayMetrics.density
                        prefs.overlayX = mochiParams.x
                        prefs.overlayY = mochiParams.y
                        if (hypot(vx, vy) > 400 * d) startFling(vx, vy)
                    } else if (!longPressed) {
                        onTapped()
                    }
                    // 手をはなしたら、また勝手にふわふわし始める
                    if (!moving && prefs.driftEnabled) startFling(0f, 0f)
                    tracker?.recycle(); tracker = null
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    if (prefs.driftEnabled) startFling(0f, 0f)
                    tracker?.recycle(); tracker = null
                }
            }
            return true
        }
    }

    // ---------- ぶん投げ & 壁でバウンド ----------

    private data class Bounds(val minX: Int, val maxX: Int, val minY: Int, val maxY: Int)

    /**
     * もちもちが動ける範囲。Viewのまわりには透明な余白があるので、
     * からだそのものが画面の端にぴったり当たるように、少しはみ出すのを許す。
     */
    private fun bounds(): Bounds {
        val (sw, sh) = screenSize()
        val m = mochi
        val w = m?.width?.takeIf { it > 0 } ?: m?.measuredWidth ?: 0
        val h = m?.height?.takeIf { it > 0 } ?: m?.measuredHeight ?: 0
        return Bounds(
            minX = -(w * MochiView.CONTENT_LEFT).toInt(),
            maxX = sw - (w * MochiView.CONTENT_RIGHT).toInt(),
            minY = -(h * MochiView.CONTENT_TOP).toInt(),
            maxY = sh - (h * MochiView.CONTENT_BOTTOM).toInt(),
        )
    }

    private var vx = 0f
    private var vy = 0f
    private var px = 0f
    private var py = 0f
    private var targetVX = 0f   // ふわふわ漂う時に目指す速さ
    private var targetVY = 0f
    private var resting = true  // true の間はその場でプカプカ休憩
    private var nextMoodAt = 0L // 次に「漂う/休む」を切り替える時刻
    private var lastFrameNanos = 0L
    private var moving = false
    private var lastOuchAt = 0L
    private var tilt = 0f

    private val dp get() = resources.displayMetrics.density

    private val motionFrame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val m = mochi ?: return
            if (!moving) return
            val dt = if (lastFrameNanos == 0L) 1f / 60f
            else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(0.001f, 0.05f)
            lastFrameNanos = frameTimeNanos

            val drift = prefs.driftEnabled
            if (drift) updateMood()
            else { targetVX = 0f; targetVY = 0f }

            // 速さを目指す速さに少しずつ近づける。
            // 投げられて速い時は空気でじわっと減速、ふだんはふんわり向きを変える。
            val speed = hypot(vx, vy)
            val rate = if (speed > 120 * dp) FRICTION else 0.7f
            val k = 1f - exp(-rate * dt)
            vx += (targetVX - vx) * k
            vy += (targetVY - vy) * k

            px += vx * dt
            py += vy * dt

            // 壁にぶつかったら跳ね返る。漂っている時は、目指す向きも壁の反対にする。
            val b = bounds()
            var hitH = 0f
            var hitV = 0f
            if (px < b.minX) { px = b.minX.toFloat(); hitH = abs(vx); vx = abs(vx) * RESTITUTION; targetVX = abs(targetVX) }
            if (px > b.maxX) { px = b.maxX.toFloat(); hitH = abs(vx); vx = -abs(vx) * RESTITUTION; targetVX = -abs(targetVX) }
            if (py < b.minY) { py = b.minY.toFloat(); hitV = abs(vy); vy = abs(vy) * RESTITUTION; targetVY = abs(targetVY) }
            if (py > b.maxY) { py = b.maxY.toFloat(); hitV = abs(vy); vy = -abs(vy) * RESTITUTION; targetVY = -abs(targetVY) }
            val hit = maxOf(hitH, hitV)
            if (hit > 150 * dp) {
                val strength = hit / (2500 * dp)
                m.bump(horizontal = hitH >= hitV, strength = strength)
                val now = System.currentTimeMillis()
                if (strength > 0.6f && now - lastOuchAt > 2500 && Random.nextInt(3) == 0) {
                    lastOuchAt = now
                    talk(MochiLines.ouch(prefs.masterName))
                }
            } else if (hit > 10 * dp) {
                m.bump(horizontal = hitH >= hitV, strength = 0.12f) // ふわっと当たった時も少しだけむにっ
            }

            // 進む向きに少し傾く
            val wantTilt = (vx / (60 * dp) * 6f).coerceIn(-14f, 14f)
            tilt += (wantTilt - tilt) * (1f - exp(-5f * dt))
            m.tilt = tilt

            val nx = px.toInt()
            val ny = py.toInt()
            if (nx != mochiParams.x || ny != mochiParams.y) {
                mochiParams.x = nx
                mochiParams.y = ny
                wm.updateViewLayout(m, mochiParams)
                if (bubbleShown) positionBubble()
                        positionRich()
            }

            // 勝手に動かない設定で、投げた勢いもなくなったら止める
            if (!drift && hypot(vx, vy) < 30 * dp) stopFling(save = true)
            else Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /** ときどき「ふわふわ漂う」と「その場で休む」を切り替える */
    private fun updateMood() {
        val now = System.currentTimeMillis()
        if (now < nextMoodAt) return
        resting = if (resting) false else Random.nextInt(3) == 0
        if (resting) {
            targetVX = 0f; targetVY = 0f
            nextMoodAt = now + 4000 + Random.nextLong(6000)
            prefs.overlayX = mochiParams.x
            prefs.overlayY = mochiParams.y
        } else {
            // ゆっくり、好きな方向へ。上下より左右に流れやすくして、風船っぽく。
            val angle = Random.nextDouble(0.0, 2 * Math.PI)
            val sp = (18 + Random.nextInt(28)) * dp
            targetVX = (kotlin.math.cos(angle) * sp).toFloat()
            targetVY = (kotlin.math.sin(angle) * sp * 0.6f).toFloat()
            nextMoodAt = now + 3000 + Random.nextLong(5000)
        }
    }

    /** 動きのループを始める(投げた時はその勢いから) */
    private fun startFling(vx0: Float, vy0: Float) {
        val max = 7000 * dp
        val speed = hypot(vx0, vy0)
        val k = if (speed > max) max / speed else 1f
        vx = vx0 * k
        vy = vy0 * k
        px = mochiParams.x.toFloat()
        py = mochiParams.y.toFloat()
        // 投げられた直後は少しのあいだ漂い続ける
        resting = true
        nextMoodAt = System.currentTimeMillis() + if (speed > 0f) 1500 else 2500
        lastFrameNanos = 0L
        if (!moving) {
            moving = true
            Choreographer.getInstance().postFrameCallback(motionFrame)
        }
    }

    /** 動きのループを止める(つかんだ時・おやすみの時) */
    private fun stopFling(save: Boolean) {
        if (!moving) return
        moving = false
        Choreographer.getInstance().removeFrameCallback(motionFrame)
        if (save) {
            prefs.overlayX = mochiParams.x
            prefs.overlayY = mochiParams.y
        }
    }

    private fun onTapped() {
        // お知らせの吹き出しが出ている時は、タップで閉じる
        if (rich != null) { dismissRich(); return }
        mochi?.react()
        val name = prefs.masterName
        talkCount++
        // あいさつ → (天気) → (カロリー) → ふつうのおしゃべり、の順にくり返す
        val topics = buildList<() -> Unit> {
            add { talk(MochiLines.greeting(name)) }
            if (prefs.careWeather) add { talkWeather() }
            if (prefs.careCalorie) add { talk(CalorieStore(this@OverlayService).line(name)) }
            if (prefs.careMoney) add { talk(MoneyStore(this@OverlayService).line(name)) }
        }
        val i = (talkCount - 1) % (topics.size + 1)
        when {
            i < topics.size -> topics[i]()
            else -> talk(MochiLines.random(name))
        }
    }

    private fun talkWeather() {
        talk("お天気を調べてきますね…")
        WeatherRepository.today(prefs) { w ->
            if (mochi != null) talk(WeatherRepository.line(w, prefs))
        }
    }

    private fun openSettings() {
        mochi?.react()
        talk(MochiLines.openSettings(prefs.masterName))
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    // ---------- ロック画面 ----------

    /** 画面がついた時、ロックがかかっていたらロック画面にもメイドさんを出す */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val km = getSystemService(KeyguardManager::class.java)
            LockDiag.log(this@OverlayService, "画面ON ロック=${km?.isKeyguardLocked} 設定=${prefs.lockScreenMaid} 表示中=${LockMaidActivity.isShowing}")
            if (!prefs.lockScreenMaid || LockMaidActivity.isShowing) return
            if (km?.isKeyguardLocked != true) return
            // 方法1: そのまま画面を開く
            val r = runCatching {
                startActivity(
                    Intent(this@OverlayService, LockMaidActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                )
            }
            LockDiag.log(this@OverlayService, "方法1 " + (r.exceptionOrNull()?.let { "失敗: ${it.javaClass.simpleName}" } ?: "依頼しました"))
            // 方法2: 開かなかったら、目覚ましアプリと同じ「全画面通知」で開く
            handler.postDelayed({
                if (!LockMaidActivity.isShowing && km.isKeyguardLocked) {
                    LockDiag.log(this@OverlayService, "方法1では開かなかったので、方法2(全画面通知)を試します")
                    postLockFullScreen()
                }
            }, 700)
        }
    }

    private fun postLockFullScreen() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !nm.canUseFullScreenIntent()) {
            LockDiag.log(this, "方法2: 全画面通知が許可されていません")
            return
        }
        nm.createNotificationChannel(
            NotificationChannel(LOCK_CHANNEL, "ロック画面のメイドさん", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        val pi = PendingIntent.getActivity(
            this, 3, Intent(this, LockMaidActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, LOCK_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("メイドさん")
            .setContentText("ロック画面にいます")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .build()
        runCatching { nm.notify(LOCK_NOTIFICATION_ID, n) }
            .onFailure { LockDiag.log(this, "方法2 失敗: ${it.javaClass.simpleName}") }
            .onSuccess { LockDiag.log(this, "方法2 依頼しました") }
    }

    // ---------- 電池 ----------

    private var lastBatteryWarn = 101

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!prefs.batteryTalk) return
            if (intent.action == Intent.ACTION_POWER_CONNECTED) {
                if (lastBatteryWarn <= 20) talk("充電ありがとうございます、${prefs.masterName}！")
                lastBatteryWarn = 101
                return
            }
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            if (level < 0 || charging) return
            val pct = level * 100 / scale
            // 20% と 10% と 5% を下回った時に1回ずつ
            val step = listOf(20, 10, 5).firstOrNull { pct <= it && lastBatteryWarn > it } ?: return
            lastBatteryWarn = step
            talk(
                when (step) {
                    20 -> "${prefs.masterName}、電池が残り${pct}%です。そろそろ充電しましょう！"
                    10 -> "電池が${pct}%しかありません…！充電器はどこですか〜？"
                    else -> "もう${pct}%です！このままだとわたし、消えちゃいます…！"
                },
            )
        }
    }

    // ---------- 吹き出し ----------

    private fun talk(text: String) {
        if (rich != null) dismissRich()
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

        var y = mochiParams.y - bh + (mochiH * (MochiView.CONTENT_TOP - 0.02f)).toInt()
        if (y < 0) {
            b.tailOnTop = true
            y = mochiParams.y + mochiH - (mochiH * 0.05f).toInt()
        }
        bubbleParams.x = (mochiParams.x + mochiW / 2 - bw / 2).coerceIn(0, (sw - bw).coerceAtLeast(0))
        bubbleParams.y = y.coerceIn(0, (sh - bh).coerceAtLeast(0))
        b.tailX = (mochiParams.x + mochiW / 2 - bubbleParams.x).toFloat()
        if (bubbleShown) wm.updateViewLayout(b, bubbleParams)
    }

    // ---------- お知らせの吹き出し ----------

    private fun showRich(spec: CardSpec) {
        if (mochi == null) return
        dismissRich(resume = false)
        setBubbleVisible(false)
        handler.removeCallbacks(hideBubble)
        // お知らせしている間は、その場で止まって待っている
        stopFling(save = true)

        val (sw, _) = screenSize()
        val d = resources.displayMetrics.density
        val frame = BubbleFrame(this)
        frame.addView(CardContent.build(this, spec, onClose = { dismissRich() }, onTouched = { keepRich() }))
        richParams.width = (280 * d).toInt().coerceAtMost(sw - (16 * d).toInt())
        rich = frame
        richSpec = spec
        positionRich()
        wm.addView(frame, richParams)
        mochi?.react()
        keepRich()
        if (spec.sticky) handler.postDelayed(alarmNudge, 2500)
    }

    private fun keepRich() {
        handler.removeCallbacks(richDismiss)
        if (richSpec?.sticky != true) handler.postDelayed(richDismiss, 12_000)
    }

    private fun dismissRich(resume: Boolean = true) {
        handler.removeCallbacks(richDismiss)
        handler.removeCallbacks(alarmNudge)
        val f = rich ?: return
        runCatching { wm.removeView(f) }
        rich = null
        richSpec = null
        if (resume && prefs.driftEnabled && mochi != null) startFling(0f, 0f)
    }

    /** お知らせの吹き出しを、メイドさんの頭の上(入りきらなければ足元)に置く */
    private fun positionRich() {
        val f = rich ?: return
        val m = mochi ?: return
        val (sw, sh) = screenSize()
        val mochiW = m.width.takeIf { it > 0 } ?: m.measuredWidth
        val mochiH = m.height.takeIf { it > 0 } ?: m.measuredHeight
        f.tailOnTop = false
        f.measure(
            View.MeasureSpec.makeMeasureSpec(richParams.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val bw = f.measuredWidth
        val bh = f.measuredHeight
        var y = mochiParams.y - bh + (mochiH * (MochiView.CONTENT_TOP - 0.02f)).toInt()
        if (y < 0) {
            f.tailOnTop = true
            y = mochiParams.y + mochiH - (mochiH * 0.05f).toInt()
        }
        richParams.x = (mochiParams.x + mochiW / 2 - bw / 2).coerceIn(0, (sw - bw).coerceAtLeast(0))
        richParams.y = y.coerceIn(0, (sh - bh).coerceAtLeast(0))
        f.tailX = (mochiParams.x + mochiW / 2 - richParams.x).toFloat()
        if (f.isAttachedToWindow) wm.updateViewLayout(f, richParams)
    }

    companion object {
        private const val CHANNEL_ID = "mochi_overlay"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.mochimate.maid.STOP"
        private const val LOCK_CHANNEL = "mochi_lock"
        const val LOCK_NOTIFICATION_ID = 7
        private const val RESTITUTION = 0.72f // 跳ね返りの強さ(1で減らない)
        private const val FRICTION = 1.3f     // 空気の抵抗(大きいほど早く止まる)

        @Volatile
        var isRunning = false
            private set

        @Volatile
        private var instance: OverlayService? = null

        /** ホーム画面に小さなカードを出して、メイドさんにもひと言しゃべってもらう */
        /** お知らせを出す。ふだんはメイドさんの吹き出しで、設定によっては画面の上のカードで。 */
        fun showCard(spec: CardSpec): Boolean {
            val s = instance ?: return false
            s.handler.post {
                if (s.prefs.cardAtTop) {
                    val c = s.card ?: NotifyCard(s, s.wm).also { s.card = it }
                    c.show(spec.copy(header = null))
                    spec.header?.let { s.mochi?.react(); s.talk(it) }
                } else {
                    s.showRich(spec)
                }
            }
            return true
        }

        /** 元の通知が消えた時(アラームを止めた時など)に、お知らせも消す */
        fun dismissTag(tag: String) {
            val s = instance ?: return
            s.handler.post {
                if (s.richSpec?.tag == tag) s.dismissRich()
                if (s.card?.tag == tag) s.card?.dismiss()
            }
        }

        /** ほかの場所(通知の読み取りなど)から、メイドさんにしゃべってもらう */
        fun say(text: String) {
            val s = instance ?: return
            s.handler.post {
                s.mochi?.react()
                s.talk(text)
            }
        }

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
