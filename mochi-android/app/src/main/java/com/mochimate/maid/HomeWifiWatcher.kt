package com.mochimate.maid

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * 家の Wi-Fi につながった/切れた、で「おかえり」「いってらっしゃい」を言う。
 * インターネットやモバイルデータは使わない(Wi-Fi につながっているかどうかだけを見る)。
 * メイドさんがホーム画面にいる間(OverlayService が動いている間)だけ見張る。
 */
class HomeWifiWatcher(private val context: Context) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val prefs = Prefs(context)

    /** つながっている Wi-Fi ごとの名前(切れた時に、それが家の Wi-Fi だったかを知るため) */
    private val ssidOf = HashMap<Network, String>()
    private var started = false

    private val callback = object : ConnectivityManager.NetworkCallback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) FLAG_INCLUDE_LOCATION_INFO else 0,
    ) {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val ssid = runCatching { ssidFrom(caps) }.getOrNull() ?: return
            val first = ssidOf.put(network, ssid) == null
            if (first && isHome(ssid)) main.post {
                // 家の Wi-Fi にいる間に天気を取っておく(外ではネットが使えないため)
                WeatherRepository.today(prefs) { }
                HomeGeofence.onTransition(context, entered = true, source = "wifi")
            }
        }

        override fun onLost(network: Network) {
            val ssid = ssidOf.remove(network) ?: return
            if (!isHome(ssid)) return
            main.postDelayed({
                // Wi-Fi そのものを自分でオフにした時は、出かけたわけではないので言わない
                if (wifi?.isWifiEnabled == false) return@postDelayed
                // すぐにつなぎ直された(ルーターの一瞬の切れなど)時も言わない
                if (ssidOf.values.any { isHome(it) }) return@postDelayed
                HomeGeofence.onTransition(context, entered = false, source = "wifi")
            }, 8_000)
        }
    }

    fun start() {
        if (started || prefs.homeWifi == null) return
        val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        runCatching { cm.registerNetworkCallback(req, callback) }.onSuccess { started = true }
    }

    fun stop() {
        if (!started) return
        runCatching { cm.unregisterNetworkCallback(callback) }
        started = false
        ssidOf.clear()
    }

    private fun isHome(ssid: String) = prefs.homeEnabled && ssid == prefs.homeWifi

    private fun ssidFrom(caps: NetworkCapabilities): String? {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) caps.transportInfo as? WifiInfo else null
        @Suppress("DEPRECATION")
        val raw = info?.ssid ?: wifi?.connectionInfo?.ssid
        return clean(raw)
    }

    companion object {
        /** 今つながっている Wi-Fi の名前(わからなければ null) */
        fun currentSsid(context: Context): String? = runCatching { readSsid(context) }.getOrNull()

        private fun readSsid(context: Context): String? {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val fromCaps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) (caps?.transportInfo as? WifiInfo)?.ssid else null
            @Suppress("DEPRECATION")
            val raw = fromCaps ?: context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid
            return clean(raw)
        }

        private fun clean(raw: String?): String? {
            val s = raw?.trim()?.removeSurrounding("\"") ?: return null
            if (s.isEmpty() || s == WifiManager.UNKNOWN_SSID.removeSurrounding("<", ">") || s == "<unknown ssid>") return null
            return s
        }
    }
}
