package com.mochimate.maid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView

/** 設定画面の「おうち」カード(おかえり・いってらっしゃい) */
class HomeSettings(private val activity: Activity, private val card: LinearLayout) {

    private val prefs = Prefs(activity)
    private val d = activity.resources.displayMetrics.density
    private fun px(v: Int) = (v * d).toInt()

    private lateinit var status: TextView
    private lateinit var body: LinearLayout
    private lateinit var bgButton: Button
    private val radiusChips = mutableListOf<Pair<Int, TextView>>()

    fun build() {
        card.addView(TextView(activity).apply {
            text = "🏠 おうち"
            textSize = 17f
            setTextColor(activity.getColor(R.color.ink))
            paint.isFakeBoldText = true
        })
        card.addView(check("帰ってきたら「おかえりなさいませ」と言ってもらう", prefs.homeEnabled) { on ->
            prefs.homeEnabled = on
            if (on && !HomeGeofence.hasForeground(activity)) {
                activity.requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    REQ_LOCATION,
                )
            }
            if (on) HomeGeofence.register(activity) else HomeGeofence.unregister(activity)
            refresh()
        })

        body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        card.addView(body)

        status = TextView(activity).apply {
            textSize = 14f
            setTextColor(activity.getColor(R.color.ink_sub))
            setPadding(0, px(4), 0, 0)
        }
        body.addView(status)

        bgButton = button("位置情報を「常に許可」にする") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ_BACKGROUND)
            }
        }
        body.addView(bgButton, lp())

        body.addView(button("📍 今いる場所をおうちにする") { setHomeHere() }, lp())

        body.addView(TextView(activity).apply {
            text = "おうちとみなす範囲"
            textSize = 13f
            setTextColor(activity.getColor(R.color.ink_sub))
            setPadding(0, px(10), 0, px(4))
        })
        val chips = FlowLayout(activity)
        for (r in listOf(100, 150, 300)) {
            val chip = TextView(activity).apply {
                text = "${r}m"
                textSize = 14f
                setTextColor(activity.getColor(R.color.ink))
                setPadding(px(14), px(6), px(14), px(6))
                setOnClickListener {
                    prefs.homeRadius = r
                    HomeGeofence.register(activity)
                    refresh()
                }
            }
            radiusChips += r to chip
            chips.addView(chip)
        }
        body.addView(chips)

        body.addView(check("出かける時に「いってらっしゃいませ」も言ってもらう", prefs.homeSayGoodbye) { prefs.homeSayGoodbye = it })

        val tests = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        tests.addView(button("おかえりのテスト") { test(true) }, LinearLayout.LayoutParams(0, px(40), 1f))
        tests.addView(button("いってらっしゃいのテスト") { test(false) },
            LinearLayout.LayoutParams(0, px(40), 1f).apply { marginStart = px(8) })
        body.addView(tests, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10) })

        body.addView(TextView(activity).apply {
            text = "おうちの場所はスマホの中だけに保存して、どこにも送りません。"
            textSize = 12f
            setTextColor(activity.getColor(R.color.ink_sub))
            setPadding(0, px(8), 0, 0)
        })
        refresh()
    }

    fun refresh() {
        body.visibility = if (prefs.homeEnabled) View.VISIBLE else View.GONE
        val fg = HomeGeofence.hasForeground(activity)
        val bg = HomeGeofence.hasBackground(activity)
        bgButton.visibility = if (fg && !bg) View.VISIBLE else View.GONE
        status.text = when {
            !fg -> "⚠️ 位置情報の許可がまだです"
            !bg -> "⚠️ アプリを閉じている時も判定するために、位置情報を「常に許可」にしてください（ボタンを押すと設定が開きます）"
            prefs.homeLat == null -> "おうちにいる時に「今いる場所をおうちにする」を押してね"
            else -> "✅ おうちを見守っています（範囲 ${prefs.homeRadius}m）"
        }
        for ((r, chip) in radiusChips) {
            chip.setBackgroundResource(if (r == prefs.homeRadius) R.drawable.chip_custom_bg else R.drawable.chip_bg)
        }
    }

    fun onPermissionResult(requestCode: Int) {
        if (requestCode == REQ_LOCATION || requestCode == REQ_BACKGROUND) {
            HomeGeofence.register(activity)
            refresh()
        }
    }

    private fun setHomeHere() {
        if (!HomeGeofence.hasForeground(activity)) {
            activity.requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQ_LOCATION,
            )
            return
        }
        status.text = "場所を調べています…"
        HomeGeofence.locate(activity, { lat, lon, acc ->
            prefs.setHome(lat, lon)
            HomeGeofence.register(activity) { ok ->
                refresh()
                val note = if (acc > 100) "\n（誤差が大きめ：約${acc.toInt()}m。窓の近くでもう一度押すと正確になります）" else ""
                status.text = (if (ok) "✅ おうちを登録しました！" else "おうちを登録しました。「常に許可」にすると見守りを始めます") + note
            }
        }, { msg -> status.text = "⚠️ $msg" })
    }

    private fun test(entered: Boolean) {
        HomeGeofence.onTransition(activity, entered, test = true)
        if (OverlayService.isRunning) {
            activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun lp() = LinearLayout.LayoutParams(-1, px(44)).apply { topMargin = px(8) }

    private fun button(t: String, onClick: () -> Unit) = Button(activity).apply {
        text = t
        textSize = 13f
        setTextColor(activity.getColor(R.color.accent))
        setBackgroundResource(R.drawable.button_sub_bg)
        setOnClickListener { onClick() }
    }

    private fun check(t: String, initial: Boolean, onChange: (Boolean) -> Unit) = CheckBox(activity).apply {
        text = t
        textSize = 15f
        setTextColor(activity.getColor(R.color.ink))
        isChecked = initial
        setOnCheckedChangeListener { _, c -> onChange(c) }
    }

    companion object {
        const val REQ_LOCATION = 20
        const val REQ_BACKGROUND = 21
    }
}
