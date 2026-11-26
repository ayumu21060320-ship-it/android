using UnityEngine;
using UnityEngine.UI;

// アプリ全体の流れを管理します。
// 1. 起動直後は「何をお世話してほしいか」を選ぶ画面だけ表示(天気・健康管理など)
// 2. 選んだ項目の入力欄だけが表示される
// 3. 下部の「はじめる」ボタンを押すとキャラが登場し、以後はこれらの画面は隠れる
// 4. キャラをダブルタップすると、カテゴリー選択画面ごと再び開く。
//    ここでチェックを付け外しして「つぎへ」を押せば、項目の追加・削除も後からできる。
public class MochiAppController : MonoBehaviour
{
    [Header("お世話カテゴリーを選ぶ画面(最初に出る)")]
    [SerializeField] private GameObject categoryPanel;
    [SerializeField] private Toggle wantWeatherToggle;
    [SerializeField] private Toggle wantHealthToggle;
    [SerializeField] private Toggle wantOverlayToggle;
    [SerializeField] private Button categoryNextButton;

    [Header("ホーム画面オーバーレイ(Android実機のみ)")]
    [SerializeField] private AndroidOverlayBridge overlayBridge;

    [Header("選んだ項目ごとの入力画面")]
    [Tooltip("天気を選んだ時だけ表示するもの(場所の検索欄など)")]
    [SerializeField] private GameObject[] weatherSettingsObjects = new GameObject[0];

    [Tooltip("健康管理を選んだ時だけ表示するもの(体重・食事ログなど)")]
    [SerializeField] private GameObject[] healthSettingsObjects = new GameObject[0];

    [Tooltip("天気の取得処理そのもの(選ばなかった場合は止めておく)")]
    [SerializeField] private WeatherService weatherService;

    [Header("入力が終わったら押す「はじめる」バー")]
    [SerializeField] private GameObject okBarRoot;
    [SerializeField] private Button okButton;

    [Header("キャラが登場してから表示するもの")]
    [Tooltip("お世話されるキャラ本体(Mochi)。起動直後は隠しておきます。")]
    [SerializeField] private GameObject mochiRoot;

    [Tooltip("キャラのMochiBody。ダブルタップの検知に使います。")]
    [SerializeField] private MochiBody mochiBody;

    [Tooltip("セリフの吹き出し本体。キャラ登場時に一度だけ挨拶として表示します(以後はタップで開閉)")]
    [SerializeField] private GameObject speechBubbleRoot;

    [Tooltip("キャラ登場時に最初にしゃべらせるセリフの表示先")]
    [SerializeField] private Text speechText;

    private const string OnboardedKey = "Mochi_HasOnboarded";
    private const string WantWeatherKey = "Mochi_WantWeather";
    private const string WantHealthKey = "Mochi_WantHealth";
    private const string WantOverlayKey = "Mochi_WantOverlay";

    private bool wantWeather = true;
    private bool wantHealth = true;
    private bool wantOverlay;

    private void Awake()
    {
        if (categoryNextButton != null) categoryNextButton.onClick.AddListener(OnCategoryNextClicked);
        if (okButton != null) okButton.onClick.AddListener(OnOkClicked);

        if (mochiBody != null)
        {
            mochiBody.OnDoubleTapped += OnMochiDoubleTapped;
        }
    }

    private void OnDestroy()
    {
        if (mochiBody != null)
        {
            mochiBody.OnDoubleTapped -= OnMochiDoubleTapped;
        }
    }

