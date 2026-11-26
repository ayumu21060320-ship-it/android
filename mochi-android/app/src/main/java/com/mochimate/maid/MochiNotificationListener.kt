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
    /** もう教えた通知(更新されても2回目は教えない) */
    private val seenKeys = HashSet<String>()

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        // 自分のアプリの通知は無視。ただし「テストの支払い通知」だけは本物の支払いアプリとして扱う
        val isTest = sbn.packageName == packageName && n.extras.getBoolean(EXTRA_TEST)
        if (sbn.packageName == packageName && !isTest) return
        val pkg = sbn.packageName
        // まとめ通知は中身がないので無視
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        // 同じ通知の更新(タイマーのカウントダウンなど)は、最初の1回だけ扱う
        val firstTime = seenKeys.add(sbn.key)

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val label = if (isTest) "テスト支払い" else appLabel(pkg)

        if (!sbn.isOngoing && (isTest || pkg in prefs.paymentApps)) {
            val pay = PaymentParser.parse(title, text)
            if (pay != null) {
                val money = MoneyStore(this)
                if (!money.isDuplicate(pay.amount, label)) {
                    val time = System.currentTimeMillis()
                    val category = MoneyStore.guessCategory(pay.store)
                    money.add(MoneyEntry(pay.amount, category, pay.store ?: label, time, label))
                    OverlayService.showCard(
                        CardSpec(
                            icon = appIcon(pkg),
                            appLabel = label,
                            header = "${label}で払いましたね！家計簿につけておきました。",
                            title = MoneyStore.yen(pay.amount),
                            body = pay.store,
                            chips = MoneyStore.CATEGORIES,
                            selectedChip = category,
                            onChip = { c -> MoneyStore(this).setCategory(time, c) },
                            onUndo = {
                                MoneyStore(this).removeByTime(time)
                                OverlayService.say("取り消しておきました！")
                            },
                        ),
                    )
                }
                return
            }
        }

        if (pkg !in prefs.announceApps || !firstTime) return

        // アラーム: 鳴っている間ずっと吹き出しを出して、「止める」「スヌーズ」を押せるようにする
        // 時計アプリによっては「アラーム」の印がないので、全画面で出る時計アプリの通知もアラームとみなす
        val isAlarm = n.category == Notification.CATEGORY_ALARM ||
            (pkg.contains("clock", ignoreCase = true) && n.fullScreenIntent != null)
        if (isAlarm) {
            val actions = n.actions.orEmpty().mapNotNull { a ->
                val t = a.title?.toString() ?: return@mapNotNull null
                when {
                    STOP.containsMatchIn(t) -> "止める" to { sendIntent(a.actionIntent) }
                    SNOOZE.containsMatchIn(t) -> "スヌーズ" to { sendIntent(a.actionIntent) }
                    else -> null
                }
            }.distinctBy { it.first }
            OverlayService.showCard(
                CardSpec(
                    icon = appIcon(pkg),
                    appLabel = label,
                    header = "${prefs.masterName}、アラームの時間ですよ〜！起きてください！",
                    title = title?.takeIf { it.isNotBlank() } ?: "アラーム",
                    body = text,
                    actions = actions,
                    onOpen = if (actions.isEmpty()) n.contentIntent?.let { pi -> { sendIntent(pi) } } else null,
                    tag = sbn.key,
                    sticky = true,
                ),
            )
            return
        }

        // 同じアプリから立て続けに来た時は、少し間をあける
        val now = System.currentTimeMillis()
        if (now - (lastAnnounce[pkg] ?: 0L) < 8_000) return
        lastAnnounce[pkg] = now
        val who = title?.takeIf { it.isNotBlank() }
        val body = text?.replace('\n', ' ')?.let { if (it.length > 60) it.take(60) + "…" else it }
        OverlayService.showCard(
            CardSpec(
                icon = appIcon(pkg),
                appLabel = label,
                header = "${label}から通知が来ましたよ！",
                title = who ?: label,
                body = if (prefs.announceBody) body else null,
                onOpen = n.contentIntent?.let { pi -> { sendIntent(pi) } },
                tag = sbn.key,
            ),
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        seenKeys.remove(sbn.key)
        OverlayService.dismissTag(sbn.key)
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

    companion object {
        const val EXTRA_TEST = "com.mochimate.maid.TEST_PAYMENT"
        private val STOP = Regex("解除|停止|止める|オフ|閉じる|dismiss|stop|turn off", RegexOption.IGNORE_CASE)
        private val SNOOZE = Regex("スヌーズ|snooze|後で", RegexOption.IGNORE_CASE)
        private const val TEST_CHANNEL = "mochi_test"

        /** 支払いアプリのふりをした通知を出す(サブ機で連携を確かめる用) */
        fun postTestPayment(context: android.content.Context) {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            nm.createNotificationChannel(
                android.app.NotificationChannel(TEST_CHANNEL, "テスト用の通知", android.app.NotificationManager.IMPORTANCE_DEFAULT),
            )
            val n = Notification.Builder(context, TEST_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("お支払い完了（テスト）")
                .setContentText("セブン-イレブン 青葉通店で1,280円のお支払いが完了しました")
                .setAutoCancel(true)
                .addExtras(android.os.Bundle().apply { putBoolean(EXTRA_TEST, true) })
                .build()
            nm.notify(9999, n)
        }
    }

    private fun appIcon(pkg: String): Drawable? = runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()

    private fun appLabel(pkg: String): String = runCatching {
        val pm = packageManager
        @Suppress("DEPRECATION")
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
