package com.mochimate.maid

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** 今日の天気(Open-Meteo から取得。APIキー不要・無料) */
data class Weather(
    val code: Int,
    val temp: Double,
    val max: Double,
    val min: Double,
    val rainChance: Int,
) {
    val label: String
        get() = when (code) {
            0 -> "快晴☀️"
            1 -> "晴れ☀️"
            2 -> "晴れときどきくもり⛅"
            3 -> "くもり☁️"
            45, 48 -> "霧🌫️"
            in 51..57 -> "霧雨🌦️"
            in 61..67, in 80..82 -> "雨☔"
            in 71..77, 85, 86 -> "雪⛄"
            in 95..99 -> "雷雨⛈️"
            else -> "よくわからないお天気"
        }

    val isRainy: Boolean get() = code in 51..67 || code in 80..82 || code >= 95
    val isSnowy: Boolean get() = code in 71..77 || code == 85 || code == 86
}

/** 街の検索結果 */
data class City(val name: String, val detail: String, val lat: Double, val lon: Double)

object WeatherRepository {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private const val CACHE_MS = 30 * 60 * 1000L

    /** 天気を取得する。30分以内に取ったものがあればそれを使う。失敗したら null。 */
    fun today(prefs: Prefs, force: Boolean = false, callback: (Weather?) -> Unit) {
        val lat = prefs.cityLat ?: return callback(null)
        val lon = prefs.cityLon ?: return callback(null)
        if (!force) {
            prefs.weatherCache?.let { (savedAt, json) ->
                if (System.currentTimeMillis() - savedAt < CACHE_MS) {
                    parseWeather(json)?.let { return callback(it) }
                }
            }
        }
        io.execute {
            val url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                    "&current=temperature_2m,weather_code" +
                    "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                    "&timezone=auto&forecast_days=1",
                lat, lon,
            )
            val json = runCatching { get(url) }.getOrNull()
            val weather = json?.let { parseWeather(it) }
            if (weather != null) prefs.weatherCache = System.currentTimeMillis() to json
            main.post { callback(weather) }
        }
    }

    /** 街の名前から場所を探す(日本語OK)。失敗したら空のリスト。 */
    fun searchCity(query: String, callback: (List<City>) -> Unit) {
        io.execute {
            val url = "https://geocoding-api.open-meteo.com/v1/search?count=6&language=ja&format=json&name=" +
                URLEncoder.encode(query, "UTF-8")
            val result = runCatching {
                val arr = JSONObject(get(url)).optJSONArray("results")
                (0 until (arr?.length() ?: 0)).map { i ->
                    val o = arr!!.getJSONObject(i)
                    City(
                        name = o.getString("name"),
                        detail = listOf(o.optString("admin1"), o.optString("country"))
                            .filter { it.isNotBlank() }.joinToString("・"),
                        lat = o.getDouble("latitude"),
                        lon = o.getDouble("longitude"),
                    )
                }
            }.getOrDefault(emptyList())
            main.post { callback(result) }
        }
    }

    private fun parseWeather(json: String): Weather? = runCatching {
        val o = JSONObject(json)
        val cur = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        Weather(
            code = cur.getInt("weather_code"),
            temp = cur.getDouble("temperature_2m"),
            max = daily.getJSONArray("temperature_2m_max").getDouble(0),
            min = daily.getJSONArray("temperature_2m_min").getDouble(0),
            rainChance = daily.getJSONArray("precipitation_probability_max").optInt(0, 0),
        )
    }.getOrNull()

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun Double.c() = "${roundToInt()}℃"

    /** もちもちが天気を伝えるセリフ */
    fun line(w: Weather?, prefs: Prefs): String {
        val name = prefs.masterName
        val city = prefs.cityName
        if (city == null) return "${name}、お天気を教えるには、設定で住んでいる街を選んでくださいね！"
        if (w == null) return "ごめんなさい、${name}…お天気がうまく調べられませんでした。電波が届いているか見てみてください。"

        val head = "今日の${city}は${w.label}、いまは${w.temp.c()}です。最高${w.max.c()}・最低${w.min.c()}みたいですよ。"
        val advice = when {
            w.isSnowy -> "雪が降るかも！足元に気をつけて、あったかくしてくださいね。"
            w.rainChance >= 50 || w.isRainy -> "雨が降りそうなので、傘を忘れないでくださいね☂️"
            w.rainChance >= 30 -> "雨が降るかもしれないので、折りたたみ傘があると安心です。"
            w.max >= 30 -> "とっても暑いので、こまめにお水を飲んでくださいね。"
            w.min <= 5 -> "冷えこむので、あったかくしてお出かけしてくださいね。"
            w.max - w.min >= 10 -> "寒暖差が大きいので、羽織るものがあるといいですよ。"
            else -> "過ごしやすい一日になりそうです♪"
        }
        return "$head\n$advice"
    }
}
