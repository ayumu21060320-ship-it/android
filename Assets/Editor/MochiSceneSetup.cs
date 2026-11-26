using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

// Unityエディタのメニュー「Mochi > Setup Scene」から実行する、
// Mochiキャラのシーン構成(カメラ設定・本体・顔・スクリプトのアタッチ)を自動で行うツールです。
public static class MochiSceneSetup
{
    [MenuItem("Mochi/Setup Scene")]
    public static void SetupScene()
    {
        EnsureEventSystem();
        Camera cam = EnsureCamera();
        Material mochiMat = EnsureMochiMaterial();
        GameObject mochi = EnsureMochiBody(mochiMat);
        GameObject face = EnsureFace(mochi);
        EnsureVisor(mochi);
        GameObject[] limbTransforms = EnsureLimbs(mochi, mochiMat);
        AttachScripts(mochi, face, limbTransforms);

        Text speechText = EnsureSpeechCanvas();
        WeatherService weatherService = EnsureWeatherComponents(mochi, speechText);
        EnsureLocationUI(weatherService);

        Selection.activeGameObject = mochi;
        EditorSceneManager.MarkSceneDirty(EditorSceneManager.GetActiveScene());

        Debug.Log("Mochiシーンのセットアップが完了しました。Playボタンでつついて確認してください。");
    }

    private static void EnsureEventSystem()
    {
        if (Object.FindFirstObjectByType<EventSystem>() != null) return;

        var go = new GameObject("EventSystem", typeof(EventSystem), typeof(StandaloneInputModule));
        Undo.RegisterCreatedObjectUndo(go, "Create EventSystem");
    }

    private static Camera EnsureCamera()
    {
        Camera cam = Camera.main;
        if (cam == null)
        {
            var camGo = new GameObject("Main Camera", typeof(Camera));
            camGo.tag = "MainCamera";
            Undo.RegisterCreatedObjectUndo(camGo, "Create Main Camera");
            cam = camGo.GetComponent<Camera>();
        }

        cam.transform.position = new Vector3(0f, 0f, -5f);

        if (cam.GetComponent<PhysicsRaycaster>() == null)
        {
            Undo.AddComponent<PhysicsRaycaster>(cam.gameObject);
        }

        return cam;
    }

    private static Material EnsureMochiMaterial()
    {
        const string dir = "Assets/Materials";
        const string path = dir + "/Mochi_Mat.mat";

        if (!AssetDatabase.IsValidFolder(dir))
        {
            AssetDatabase.CreateFolder("Assets", "Materials");
        }

        Material mat = AssetDatabase.LoadAssetAtPath<Material>(path);
        if (mat != null) return mat;

        Shader shader = Shader.Find("Universal Render Pipeline/Lit");
        if (shader == null)
        {
            Debug.LogWarning("Universal Render Pipeline/Lit シェーダーが見つかりませんでした。デフォルトのマテリアルのままにします。");
            return null;
        }

        mat = new Material(shader) { color = new Color(1f, 0.937f, 0.835f) };
        AssetDatabase.CreateAsset(mat, path);
        return mat;
    }

    private static GameObject EnsureMochiBody(Material mat)
    {
        GameObject mochi = GameObject.Find("Mochi");
        if (mochi == null)
        {
            mochi = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            mochi.name = "Mochi";
            Undo.RegisterCreatedObjectUndo(mochi, "Create Mochi");
        }

        mochi.transform.position = Vector3.zero;
        mochi.transform.localScale = new Vector3(1f, 0.85f, 1f);

        if (mat != null)
        {
            mochi.GetComponent<Renderer>().sharedMaterial = mat;
        }

        return mochi;
    }

    private static GameObject EnsureFace(GameObject mochi)
    {
        Transform existing = mochi.transform.Find("Face");
        GameObject face;

        if (existing == null)
        {
            face = new GameObject("Face", typeof(SpriteRenderer));
            face.transform.SetParent(mochi.transform);
            Undo.RegisterCreatedObjectUndo(face, "Create Face");
        }
        else
        {
            face = existing.gameObject;
        }

        face.transform.localPosition = new Vector3(0f, 0f, -0.5f);
        face.transform.localScale = new Vector3(0.3f, 0.3f, 0.3f);

        var sr = face.GetComponent<SpriteRenderer>();
        if (sr.sprite == null)
        {
            // 顔のイラストを入れるまでの仮表示(Unity標準の円スプライト)
            sr.sprite = AssetDatabase.GetBuiltinExtraResource<Sprite>("UI/Skin/Knob.psd");
        }

        return face;
    }

