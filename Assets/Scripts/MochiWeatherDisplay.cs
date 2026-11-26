using UnityEngine;
using UnityEngine.UI;

// WeatherServiceから天気情報を受け取り、Mochiの色と吹き出しのセリフに反映します。
public class MochiWeatherDisplay : MonoBehaviour
{
    [Header("参照")]
    [Tooltip("天気を取得してくるWeatherService")]
    [SerializeField] private WeatherService weatherService;

    [Tooltip("色を変えるMochi本体のRenderer")]
    [SerializeField] private Renderer mochiRenderer;

    [Tooltip("セリフを表示する吹き出しのText")]
    [SerializeField] private Text speechText;

    [Header("天気ごとの色")]
    [SerializeField] private Color clearColor = new Color(1f, 0.85f, 0.4f);
    [SerializeField] private Color cloudyColor = new Color(0.75f, 0.75f, 0.78f);
    [SerializeField] private Color rainColor = new Color(0.5f, 0.6f, 0.8f);
    [SerializeField] private Color snowColor = new Color(0.9f, 0.95f, 1f);
    [SerializeField] private Color thunderstormColor = new Color(0.4f, 0.35f, 0.5f);

    private void OnEnable()
    {
        if (weatherService != null)
        {
            weatherService.OnWeatherUpdated += HandleWeatherUpdated;
        }
    }

    private void OnDisable()
    {
        if (weatherService != null)
        {
            weatherService.OnWeatherUpdated -= HandleWeatherUpdated;
        }
    }

    private void HandleWeatherUpdated(float temperature, WeatherService.WeatherCategory category)
    {
        if (mochiRenderer != null)
        {
            mochiRenderer.material.color = GetColor(category);
        }

        if (speechText != null)
        {
            speechText.text = BuildMessage(temperature, category);
        }
    }

    private Color GetColor(WeatherService.WeatherCategory category)
    {
        switch (category)
        {
            case WeatherService.WeatherCategory.Clear: return clearColor;
            case WeatherService.WeatherCategory.Cloudy: return cloudyColor;
            case WeatherService.WeatherCategory.Rain: return rainColor;
            case WeatherService.WeatherCategory.Snow: return snowColor;
            case WeatherService.WeatherCategory.Thunderstorm: return thunderstormColor;
            default: return Color.white;
        }
    }

    private string BuildMessage(float temperature, WeatherService.WeatherCategory category)
    {
        string weatherPhrase;
        switch (category)
        {
            case WeatherService.WeatherCategory.Clear: weatherPhrase = "今日は晴れだよ"; break;
            case WeatherService.WeatherCategory.Cloudy: weatherPhrase = "今日は曇りだね"; break;
            case WeatherService.WeatherCategory.Rain: weatherPhrase = "雨が降ってるよ、傘持った?"; break;
            case WeatherService.WeatherCategory.Snow: weatherPhrase = "雪だ!あったかくしてね"; break;
            case WeatherService.WeatherCategory.Thunderstorm: weatherPhrase = "雷が鳴ってるから気をつけて"; break;
            default: weatherPhrase = "今日の天気はこんな感じだよ"; break;
        }

        return string.Format("{0}\n気温は{1:0.0}℃だよ", weatherPhrase, temperature);
    }
}
