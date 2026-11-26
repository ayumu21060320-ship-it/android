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

        bindCheck(R.id.careWeather, prefs.careWeather) { prefs.careWeather = it }
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

    private fun bindCheck(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        findViewById<CheckBox>(id).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
    }
}
