package com.mochimate.maid

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * アプリ全体。メイドさんのアプリの画面が開いている間は、
 * ホーム画面に浮かんでいるメイドさんを隠す(2人いるように見えないように)。
 */
class MochiApp : Application() {

    private var started = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (activity is LockMaidActivity) return // ロック画面のメイドさんは別
                started++
                OverlayService.setAppVisible(true)
            }

            override fun onActivityStopped(activity: Activity) {
                if (activity is LockMaidActivity) return
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) OverlayService.setAppVisible(false)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
