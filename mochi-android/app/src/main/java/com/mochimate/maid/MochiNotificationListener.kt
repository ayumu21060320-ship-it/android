package com.mochimate.maid

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.graphics.drawable.Drawable
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * スマホに届いた通知を見て、
 *  - 「教えてもらう」にしたアプリの通知 → メイドさんが吹き出しで教える
 *  - 「家計簿に自動記録」にしたアプリの支払い通知 → 金額を読み取って家計簿に記録
 * をする。通知の中身はどこにも送らず、スマホの中だけで処理する。
 */
class MochiNotificationListener : NotificationListenerService() {

    private lateinit var prefs: Prefs
    private val lastAnnounce = HashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return
        val n = sbn.notification
        // 常駐通知・まとめ通知・音楽プレーヤーなどは無視
        if (sbn.isOngoing || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val label = appLabel(pkg)

        if (pkg in prefs.paymentApps) {
            val pay = PaymentParser.parse(title, text)
            if (pay != null) {
                val money = MoneyStore(this)
                if (!money.isDuplicate(pay.amount, label)) {
                    val time = System.currentTimeMillis()
                    val category = MoneyStore.guessCategory(pay.store)
                    money.add(MoneyEntry(pay.amount, category, pay.store ?: label, time, label))
                    val where = pay.store?.let { "（$it）" } ?: ""
                    val line = "${label}で${MoneyStore.yen(pay.amount)}の支払い${where}、家計簿につけておきました！"
                    if (prefs.notifyCard) {
                        OverlayService.showCard(
                            CardSpec(
                                icon = appIcon(pkg),
                                appLabel = "$label ・ 家計簿に記録しました",
                                title = MoneyStore.yen(pay.amount),
                                body = pay.store,
                                chips = MoneyStore.CATEGORIES,
                                selectedChip = category,
                                onChip = { c -> MoneyStore(this).setCategory(time, c) },
                                onUndo = {
                                    MoneyStore(this).removeByTime(time)
                                    OverlayService.say("取り消しておきました！")
                                },
                                onOpen = null,
                            ),
                            line = "${label}で払いましたね！",
                        )
                    } else {
                        OverlayService.say(line)
                    }
                }
                return
            }
        }

        if (pkg in prefs.announceApps) {
            // 同じアプリから立て続けに来た時は、少し間をあける
            val now = System.currentTimeMillis()
            if (now - (lastAnnounce[pkg] ?: 0L) < 8_000) return
            lastAnnounce[pkg] = now
            val who = title?.takeIf { it.isNotBlank() }
            val body = text?.replace('\n', ' ')?.let { if (it.length > 40) it.take(40) + "…" else it }
            if (prefs.notifyCard) {
                val open = n.contentIntent
                OverlayService.showCard(
                    CardSpec(
                        icon = appIcon(pkg),
                        appLabel = label,
                        title = who ?: "${label}から通知です",
                        body = if (prefs.announceBody) body else null,
                        onOpen = open?.let { pi -> { sendIntent(pi) } },
                    ),
                    line = "${label}から通知が来ましたよ！",
                )
            } else {
                val msg = buildString {
                    append("${label}から通知です")
                    if (who != null) append("、${who}")
                    append("！")
                    if (prefs.announceBody && !body.isNullOrBlank()) append("\n「$body」")
                }
                OverlayService.say(msg)
            }
        }
    }

    /** 通知をタップした時と同じように、そのアプリを開く */
    private fun sendIntent(pi: PendingIntent) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val opts = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                pi.send(this, 0, null, null, null, null, opts.toBundle())
            } else {
                pi.send()
            }
        }
    }

    private fun appIcon(pkg: String): Drawable? = runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()

    private fun appLabel(pkg: String): String = runCatching {
        val pm = packageManager
        @Suppress("DEPRECATION")
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
