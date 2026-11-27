package com.mochimate.maid

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import java.util.Calendar

/**
 * ロック画面を見た時(画面をつけた時)だけ、ロック画面の上にメイドさんを出す画面。
 * 画面の一部だけの小さなウィンドウにして、まわりのタッチはロック画面にそのまま通す。
 * ロックを解除したり、画面を消したりしたら閉じる。ロックそのものは一切さわらない。
 */
class LockMaidActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var mochi: MochiView
    private lateinit var bubble: BubbleView

    private val closer = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finishQuietly()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        // 小さなウィンドウにして、外側のタッチ(ロック解除のスワイプなど)は下のロック画面へ通す
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )

        val d = resources.displayMetrics.density
        bubble = BubbleView(this).apply { text = line() }
        mochi = MochiView(this).apply {
            setOnClickListener {
                react()
                bubble.text = MochiLines.random(prefs.masterName)
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(bubble)
            addView(mochi)
        }
        setContentView(root)

        // 置く場所: 画面の下のほう、まんなか(ロック解除のスワイプや時計とかぶりにくい所)
        window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
        window.attributes = window.attributes.apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (150 * d).toInt()
        }

        registerReceiver(closer, IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
        isShowing = true
        LockDiag.log(this, "ロック画面のメイドさんを開きました")
        // 方法2で開いた時の通知は、もういらないので消す
        getSystemService(android.app.NotificationManager::class.java)?.cancel(OverlayService.LOCK_NOTIFICATION_ID)
        mochi.postDelayed({ mochi.react() }, 300)
    }

    /** ロック画面でのひと言(個人的な情報は出さない) */
    private fun line(): String {
        val c = Calendar.getInstance()
        val time = "%d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        return "${MochiLines.greeting(prefs.masterName)}\n今は${time}ですよ。"
    }

    private fun finishQuietly() {
        if (!isFinishing) {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        LockDiag.log(this, "表示されました")
    }

    override fun onStop() {
        super.onStop()
        LockDiag.log(this, "見えなくなりました")
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(closer) }
        isShowing = false
        super.onDestroy()
    }

    companion object {
        @Volatile
        var isShowing = false
            private set
    }
}
