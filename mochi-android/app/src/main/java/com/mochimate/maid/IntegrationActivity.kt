package com.mochimate.maid

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** ほかのアプリとの連携の設定(通知の読み取り・家計簿の自動記録・電池) */
class IntegrationActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var grant: Button
    private lateinit var appList: LinearLayout
    private val d by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(20), px(20), px(20))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.bg))
            fitsSystemWindows = true
            addView(root)
        })

        // --- 通知へのアクセス ---
        val accessCard = card()
        accessCard.addView(title("通知の読み取り"))
        accessCard.addView(sub("選んだアプリの通知をメイドさんが教えてくれたり、支払いの通知を家計簿に自動でつけたりします。通知の中身はスマホの外には送りません。"))
        status = TextView(this).apply { textSize = 15f; setPadding(0, px(8), 0, 0) }
        accessCard.addView(status)
        grant = Button(this).apply {
            text = "通知へのアクセスを許可する"
            setTextColor(getColor(R.color.accent))
            setBackgroundResource(R.drawable.button_sub_bg)
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        accessCard.addView(grant, LinearLayout.LayoutParams(-1, px(48)).apply { topMargin = px(10) })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            accessCard.addView(sub("※「制限付き設定」と出てオンにできない時は、下のボタン → 右上の︙ →「制限付き設定を許可」をしてから、もう一度ためしてください。"))
            accessCard.addView(Button(this).apply {
                text = "アプリ情報を開く"
                setTextColor(getColor(R.color.ink_sub))
                setBackgroundResource(R.drawable.button_sub_bg)
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }, LinearLayout.LayoutParams(-1, px(44)).apply { topMargin = px(6) })
        }

        // --- オプション ---
        val opt = card()
        opt.addView(title("お知らせのしかた"))
        opt.addView(check("🧹 メイドさんが知らせたら、元のアプリの通知は消す", prefs.takeOver) { prefs.takeOver = it })
        opt.addView(sub("音やポップアップまで止めたい時は、下の一覧の 🔕 からそのアプリの通知を「サイレント」にしてね。"))
        opt.addView(check("🪟 吹き出しではなく、画面の上にカードで出す", prefs.cardAtTop) { prefs.cardAtTop = it })
        opt.addView(check("通知の本文も教えてもらう（オフなら「誰から」だけ）", prefs.announceBody) { prefs.announceBody = it })
        opt.addView(check("🔋 電池が少なくなったら教えてもらう", prefs.batteryTalk) { prefs.batteryTalk = it })
        opt.addView(Button(this).apply {
            text = "カードを試す（記録はされません）"
            setTextColor(getColor(R.color.accent))
            setBackgroundResource(R.drawable.button_sub_bg)
            setOnClickListener {
                if (!OverlayService.isRunning) {
                    text = "先にメイドさんをホーム画面に呼んでね"
                    return@setOnClickListener
                }
                OverlayService.showCard(
                    CardSpec(
                        icon = getDrawable(R.mipmap.ic_launcher),
                        appLabel = "テスト ・ 家計簿に記録しました",
                        header = "こんな感じでお知らせしますね！",
                        title = MoneyStore.yen(1280),
                        body = "セブン-イレブン 青葉通店",
                        chips = MoneyStore.CATEGORIES,
                        selectedChip = "食費",
                        onChip = {},
                        onUndo = { OverlayService.say("取り消しておきました！") },
                    ),
                )
                // ホーム画面に戻って見てもらう
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }, LinearLayout.LayoutParams(-1, px(44)).apply { topMargin = px(8) })

        opt.addView(Button(this).apply {
            text = "テストの支払い通知を送る（本番と同じ流れ）"
            setTextColor(getColor(R.color.accent))
            setBackgroundResource(R.drawable.button_sub_bg)
            setOnClickListener {
                val granted = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                    ?.contains(packageName) == true
                when {
                    !granted -> text = "先に通知へのアクセスを許可してね"
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2)
                        text = "通知を許可してから、もう一度押してね"
                    }
                    else -> {
                        MochiNotificationListener.postTestPayment(this@IntegrationActivity)
                        text = "送りました！家計簿に1,280円が記録されるはず"
                        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }, LinearLayout.LayoutParams(-1, px(44)).apply { topMargin = px(8) })

        opt.addView(Button(this).apply {
            text = "📜 通知の履歴を見る"
            setTextColor(getColor(R.color.accent))
            setBackgroundResource(R.drawable.button_sub_bg)
            setOnClickListener { startActivity(Intent(this@IntegrationActivity, HistoryActivity::class.java)) }
        }, LinearLayout.LayoutParams(-1, px(44)).apply { topMargin = px(8) })

        // --- アプリごとの設定 ---
        val apps = card()
        apps.addView(title("アプリごとの設定"))
        apps.addView(sub("🔔 … メイドさんが通知を教えてくれる\n💰 … 支払い通知を家計簿に自動記録\n🔕 … そのアプリの通知設定を開く（サイレントにする用）\n💬 のアプリは、送り主の名前も中身も読まず「LINEが来ましたよ」だけ伝えます。"))
        appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        apps.addView(appList)
        appList.addView(sub("アプリを読み込んでいます…"))
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        val granted = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
        status.text = if (granted) "✅ 許可されています" else "⚠️ まだ許可されていません"
        status.setTextColor(getColor(if (granted) R.color.ink else R.color.accent))
        grant.text = if (granted) "通知へのアクセスの設定を開く" else "通知へのアクセスを許可する"
    }

    private data class AppRow(val pkg: String, val label: String, val info: ResolveInfo, val payLike: Boolean, val clockLike: Boolean)

    private fun loadApps() {
        val main = Handler(Looper.getMainLooper())
        Executors.newSingleThreadExecutor().execute {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val rows = pm.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != packageName }
                .distinctBy { it.activityInfo.packageName }
                .map {
                    val label = it.loadLabel(pm).toString()
                    val pkg = it.activityInfo.packageName
                    AppRow(pkg, label, it, PAY_LIKE.containsMatchIn(label) || PAY_LIKE.containsMatchIn(pkg),
                        CLOCK_LIKE.containsMatchIn(label) || CLOCK_LIKE.containsMatchIn(pkg))
                }
                .sortedWith(compareByDescending<AppRow> { it.pkg in prefs.paymentApps || it.pkg in prefs.announceApps }
                    .thenByDescending { it.payLike }
                    .thenByDescending { it.clockLike }
                    .thenBy { it.label })
            main.post { renderApps(rows) }
        }
    }

    private fun renderApps(rows: List<AppRow>) {
        appList.removeAllViews()
        val pm = packageManager
        for (r in rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(4), 0, px(4))
            }
            row.addView(ImageView(this).apply { setImageDrawable(r.info.loadIcon(pm)) }, LinearLayout.LayoutParams(px(32), px(32)))
            row.addView(TextView(this).apply {
                text = (if (MochiNotificationListener.isPrivateApp(r.pkg)) "💬 " else "") + r.label
                textSize = 15f
                setTextColor(getColor(R.color.ink))
                setPadding(px(10), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(smallCheck("🔔", r.pkg in prefs.announceApps) { on ->
                prefs.announceApps = if (on) prefs.announceApps + r.pkg else prefs.announceApps - r.pkg
            })
            row.addView(smallCheck("💰", r.pkg in prefs.paymentApps) { on ->
                prefs.paymentApps = if (on) prefs.paymentApps + r.pkg else prefs.paymentApps - r.pkg
                if (on && !prefs.careMoney) prefs.careMoney = true
            })
            row.addView(TextView(this).apply {
                text = "🔕"
                textSize = 18f
                setPadding(px(8), px(6), px(2), px(6))
                contentDescription = "${r.label}の通知設定を開く"
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, r.pkg))
                }
            })
            appList.addView(row)
        }
    }

    // ---------- 小さな部品 ----------

    private fun px(v: Int) = (v * d).toInt()

    private fun card(): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_bg)
            setPadding(px(16), px(16), px(16), px(16))
        }
        root.addView(c, LinearLayout.LayoutParams(-1, -2).apply { if (root.childCount > 0) topMargin = px(14) })
        return c
    }

    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 17f; setTextColor(getColor(R.color.ink)); paint.isFakeBoldText = true
    }

    private fun sub(t: String) = TextView(this).apply {
        text = t; textSize = 13f; setTextColor(getColor(R.color.ink_sub)); setPadding(0, px(4), 0, 0)
    }

    private fun check(t: String, initial: Boolean, onChange: (Boolean) -> Unit) = CheckBox(this).apply {
        text = t; textSize = 15f; setTextColor(getColor(R.color.ink)); isChecked = initial
        setOnCheckedChangeListener { _, c -> onChange(c) }
    }

    private fun smallCheck(t: String, initial: Boolean, onChange: (Boolean) -> Unit) = CheckBox(this).apply {
        text = t; textSize = 15f; isChecked = initial
        setOnCheckedChangeListener { _, c -> onChange(c) }
        visibility = View.VISIBLE
    }

    companion object {
        private val CLOCK_LIKE = Regex("clock|時計|アラーム|alarm", RegexOption.IGNORE_CASE)
        private val PAY_LIKE = Regex("pay|ペイ|払い|wallet|ウォレット|銀行|bank|カード|card|suica|pasmo|楽天|PayPay|メルペイ|クレジット", RegexOption.IGNORE_CASE)
    }
}