    private void Start()
    {
        bool hasOnboarded = PlayerPrefs.GetInt(OnboardedKey, 0) == 1;
        wantWeather = PlayerPrefs.GetInt(WantWeatherKey, 1) == 1;
        wantHealth = PlayerPrefs.GetInt(WantHealthKey, 1) == 1;
        wantOverlay = PlayerPrefs.GetInt(WantOverlayKey, 0) == 1;

        if (hasOnboarded)
        {
            ApplyCategoryVisibility(false);
            SetActive(categoryPanel, false);
            SetActive(okBarRoot, false);
            SetActive(mochiRoot, true);
            if (weatherService != null) weatherService.enabled = wantWeather;

            if (wantOverlay && overlayBridge != null)
            {
                overlayBridge.StartOverlay();
            }
        }
        else
        {
            SetActive(categoryPanel, true);
            ApplyCategoryVisibility(false);
            SetActive(okBarRoot, false);
            SetActive(mochiRoot, false);
            SetActive(speechBubbleRoot, false);

            if (wantWeatherToggle != null) wantWeatherToggle.isOn = wantWeather;
            if (wantHealthToggle != null) wantHealthToggle.isOn = wantHealth;
            if (wantOverlayToggle != null) wantOverlayToggle.isOn = wantOverlay;
        }
    }

    private void OnCategoryNextClicked()
    {
        wantWeather = wantWeatherToggle == null || wantWeatherToggle.isOn;
        wantHealth = wantHealthToggle == null || wantHealthToggle.isOn;
        wantOverlay = wantOverlayToggle != null && wantOverlayToggle.isOn;

        PlayerPrefs.SetInt(WantWeatherKey, wantWeather ? 1 : 0);
        PlayerPrefs.SetInt(WantHealthKey, wantHealth ? 1 : 0);
        PlayerPrefs.SetInt(WantOverlayKey, wantOverlay ? 1 : 0);
        PlayerPrefs.Save();

        if (weatherService != null) weatherService.enabled = wantWeather;

        SetActive(categoryPanel, false);
        ApplyCategoryVisibility(true);
        SetActive(okBarRoot, true);
    }

    private void OnOkClicked()
    {
        PlayerPrefs.SetInt(OnboardedKey, 1);
        PlayerPrefs.Save();

        ApplyCategoryVisibility(false);
        SetActive(okBarRoot, false);

        SetActive(mochiRoot, true);

        if (speechText != null)
        {
            speechText.text = "はじめまして!今日からお世話するね!";
        }
        SetActive(speechBubbleRoot, true);

        ApplyOverlayPreference();
    }

    private void ApplyOverlayPreference()
    {
        if (overlayBridge == null) return;

        if (!wantOverlay)
        {
            overlayBridge.StopOverlay();
            return;
        }

        if (overlayBridge.CanDrawOverlays())
        {
            overlayBridge.StartOverlay();
        }
        else
        {
            // 許可が必要なので設定画面を開く。許可後にもう一度「はじめる」を押すとオーバーレイが起動する。
            overlayBridge.RequestOverlayPermission();
        }
    }

    private void OnMochiDoubleTapped()
    {
        bool currentlyOpen = okBarRoot != null && okBarRoot.activeSelf;

        if (currentlyOpen)
        {
            SetActive(categoryPanel, false);
            ApplyCategoryVisibility(false);
            SetActive(okBarRoot, false);
        }
        else
        {
            // 今選んでいる項目を反映した状態で、カテゴリー選択からやり直せるようにする
            if (wantWeatherToggle != null) wantWeatherToggle.isOn = wantWeather;
            if (wantHealthToggle != null) wantHealthToggle.isOn = wantHealth;
            if (wantOverlayToggle != null) wantOverlayToggle.isOn = wantOverlay;

            SetActive(categoryPanel, true);
            ApplyCategoryVisibility(true);
            SetActive(okBarRoot, true);
        }
    }

    private void ApplyCategoryVisibility(bool visible)
    {
        SetActiveAll(weatherSettingsObjects, visible && wantWeather);
        SetActiveAll(healthSettingsObjects, visible && wantHealth);
    }

    private static void SetActive(GameObject go, bool active)
    {
        if (go != null) go.SetActive(active);
    }

    private static void SetActiveAll(GameObject[] objects, bool active)
    {
        foreach (GameObject go in objects)
        {
            SetActive(go, active);
        }
    }
}
