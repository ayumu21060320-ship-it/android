package com.mochimate.maid

import android.content.Context

/** アプリの設定(呼び方・お世話の種類・もちもちの位置)をスマホに保存する。 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("mochi_prefs", Context.MODE_PRIVATE)

    var masterName: String
        get() = sp.getString("master_name", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME
        set(value) = sp.edit().putString("master_name", value.trim()).apply()

    var careWeather: Boolean
        get() = sp.getBoolean("care_weather", true)
        set(value) = sp.edit().putBoolean("care_weather", value).apply()

    var careCalorie: Boolean
        get() = sp.getBoolean("care_calorie", false)
        set(value) = sp.edit().putBoolean("care_calorie", value).apply()

    var careMoney: Boolean
        get() = sp.getBoolean("care_money", false)
        set(value) = sp.edit().putBoolean("care_money", value).apply()

    /** ホーム画面上の位置。-1 は「まだ決まっていない」。 */
    var overlayX: Int
        get() = sp.getInt("overlay_x", -1)
        set(value) = sp.edit().putInt("overlay_x", value).apply()

    var overlayY: Int
        get() = sp.getInt("overlay_y", -1)
        set(value) = sp.edit().putInt("overlay_y", value).apply()

    companion object {
        const val DEFAULT_NAME = "ご主人様"
    }
}