    private static void EnsureVisor(GameObject mochi)
    {
        Transform existing = mochi.transform.Find("Visor");
        GameObject visor;

        if (existing == null)
        {
            visor = new GameObject("Visor", typeof(SpriteRenderer));
            visor.transform.SetParent(mochi.transform);
            Undo.RegisterCreatedObjectUndo(visor, "Create Visor");
        }
        else
        {
            visor = existing.gameObject;
        }

        // 顔(Face)のすぐ後ろに、少し大きい濃紺の円を置いてヘルメットのバイザー風に見せる
        visor.transform.localPosition = new Vector3(0f, 0f, -0.45f);
        visor.transform.localScale = new Vector3(0.4f, 0.4f, 0.4f);

        var sr = visor.GetComponent<SpriteRenderer>();
        if (sr.sprite == null)
        {
            sr.sprite = AssetDatabase.GetBuiltinExtraResource<Sprite>("UI/Skin/Knob.psd");
        }
        sr.color = new Color(0.08f, 0.1f, 0.2f);
    }

    private static GameObject EnsureLimb(GameObject mochi, Material mat, string name, Vector3 localPos, Vector3 localEuler, Vector3 localScale)
    {
        Transform existing = mochi.transform.Find(name);
        GameObject limb;

        if (existing == null)
        {
            limb = GameObject.CreatePrimitive(PrimitiveType.Capsule);
            limb.name = name;
            limb.transform.SetParent(mochi.transform);
            Undo.RegisterCreatedObjectUndo(limb, "Create " + name);
        }
        else
        {
            limb = existing.gameObject;
        }

        limb.transform.localPosition = localPos;
        limb.transform.localRotation = Quaternion.Euler(localEuler);
        limb.transform.localScale = localScale;

        if (mat != null)
        {
            limb.GetComponent<Renderer>().sharedMaterial = mat;
        }

        return limb;
    }

    private static GameObject[] EnsureLimbs(GameObject mochi, Material mat)
    {
        GameObject armLeft = EnsureLimb(mochi, mat, "ArmLeft",
            new Vector3(-0.55f, 0.05f, 0f), new Vector3(0f, 0f, 70f), new Vector3(0.18f, 0.35f, 0.18f));
        GameObject armRight = EnsureLimb(mochi, mat, "ArmRight",
            new Vector3(0.55f, 0.05f, 0f), new Vector3(0f, 0f, -70f), new Vector3(0.18f, 0.35f, 0.18f));
        GameObject legLeft = EnsureLimb(mochi, mat, "LegLeft",
            new Vector3(-0.25f, -0.55f, 0f), new Vector3(0f, 0f, 15f), new Vector3(0.18f, 0.35f, 0.18f));
        GameObject legRight = EnsureLimb(mochi, mat, "LegRight",
            new Vector3(0.25f, -0.55f, 0f), new Vector3(0f, 0f, -15f), new Vector3(0.18f, 0.35f, 0.18f));

        return new[] { armLeft, armRight, legLeft, legRight };
    }

