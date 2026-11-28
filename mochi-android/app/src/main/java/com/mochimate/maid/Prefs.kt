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

    /** 体重のお世話(ホームに体重のカード、メイドさんが体重の話をする) */
    var careWeight: Boolean
        get() = sp.getBoolean("care_weight", true)
        set(value) = sp.edit().putBoolean("care_weight", value).apply()

    /** きょうの調子のお世話(朝に調子を聞く、ホームにカード) */
    var careCondition: Boolean
        get() = sp.getBoolean("care_condition", true)
        set(value) = sp.edit().putBoolean("care_condition", value).apply()

    /** 最後に朝の調子を聞いた日(yyyy-MM-dd) */
    var lastConditionAsk: String?
        get() = sp.getString("condition_ask", null)
        set(value) = sp.edit().putString("condition_ask", value).apply()

    /** ゴミの日のお世話 */
    var careGarbage: Boolean
        get() = sp.getBoolean("care_garbage", true)
        set(value) = sp.edit().putBoolean("care_garbage", value).apply()

    /** 集中タイマー(ホームにカード) */
    var careFocus: Boolean
        get() = sp.getBoolean("care_focus", true)
        set(value) = sp.edit().putBoolean("care_focus", value).apply()

    /** やることリスト */
    var careTodo: Boolean
        get() = sp.getBoolean("care_todo", true)
        set(value) = sp.edit().putBoolean("care_todo", value).apply()

    /** 予定 */
    var careSchedule: Boolean
        get() = sp.getBoolean("care_schedule", true)
        set(value) = sp.edit().putBoolean("care_schedule", value).apply()

    /** スマホの使いすぎチェック */
    var careScreenTime: Boolean
        get() = sp.getBoolean("care_screen", true)
        set(value) = sp.edit().putBoolean("care_screen", value).apply()

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

    /** メイドさんが知らせたら、元のアプリの通知は通知バーから消すか */
    var takeOver: Boolean
        get() = sp.getBoolean("take_over", true)
        set(v) = sp.edit().putBoolean("take_over", v).apply()

    /** お知らせを、吹き出しではなく画面の上のカードで出すか */
    var cardAtTop: Boolean
        get() = sp.getBoolean("card_at_top", false)
        set(v) = sp.edit().putBoolean("card_at_top", v).apply()

    /** 電池が少なくなったら教えてもらうか */
    var batteryTalk: Boolean
        get() = sp.getBoolean("battery_talk", true)
        set(v) = sp.edit().putBoolean("battery_talk", v).apply()

    // ---------- おうち ----------

    /** おかえり・いってらっしゃいを使うか */
    var homeEnabled: Boolean
        get() = sp.getBoolean("home_enabled", false)
        set(v) = sp.edit().putBoolean("home_enabled", v).apply()

    val homeLat: Double? get() = sp.getString("home_lat", null)?.toDoubleOrNull()
    val homeLon: Double? get() = sp.getString("home_lon", null)?.toDoubleOrNull()

    fun setHome(lat: Double, lon: Double) {
        sp.edit().putString("home_lat", lat.toString()).putString("home_lon", lon.toString()).apply()
    }

    /** おうちとみなす範囲(メートル) */
    var homeRadius: Int
        get() = sp.getInt("home_radius", 150)
        set(v) = sp.edit().putInt("home_radius", v).apply()

    /** 出かける時に「いってらっしゃい」も言うか */
    var homeSayGoodbye: Boolean
        get() = sp.getBoolean("home_goodbye", true)
        set(v) = sp.edit().putBoolean("home_goodbye", v).apply()

    var lastHomeEntered: Boolean
        get() = sp.getBoolean("home_last_in", true)
        set(v) = sp.edit().putBoolean("home_last_in", v).apply()

    /** 家の Wi-Fi の名前(未設定なら null) */
    var homeWifi: String?
        get() = sp.getString("home_wifi", null)
        set(v) = sp.edit().putString("home_wifi", v).apply()

    var lastHomeSource: String
        get() = sp.getString("home_last_src", "gps")!!
        set(v) = sp.edit().putString("home_last_src", v).apply()

    var lastHomeEventAt: Long
        get() = sp.getLong("home_last_at", 0L)
        set(v) = sp.edit().putLong("home_last_at", v).apply()

    /** 最後に夜のごはんのふり返りをした日(yyyy-MM-dd) */
    var lastDietReview: String?
        get() = sp.getString("diet_review", null)
        set(v) = sp.edit().putString("diet_review", v).apply()

    /** ロック画面を見た時にもメイドさんが出てくるか */
    var lockScreenMaid: Boolean
        get() = sp.getBoolean("lock_maid", true)
        set(value) = sp.edit().putBoolean("lock_maid", value).apply()

    /** リリと話すための Anthropic の API キー(スマホの中だけに保存) */
    var anthropicKey: String?
        get() = sp.getString("anthropic_key", null)
        set(v) = sp.edit().putString("anthropic_key", v?.trim()).apply()

    /** ほかのアプリが全画面の時は隠れるか */
    var hideInFullscreen: Boolean
        get() = sp.getBoolean("hide_fullscreen", true)
        set(value) = sp.edit().putBoolean("hide_fullscreen", value).apply()

    /** はじめての会話(お世話の提案)を済ませたか */
    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(value) = sp.edit().putBoolean("onboarded", value).apply()

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
