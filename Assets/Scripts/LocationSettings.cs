using System;
using System.Collections;
using UnityEngine;
using UnityEngine.Networking;
using UnityEngine.UI;

// 街の名前を入力すると、Open-Meteoのジオコーディング(無料・APIキー不要)で
// 緯度経度を調べて、WeatherServiceに反映します。選んだ場所は端末に保存されます。
public class LocationSettings : MonoBehaviour
{
    [Header("参照")]
    [Tooltip("場所を反映するWeatherService")]
    [SerializeField] private WeatherService weatherService;

    [Tooltip("街の名前を入力するフィールド")]
    [SerializeField] private InputField cityInputField;

    [Tooltip("検索を実行するボタン")]
    [SerializeField] private Button searchButton;

    [Tooltip("検索結果・エラーを表示するテキスト")]
    [SerializeField] private Text statusText;

    [Serializable]
    private class GeocodingResult
    {
        public float latitude;
        public float longitude;
        public string name;
        public string country;
    }

    [Serializable]
    private class GeocodingResponse
    {
        public GeocodingResult[] results;
    }

    private void Awake()
    {
        if (searchButton != null)
        {
            searchButton.onClick.AddListener(OnSearchClicked);
        }
    }

    private void OnSearchClicked()
    {
        if (cityInputField == null || string.IsNullOrWhiteSpace(cityInputField.text)) return;
        StartCoroutine(SearchCity(cityInputField.text));
    }

    private IEnumerator SearchCity(string cityName)
    {
        SetStatus("検索中...");

        string encodedName = Uri.EscapeDataString(cityName);
        string url = "https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + encodedName;

        using (UnityWebRequest request = UnityWebRequest.Get(url))
        {
            yield return request.SendWebRequest();

            if (request.result != UnityWebRequest.Result.Success)
            {
                SetStatus("検索に失敗しました");
                yield break;
            }

            GeocodingResponse response = JsonUtility.FromJson<GeocodingResponse>(request.downloadHandler.text);
            if (response == null || response.results == null || response.results.Length == 0)
            {
                SetStatus("見つかりませんでした: " + cityName);
                yield break;
            }

            GeocodingResult result = response.results[0];
            if (weatherService != null)
            {
                weatherService.SetLocation(result.latitude, result.longitude);
            }

            SetStatus(string.Format("場所を{0}({1})に設定しました", result.name, result.country));
        }
    }

    private void SetStatus(string message)
    {
        if (statusText != null)
        {
            statusText.text = message;
        }
    }
}
