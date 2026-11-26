using System;
using System.Collections;
using System.Globalization;
using UnityEngine;
using UnityEngine.Networking;

// Open-Meteo(APIキー不要の無料天気API)から現在の気温・天気を定期的に取得します。
// 取得できたら OnWeatherUpdated イベントで他のスクリプトに知らせるだけで、
// 見た目やセリフの処理はこのスクリプトでは行いません(MochiWeatherDisplayが担当)。
public class WeatherService : MonoBehaviour
{
    public enum WeatherCategory
    {
        Clear,
        Cloudy,
        Rain,
        Snow,
        Thunderstorm
    }

    [Header("場所(緯度・経度)")]
    [Tooltip("デフォルトの緯度。LocationSettingsで場所を選んでいればそちらが優先されます。例: 東京は35.6762")]
    [SerializeField] private float latitude = 35.6762f;

    [Tooltip("デフォルトの経度。LocationSettingsで場所を選んでいればそちらが優先されます。例: 東京は139.6503")]
    [SerializeField] private float longitude = 139.6503f;

    [Header("更新間隔")]
    [Tooltip("何秒おきに天気を取得し直すか")]
    [SerializeField] private float refreshIntervalSeconds = 1800f;

    public event Action<float, WeatherCategory> OnWeatherUpdated;

    private const string LatitudePrefKey = "Mochi_Weather_Latitude";
    private const string LongitudePrefKey = "Mochi_Weather_Longitude";

    [Serializable]
    private class CurrentWeather
    {
        public float temperature_2m;
        public int weather_code;
    }

    [Serializable]
    private class OpenMeteoResponse
    {
        public CurrentWeather current;
    }

    private void Start()
    {
        if (PlayerPrefs.HasKey(LatitudePrefKey) && PlayerPrefs.HasKey(LongitudePrefKey))
        {
            latitude = PlayerPrefs.GetFloat(LatitudePrefKey);
            longitude = PlayerPrefs.GetFloat(LongitudePrefKey);
        }

        StartCoroutine(RefreshLoop());
    }

    // LocationSettingsなど、場所を選ぶUIから呼び出します。
    // 次回以降も使えるよう保存し、すぐに最新の天気を取得し直します。
    public void SetLocation(float newLatitude, float newLongitude)
    {
        latitude = newLatitude;
        longitude = newLongitude;

        PlayerPrefs.SetFloat(LatitudePrefKey, latitude);
        PlayerPrefs.SetFloat(LongitudePrefKey, longitude);
        PlayerPrefs.Save();

        StopAllCoroutines();
        StartCoroutine(RefreshLoop());
    }

    private IEnumerator RefreshLoop()
    {
        while (true)
        {
            yield return StartCoroutine(FetchWeather());
            yield return new WaitForSeconds(refreshIntervalSeconds);
        }
    }

    private IEnumerator FetchWeather()
    {
        string url = string.Format(
            CultureInfo.InvariantCulture,
            "https://api.open-meteo.com/v1/forecast?latitude={0}&longitude={1}&current=temperature_2m,weather_code",
            latitude,
            longitude);

        using (UnityWebRequest request = UnityWebRequest.Get(url))
        {
            yield return request.SendWebRequest();

            if (request.result != UnityWebRequest.Result.Success)
            {
                Debug.LogWarning("天気の取得に失敗しました: " + request.error);
                yield break;
            }

            OpenMeteoResponse response = JsonUtility.FromJson<OpenMeteoResponse>(request.downloadHandler.text);
            if (response == null || response.current == null)
            {
                Debug.LogWarning("天気データの形式が想定と違いました: " + request.downloadHandler.text);
                yield break;
            }

            WeatherCategory category = ToCategory(response.current.weather_code);
            OnWeatherUpdated?.Invoke(response.current.temperature_2m, category);
        }
    }

    // WMO weather_codeをざっくり5分類にまとめる
    private static WeatherCategory ToCategory(int code)
    {
        if (code == 0 || code == 1) return WeatherCategory.Clear;
        if (code == 2 || code == 3 || code == 45 || code == 48) return WeatherCategory.Cloudy;
        if (code >= 95) return WeatherCategory.Thunderstorm;
        if (code == 71 || code == 73 || code == 75 || code == 77 || code == 85 || code == 86) return WeatherCategory.Snow;
        return WeatherCategory.Rain;
    }
}