    private static void AttachScripts(GameObject mochi, GameObject face, GameObject[] limbTransforms)
    {
        var faceScript = face.GetComponent<MochiFace>();
        if (faceScript == null) faceScript = face.AddComponent<MochiFace>();

        var faceSo = new SerializedObject(faceScript);
        var idleProp = faceSo.FindProperty("idleSprite");
        if (idleProp.objectReferenceValue == null)
        {
            idleProp.objectReferenceValue = face.GetComponent<SpriteRenderer>().sprite;
        }
        faceSo.ApplyModifiedPropertiesWithoutUndo();

        var limbsScript = mochi.GetComponent<MochiLimbs>();
        if (limbsScript == null) limbsScript = mochi.AddComponent<MochiLimbs>();

        var limbsSo = new SerializedObject(limbsScript);
        var limbsProp = limbsSo.FindProperty("limbs");
        limbsProp.arraySize = limbTransforms.Length;
        for (int i = 0; i < limbTransforms.Length; i++)
        {
            var element = limbsProp.GetArrayElementAtIndex(i);
            element.FindPropertyRelative("transform").objectReferenceValue = limbTransforms[i].transform;
        }
        limbsSo.ApplyModifiedPropertiesWithoutUndo();

        var bodyScript = mochi.GetComponent<MochiBody>();
        if (bodyScript == null) bodyScript = mochi.AddComponent<MochiBody>();

        var bodySo = new SerializedObject(bodyScript);
        var limbsFieldProp = bodySo.FindProperty("limbs");
        if (limbsFieldProp.objectReferenceValue == null)
        {
            limbsFieldProp.objectReferenceValue = limbsScript;
        }
        bodySo.ApplyModifiedPropertiesWithoutUndo();
    }

