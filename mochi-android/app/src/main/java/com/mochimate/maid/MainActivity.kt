package com.mochimate.maid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView

/** 設定画面: 呼び方・お世話の種類を選んで、もちもちをホーム画面に呼ぶ。 */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var hello: TextView
    private lateinit var permissionCard: View
    private lateinit var toggle: Button
    private lateinit var home: HomeSettings

    // タブ: 0=ホーム 1=健康 2=お金 3=設定
    private lateinit var homeTab: HomeTab
    private lateinit var healthTab: HealthTab
    private lateinit var moneyTab: MoneyTab
    private lateinit var settingsView: View
    private var current = 0
    private val navItems = ArrayList<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shell)
        prefs = Prefs(this)
        setupShell()

        hello = findViewById(R.id.hello)
        permissionCard = findViewById(R.id.permissionCard)
        toggle = findViewById(R.id.toggle)

        val preview = findViewById<MochiView>(R.id.preview)
        preview.setOnClickListener {
            preview.react()
            hello.text = MochiLines.random(prefs.masterName)
        }

        val name = findViewById<EditText>(R.id.name)
        if (prefs.masterName != Prefs.DEFAULT_NAME) name.setText(prefs.masterName)
        name.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                prefs.masterName = s?.toString().orEmpty()
                hello.text = MochiLines.greeting(prefs.masterName)
            }
        })

        val weatherBox = findViewById<View>(R.id.weatherBox)
        weatherBox.visibility = if (prefs.careWeather) View.VISIBLE else View.GONE
        bindCheck(R.id.careWeather, prefs.careWeather) {
            prefs.careWeather = it
            weatherBox.visibility = if (it) View.VISIBLE else View.GONE
        }
        setupCitySearch()
        bindCheck(R.id.drift, prefs.driftEnabled) { prefs.driftEnabled = it }
        bindCheck(R.id.hideFullscreen, prefs.hideInFullscreen) { prefs.hideInFullscreen = it }
        home = HomeSettings(this, findViewById(R.id.homeCard)).also { it.build() }
        bindCheck(R.id.lockMaid, prefs.lockScreenMaid) { prefs.lockScreenMaid = it }
        findViewById<Button>(R.id.lockDiag).setOnClickListener {
            val needFsi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                !getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()
            val b = android.app.AlertDialog.Builder(this)
                .setTitle("ロック画面の記録")
                .setMessage(
                    (if (needFsi) "⚠️「全画面通知」が許可されていません。下のボタンから許可すると、ロック画面に出やすくなります。\n\n" else "") +
                        LockDiag.read(this),
                )
                .setPositiveButton("閉じる", null)
            if (needFsi) {
                b.setNeutralButton("全画面通知を許可") { _, _ ->
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")),
                    )
                }
            }
            b.show()
        }
        val openCalorie = findViewById<Button>(R.id.openCalorie)
        openCalorie.visibility = if (prefs.careCalorie) View.VISIBLE else View.GONE
        openCalorie.setOnClickListener { showTab(1) }
        bindCheck(R.id.careCalorie, prefs.careCalorie) {
            prefs.careCalorie = it
            openCalorie.visibility = if (it) View.VISIBLE else View.GONE
        }
        val openMoney = findViewById<Button>(R.id.openMoney)
        openMoney.visibility = if (prefs.careMoney) View.VISIBLE else View.GONE
        openMoney.setOnClickListener { showTab(2) }
        bindCheck(R.id.careMoney, prefs.careMoney) {
            prefs.careMoney = it
            openMoney.visibility = if (it) View.VISIBLE else View.GONE
        }
        val keyStatus = findViewById<TextView>(R.id.apiKeyStatus)
        fun showKeyStatus() {
            val k = prefs.anthropicKey
            keyStatus.text = if (k.isNullOrBlank()) "未設定" else "✅ 設定済み（…${k.takeLast(4)}）"
        }
        showKeyStatus()
        findViewById<EditText>(R.id.apiKey).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val v = s?.toString()?.trim().orEmpty()
                if (v.startsWith("sk-ant-") && v.length > 30) { prefs.anthropicKey = v; showKeyStatus() }
            }
        })
        findViewById<Button>(R.id.openChat).setOnClickListener { startActivity(Intent(this, ChatActivity::class.java)) }
        findViewById<Button>(R.id.openIntegration).setOnClickListener {
            startActivity(Intent(this, IntegrationActivity::class.java))
        }

        findViewById<Button>(R.id.grant).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            )
        }

        toggle.setOnClickListener {
            if (OverlayService.isRunning) {
                OverlayService.stop(this)
            } else {
                OverlayService.start(this)
                // ホーム画面に戻って、もちもちが出てくるところを見てもらう
                startActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            toggle.postDelayed({ refresh(); refreshTab() }, 300)
        }

        hello.text = MochiLines.greeting(prefs.masterName)

        // 常駐中の通知を出すための許可(Android 13以降)。断られてももちもちは動く。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        home.onPermissionResult(requestCode)
    }

    override fun onResume() {
        super.onResume()
        if (::home.isInitialized) {
            home.refresh()
            HomeGeofence.register(this) // 「常に許可」にして戻ってきた時に見守りを始める
        }
        MochiNotificationListener.ensureConnected(this)
        GarbageAlarm.schedule(this)
        ScheduleAlarm.schedule(this)
        refresh()
        refreshTab()
    }

    override fun onBackPressed() {
        when {
            findViewById<View>(R.id.recordSheet).visibility == View.VISIBLE -> closeRecordMenu()
            current != 0 -> showTab(0)
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    // ---------- 外枠: タブ・呼び鈴・記録メニュー ----------

    private fun setupShell() {
        val content = findViewById<android.widget.FrameLayout>(R.id.tabContent)
        homeTab = HomeTab(this) { showTab(3) }
        healthTab = HealthTab(this)
        moneyTab = MoneyTab(this)
        settingsView = layoutInflater.inflate(R.layout.activity_main, content, false)
        for (v in listOf(homeTab.view, healthTab.view, moneyTab.view, settingsView)) {
            content.addView(v, android.widget.FrameLayout.LayoutParams(-1, -1))
        }

        val nav = findViewById<LinearLayout>(R.id.bottomNav)
        val labels = listOf("🏠" to "ホーム", "🩺" to "健康", "💰" to "お金", "⚙️" to "設定")
        labels.forEachIndexed { i, (icon, label) ->
            val item = TextView(this).apply {
                text = "$icon\n$label"
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                setLineSpacing(0f, 1.1f)
                setOnClickListener { showTab(i) }
            }
            navItems += item
            nav.addView(item, LinearLayout.LayoutParams(0, -1, 1f))
        }
        // 右の呼び鈴とかぶらないよう、タブを少し左に寄せる
        nav.addView(View(this), LinearLayout.LayoutParams((84 * resources.displayMetrics.density).toInt(), -1))

        findViewById<View>(R.id.bell).setOnClickListener {
            if (findViewById<View>(R.id.recordSheet).visibility == View.VISIBLE) closeRecordMenu() else openRecordMenu()
        }
        findViewById<View>(R.id.dim).setOnClickListener { closeRecordMenu() }
        buildRecordMenu()
        showTab(0)
    }

    private fun showTab(i: Int) {
        current = i
        listOf(homeTab.view, healthTab.view, moneyTab.view, settingsView).forEachIndexed { j, v ->
            v.visibility = if (j == i) View.VISIBLE else View.GONE
        }
        navItems.forEachIndexed { j, t ->
            val on = j == i
            t.setTextColor(getColor(if (on) R.color.accent else R.color.ink_sub))
            t.paint.isFakeBoldText = on
            t.invalidate()
        }
        findViewById<TextView>(R.id.tabTitle).text = when (i) {
            1 -> "健康管理"
            2 -> "お金"
            3 -> "設定"
            else -> "もちもちメイド"
        }
        refreshTab()
    }

    private fun refreshTab() {
        if (!::homeTab.isInitialized) return
        when (current) {
            0 -> homeTab.refresh()
            1 -> healthTab.refresh()
            2 -> moneyTab.refresh()
            3 -> syncSettings()
        }
    }

    /** ホームのチップで切り替えた内容を、設定タブのチェックにも反映する */
    private fun syncSettings() {
        findViewById<CheckBox>(R.id.careWeather).isChecked = prefs.careWeather
        findViewById<CheckBox>(R.id.careCalorie).isChecked = prefs.careCalorie
        findViewById<CheckBox>(R.id.careMoney).isChecked = prefs.careMoney
        findViewById<CheckBox>(R.id.drift).isChecked = prefs.driftEnabled
        findViewById<CheckBox>(R.id.lockMaid).isChecked = prefs.lockScreenMaid
    }

    private fun buildRecordMenu() {
        val sheet = findViewById<LinearLayout>(R.id.recordSheet)
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        sheet.removeAllViews()
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val maid = MochiView(this)
        head.addView(maid, LinearLayout.LayoutParams(px(68), px(86)))
        head.addView(TextView(this).apply {
            text = "はい、${prefs.masterName}！\nなにを記録しますか？"
            textSize = 15f
            setTextColor(getColor(R.color.ink))
            setBackgroundResource(R.drawable.bubble_card_bg)
            setPadding(px(12), px(10), px(12), px(10))
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(6) })
        sheet.addView(head)

        val options = listOf(
            Triple("🍙", "ごはん", "食べたもの") to { startActivity(Intent(this, FoodSearchActivity::class.java)) },
            Triple("⚖️", "体重", "今日の体重") to { WeightDialogs.record(this) { refreshTab() } },
            Triple("💰", "お金", "使ったお金") to { startActivity(Intent(this, MoneyActivity::class.java)) },
            Triple("😊", "調子", "体調・気分") to { ConditionStore.dialog(this) { msg -> refreshTab(); toast(msg) } },
            Triple("✅", "やること", "今日のToDo") to { startActivity(Intent(this, TodoActivity::class.java)) },
            Triple("📅", "予定", "予定を入れる") to { startActivity(Intent(this, ScheduleActivity::class.java)) },
        )
        var row: LinearLayout? = null
        options.forEachIndexed { i, (labels, action) ->
            if (i % 2 == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                sheet.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })
            }
            val (icon, title, sub) = labels
            val opt = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.option_bg)
                setPadding(px(12), px(12), px(12), px(12))
                setOnClickListener { closeRecordMenu(); action() }
                addView(TextView(this@MainActivity).apply { text = icon; textSize = 22f })
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(px(8), 0, 0, 0)
                    addView(TextView(this@MainActivity).apply {
                        text = title; textSize = 15f; setTextColor(getColor(R.color.ink)); paint.isFakeBoldText = true
                    })
                    addView(TextView(this@MainActivity).apply { text = sub; textSize = 11f; setTextColor(getColor(R.color.ink_sub)) })
                })
            }
            row!!.addView(opt, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i % 2 == 1) marginStart = px(8) })
        }
    }

    private fun openRecordMenu() {
        buildRecordMenu() // 呼び方が変わっていても反映されるように
        findViewById<View>(R.id.dim).visibility = View.VISIBLE
        findViewById<View>(R.id.recordSheet).visibility = View.VISIBLE
        findViewById<TextView>(R.id.bell).apply { text = "✕"; isSelected = true }
        findViewById<TextView>(R.id.bellLabel).setTextColor(getColor(R.color.accent))
    }

    private fun closeRecordMenu() {
        findViewById<View>(R.id.dim).visibility = View.GONE
        findViewById<View>(R.id.recordSheet).visibility = View.GONE
        findViewById<TextView>(R.id.bell).apply { text = "🛎️"; isSelected = false }
        findViewById<TextView>(R.id.bellLabel).setTextColor(getColor(R.color.navy))
    }

    private fun toast(msg: String) = android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()

    private fun refresh() {
        val canOverlay = Settings.canDrawOverlays(this)
        permissionCard.visibility = if (canOverlay) View.GONE else View.VISIBLE
        toggle.isEnabled = canOverlay
        toggle.text = when {
            !canOverlay -> "先に許可してね"
            OverlayService.isRunning -> "おやすみさせる"
            else -> "ホーム画面に呼ぶ"
        }
    }

    // ---------- 天気の街えらび ----------

    private fun setupCitySearch() {
        val current = findViewById<TextView>(R.id.currentCity)
        val query = findViewById<EditText>(R.id.cityQuery)
        val results = findViewById<LinearLayout>(R.id.cityResults)
        val search = findViewById<Button>(R.id.citySearch)

        fun showCurrent() {
            current.text = prefs.cityName?.let { "いまの街: $it" } ?: "街がまだ選ばれていません"
        }
        showCurrent()

        fun doSearch() {
            val q = query.text.toString().trim()
            if (q.isEmpty()) return
            hideKeyboard(query)
            results.removeAllViews()
            results.addView(resultRow("さがしています…", null))
            WeatherRepository.searchCity(q) { cities ->
                results.removeAllViews()
                if (cities.isEmpty()) {
                    results.addView(resultRow("見つかりませんでした。別の書き方でためしてね", null))
                }
                for (city in cities) {
                    results.addView(
                        resultRow("${city.name}（${city.detail}）") {
                            prefs.setCity(city)
                            results.removeAllViews()
                            query.setText("")
                            showCurrent()
                            hello.text = "${city.name}のお天気を見ておきますね！"
                            WeatherRepository.today(prefs, force = true) { w ->
                                hello.text = WeatherRepository.line(w, prefs)
                            }
                        },
                    )
                }
            }
        }

        search.setOnClickListener { doSearch() }
        query.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { doSearch(); true } else false
        }
    }

    private fun resultRow(text: String, onClick: (() -> Unit)?): TextView {
        val pad = (10 * resources.displayMetrics.density).toInt()
        return TextView(this).apply {
            this.text = if (onClick != null) "📍 $text" else text
            textSize = 15f
            setTextColor(getColor(if (onClick != null) R.color.ink else R.color.ink_sub))
            setPadding(pad / 2, pad, pad / 2, pad)
            if (onClick != null) setOnClickListener { onClick() }
        }
    }

    private fun hideKeyboard(v: View) {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun bindCheck(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        findViewById<CheckBox>(id).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
    }
}
