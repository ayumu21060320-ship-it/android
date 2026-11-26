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

    /** 通知を教えてもらうアプリ(パッケージ名) */
    var announceApps: Set<String>
        get() = sp.getStringSet("announce_apps", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("announce_apps", v).apply()

    /** 通知の本文まで読み上げるか(オフなら「誰から」だけ) */
    var announceBody: Boolean
        get() = sp.getBoolean("announce_body", false)
        set(v) = sp.edit().putBoolean("announce_body", v).apply()

    /** 支払い通知を家計簿に自動記録するアプリ */
    var paymentApps: Set<String>
        get() = sp.getStringSet("payment_apps", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("payment_apps", v).apply()

    /** 通知をホーム画面の小さなカードで出すか(オフなら吹き出しだけ) */
    var notifyCard: Boolean
        get() = sp.getBoolean("notify_card", true)
        set(v) = sp.edit().putBoolean("notify_card", v).apply()

    /** 電池が少なくなったら教えてもらうか */
    var batteryTalk: Boolean
        get() = sp.getBoolean("battery_talk", true)
        set(v) = sp.edit().putBoolean("battery_talk", v).apply()

    /** 勝手にふわふわ動き回るか */
    var driftEnabled: Boolean
        get() = sp.getBoolean("drift", true)
        set(value) = sp.edit().putBoolean("drift", value).apply()

    /** 天気を調べる街。未設定なら null。 */
    val cityName: String? get() = sp.getString("city_name", null)
    val cityLat: Double? get() = sp.getString("city_lat", null)?.toDoubleOrNull()
    val cityLon: Double? get() = sp.getString("city_lon", null)?.toDoubleOrNull()

    fun setCity(city: City) {
        sp.edit()
            .putString("city_name", city.name)
            .putString("city_lat", city.lat.toString())
            .putString("city_lon", city.lon.toString())
            .remove("weather_json")
            .apply()
    }

    /** 最後に取った天気(保存した時刻, JSON) */
    var weatherCache: Pair<Long, String>?
        get() {
            val json = sp.getString("weather_json", null) ?: return null
            return sp.getLong("weather_at", 0L) to json
        }
        set(value) {
            if (value == null) sp.edit().remove("weather_json").apply()
            else sp.edit().putLong("weather_at", value.first).putString("weather_json", value.second).apply()
        }

    /** ホーム画面上の位置。UNSET は「まだ決まっていない」(画面の端では負の値にもなる)。 */
    var overlayX: Int
        get() = sp.getInt("overlay_x", UNSET)
        set(value) = sp.edit().putInt("overlay_x", value).apply()

    var overlayY: Int
        get() = sp.getInt("overlay_y", UNSET)
        set(value) = sp.edit().putInt("overlay_y", value).apply()

    companion object {
        const val DEFAULT_NAME = "ご主人様"
        const val UNSET = Int.MIN_VALUE
    }
}