    private static Text EnsureSpeechCanvas()
    {
        GameObject canvasGo = GameObject.Find("SpeechCanvas");
        Canvas canvas;

        if (canvasGo == null)
        {
            canvasGo = new GameObject("SpeechCanvas", typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster));
            Undo.RegisterCreatedObjectUndo(canvasGo, "Create SpeechCanvas");
            canvas = canvasGo.GetComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;

            var scaler = canvasGo.GetComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1080f, 1920f);
        }
        else
        {
            canvas = canvasGo.GetComponent<Canvas>();
        }

        Transform existingText = canvasGo.transform.Find("SpeechText");
        GameObject textGo;

        if (existingText == null)
        {
            textGo = new GameObject("SpeechText", typeof(Text));
            textGo.transform.SetParent(canvasGo.transform, false);
            Undo.RegisterCreatedObjectUndo(textGo, "Create SpeechText");
        }
        else
        {
            textGo = existingText.gameObject;
        }

        var rect = textGo.GetComponent<RectTransform>();
        rect.anchorMin = new Vector2(0.1f, 0.75f);
        rect.anchorMax = new Vector2(0.9f, 0.9f);
        rect.offsetMin = Vector2.zero;
        rect.offsetMax = Vector2.zero;

        var text = textGo.GetComponent<Text>();
        text.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("Arial.ttf");
        text.fontSize = 48;
        text.alignment = TextAnchor.MiddleCenter;
        text.color = Color.black;
        if (string.IsNullOrEmpty(text.text))
        {
            text.text = "天気を取得中...";
        }

        return text;
    }

    private static WeatherService EnsureWeatherComponents(GameObject mochi, Text speechText)
    {
        var weatherService = mochi.GetComponent<WeatherService>();
        if (weatherService == null) weatherService = mochi.AddComponent<WeatherService>();

        var weatherDisplay = mochi.GetComponent<MochiWeatherDisplay>();
        if (weatherDisplay == null) weatherDisplay = mochi.AddComponent<MochiWeatherDisplay>();

        var displaySo = new SerializedObject(weatherDisplay);

        var serviceProp = displaySo.FindProperty("weatherService");
        if (serviceProp.objectReferenceValue == null)
        {
            serviceProp.objectReferenceValue = weatherService;
        }

        var rendererProp = displaySo.FindProperty("mochiRenderer");
        if (rendererProp.objectReferenceValue == null)
        {
            rendererProp.objectReferenceValue = mochi.GetComponent<Renderer>();
        }

        var speechProp = displaySo.FindProperty("speechText");
        if (speechProp.objectReferenceValue == null)
        {
            speechProp.objectReferenceValue = speechText;
        }

        displaySo.ApplyModifiedPropertiesWithoutUndo();

        return weatherService;
    }

    private static void EnsureLocationUI(WeatherService weatherService)
    {
        GameObject canvasGo = GameObject.Find("SpeechCanvas");
        if (canvasGo == null) return;

        Transform existingPanel = canvasGo.transform.Find("LocationPanel");
        GameObject panel;

        if (existingPanel == null)
        {
            panel = new GameObject("LocationPanel");
            panel.transform.SetParent(canvasGo.transform, false);
            Undo.RegisterCreatedObjectUndo(panel, "Create LocationPanel");
        }
        else
        {
            panel = existingPanel.gameObject;
        }

        var panelRect = EnsureRect(panel, new Vector2(0.1f, 0.03f), new Vector2(0.9f, 0.12f));

        // 入力欄
        GameObject inputGo = EnsureChild(panel, "CityInputField", typeof(InputField), typeof(Image));
        EnsureRect(inputGo, new Vector2(0f, 0f), new Vector2(0.65f, 1f));
        var inputField = inputGo.GetComponent<InputField>();
        var inputImage = inputGo.GetComponent<Image>();
        inputImage.color = new Color(1f, 1f, 1f, 0.9f);

        GameObject placeholderGo = EnsureChild(inputGo, "Placeholder", typeof(Text));
        EnsureRect(placeholderGo, Vector2.zero, Vector2.one);
        var placeholderText = placeholderGo.GetComponent<Text>();
        placeholderText.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("Arial.ttf");
        placeholderText.text = "街の名前を入力(例: Tokyo)";
        placeholderText.color = new Color(0f, 0f, 0f, 0.5f);
        placeholderText.alignment = TextAnchor.MiddleLeft;

        GameObject inputTextGo = EnsureChild(inputGo, "Text", typeof(Text));
        EnsureRect(inputTextGo, Vector2.zero, Vector2.one);
        var inputText = inputTextGo.GetComponent<Text>();
        inputText.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("Arial.ttf");
        inputText.color = Color.black;
        inputText.alignment = TextAnchor.MiddleLeft;

        inputField.textComponent = inputText;
        inputField.placeholder = placeholderText;

        // 検索ボタン
        GameObject buttonGo = EnsureChild(panel, "SearchButton", typeof(Button), typeof(Image));
        EnsureRect(buttonGo, new Vector2(0.7f, 0f), new Vector2(1f, 1f));
        var buttonImage = buttonGo.GetComponent<Image>();
        buttonImage.color = new Color(0.3f, 0.6f, 0.9f);

        GameObject buttonTextGo = EnsureChild(buttonGo, "Text", typeof(Text));
        EnsureRect(buttonTextGo, Vector2.zero, Vector2.one);
        var buttonText = buttonTextGo.GetComponent<Text>();
        buttonText.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("Arial.ttf");
        buttonText.text = "検索";
        buttonText.color = Color.white;
        buttonText.alignment = TextAnchor.MiddleCenter;

        // ステータス表示(入力欄の少し下)
        GameObject statusGo = EnsureChild(canvasGo, "LocationStatusText", typeof(Text));
        EnsureRect(statusGo, new Vector2(0.1f, -0.02f), new Vector2(0.9f, 0.03f));
        var statusText = statusGo.GetComponent<Text>();
        statusText.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("Arial.ttf");
        statusText.fontSize = 28;
        statusText.alignment = TextAnchor.MiddleCenter;
        statusText.color = Color.black;

        var locationSettings = panel.GetComponent<LocationSettings>();
        if (locationSettings == null) locationSettings = panel.AddComponent<LocationSettings>();

        var so = new SerializedObject(locationSettings);
        so.FindProperty("weatherService").objectReferenceValue = weatherService;
        so.FindProperty("cityInputField").objectReferenceValue = inputField;
        so.FindProperty("searchButton").objectReferenceValue = buttonGo.GetComponent<Button>();
        so.FindProperty("statusText").objectReferenceValue = statusText;
        so.ApplyModifiedPropertiesWithoutUndo();
    }

    private static GameObject EnsureChild(GameObject parent, string name, params System.Type[] components)
    {
        Transform existing = parent.transform.Find(name);
        if (existing != null) return existing.gameObject;

        var go = new GameObject(name, components);
        go.transform.SetParent(parent.transform, false);
        Undo.RegisterCreatedObjectUndo(go, "Create " + name);
        return go;
    }

    private static RectTransform EnsureRect(GameObject go, Vector2 anchorMin, Vector2 anchorMax)
    {
        var rect = go.GetComponent<RectTransform>();
        if (rect == null) rect = go.AddComponent<RectTransform>();

        rect.anchorMin = anchorMin;
        rect.anchorMax = anchorMax;
        rect.offsetMin = Vector2.zero;
        rect.offsetMax = Vector2.zero;
        return rect;
    }
}
