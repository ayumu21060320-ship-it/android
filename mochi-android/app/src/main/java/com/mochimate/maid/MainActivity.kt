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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

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
        bindCheck(R.id.careCalorie, prefs.careCalorie) { prefs.careCalorie = it }
        bindCheck(R.id.careMoney, prefs.careMoney) { prefs.careMoney = it }

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
            toggle.postDelayed({ refresh() }, 300)
        }

        hello.text = MochiLines.greeting(prefs.masterName)

        // 常駐中の通知を出すための許可(Android 13以降)。断られてももちもちは動く。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

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
