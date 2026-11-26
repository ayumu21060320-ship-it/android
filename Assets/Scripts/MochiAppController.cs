using UnityEngine;
using UnityEngine.UI;

// アプリ全体の流れを管理します。
// 1. 起動直後はキャラは出さず、設定パネル(天気の場所・健康プロフィール)だけ表示
// 2. 「OK」ボタンを押すとキャラが登場し、以後は設定パネルは隠れる
// 3. キャラをダブルタップすると、設定パネルを開閉できる
public class MochiAppController : MonoBehaviour
{
    [Header("参照")]
    [Tooltip("お世話されるキャラ本体(Mochi)。起動直後は隠しておきます。")]
    [SerializeField] private GameObject mochiRoot;

    [Tooltip("キャラのMochiBody。ダブルタップの検知に使います。")]
    [SerializeField] private MochiBody mochiBody;

    [Tooltip("キャラが登場してから常に表示するもの(セリフの吹き出しなど)")]
    [SerializeField] private GameObject[] activeOnlyObjects = new GameObject[0];

    [Tooltip("最初の設定・あとからの編集で表示するパネル(天気の場所・健康プロフィールなど)")]
    [SerializeField] private GameObject[] settingsPanels = new GameObject[0];

    [Tooltip("設定パネルの「OK」ボタン")]
    [SerializeField] private Button okButton;

    [Tooltip("キャラ登場時に最初にしゃべらせるセリフの表示先")]
    [SerializeField] private Text speechText;

    private const string OnboardedKey = "Mochi_HasOnboarded";

    private void Awake()
    {
        if (okButton != null)
        {
            okButton.onClick.AddListener(OnOkClicked);
        }

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

        SetActive(mochiRoot, hasOnboarded);
        SetActiveAll(activeOnlyObjects, hasOnboarded);
        SetActiveAll(settingsPanels, !hasOnboarded);
    }

    private void OnOkClicked()
    {
        PlayerPrefs.SetInt(OnboardedKey, 1);
        PlayerPrefs.Save();

        SetActive(mochiRoot, true);
        SetActiveAll(activeOnlyObjects, true);
        SetActiveAll(settingsPanels, false);

        if (speechText != null)
        {
            speechText.text = "はじめまして!今日からお世話するね!";
        }
    }

    private void OnMochiDoubleTapped()
    {
        bool currentlyVisible = settingsPanels.Length > 0 && settingsPanels[0] != null && settingsPanels[0].activeSelf;
        SetActiveAll(settingsPanels, !currentlyVisible);
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
