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
        GameObject[] locationObjects = EnsureLocationUI(weatherService);
        (GameObject healthPanel, Button okButton) = EnsureHealthPanel();
        EnsureAppController(mochi, speechText, locationObjects, healthPanel, okButton);

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

    private static GameObject[] EnsureLocationUI(WeatherService weatherService)
    {
        GameObject canvasGo = GameObject.Find("SpeechCanvas");
        if (canvasGo == null) return new GameObject[0];

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

        return new[] { panel, statusGo };
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

    private static (GameObject panel, Button okButton) EnsureHealthPanel()
    {
        GameObject canvasGo = GameObject.Find("HealthCanvas");
        if (canvasGo == null)
        {
            canvasGo = new GameObject("HealthCanvas", typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster));
            Undo.RegisterCreatedObjectUndo(canvasGo, "Create HealthCanvas");

            var canvas = canvasGo.GetComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;

            var scaler = canvasGo.GetComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1080f, 1920f);
        }

        GameObject panel = EnsureChild(canvasGo, "HealthPanel", typeof(Image), typeof(VerticalLayoutGroup));
        EnsureRect(panel, new Vector2(0.08f, 0.15f), new Vector2(0.92f, 0.85f));
        panel.GetComponent<Image>().color = new Color(1f, 1f, 1f, 0.85f);

        var layout = panel.GetComponent<VerticalLayoutGroup>();
        layout.padding = new RectOffset(20, 20, 20, 20);
        layout.spacing = 10f;
        layout.childControlHeight = true;
        layout.childControlWidth = true;
        layout.childForceExpandHeight = false;
        layout.childForceExpandWidth = true;

        Text title = CreateLayoutText(panel, "TitleText", 70f, "健康管理", 36);

        InputField weightField = CreateLayoutInputField(panel, "WeightInputField", 60f, "体重(kg)");
        InputField heightField = CreateLayoutInputField(panel, "HeightInputField", 60f, "身長(cm)");
        InputField ageField = CreateLayoutInputField(panel, "AgeInputField", 60f, "年齢");

        Toggle isMaleToggle = CreateLayoutToggle(panel, "IsMaleToggle", 50f, "男性");

        GameObject sliderGo = CreateLayoutChild(panel, "ActivitySlider", 50f, typeof(Slider));
        Slider activitySlider = sliderGo.GetComponent<Slider>();
        activitySlider.minValue = 0;
        activitySlider.maxValue = 2;
        activitySlider.wholeNumbers = true;
        EnsureSliderVisuals(sliderGo);

        Button saveButton = CreateLayoutButton(panel, "SaveProfileButton", 60f, "保存して計算");
        Text calorieGoalText = CreateLayoutText(panel, "CalorieGoalText", 50f, "1日の目安: -- kcal", 28);

        InputField foodNameField = CreateLayoutInputField(panel, "FoodNameInputField", 60f, "食べたもの");
        InputField foodCaloriesField = CreateLayoutInputField(panel, "FoodCaloriesInputField", 60f, "カロリー(kcal)");
        Button addFoodButton = CreateLayoutButton(panel, "AddFoodButton", 60f, "追加");

        Text foodLogText = CreateLayoutText(panel, "FoodLogText", 300f, "今日の合計: 0kcal", 24);
        foodLogText.alignment = TextAnchor.UpperLeft;

        Button okButton = CreateLayoutButton(panel, "OkButton", 80f, "はじめる");
        okButton.GetComponent<Image>().color = new Color(0.95f, 0.6f, 0.2f);

        var userProfile = canvasGo.GetComponent<UserProfile>();
        if (userProfile == null) userProfile = canvasGo.AddComponent<UserProfile>();

        var foodLog = canvasGo.GetComponent<FoodLog>();
        if (foodLog == null) foodLog = canvasGo.AddComponent<FoodLog>();

        var healthPanel = canvasGo.GetComponent<HealthPanel>();
        if (healthPanel == null) healthPanel = canvasGo.AddComponent<HealthPanel>();

        var so = new SerializedObject(healthPanel);
        so.FindProperty("userProfile").objectReferenceValue = userProfile;
        so.FindProperty("foodLog").objectReferenceValue = foodLog;
        so.FindProperty("weightInputField").objectReferenceValue = weightField;
        so.FindProperty("heightInputField").objectReferenceValue = heightField;
        so.FindProperty("ageInputField").objectReferenceValue = ageField;
        so.FindProperty("isMaleToggle").objectReferenceValue = isMaleToggle;
        so.FindProperty("activitySlider").objectReferenceValue = activitySlider;
        so.FindProperty("saveProfileButton").objectReferenceValue = saveButton;
        so.FindProperty("calorieGoalText").objectReferenceValue = calorieGoalText;
        so.FindProperty("foodNameInputField").objectReferenceValue = foodNameField;
        so.FindProperty("foodCaloriesInputField").objectReferenceValue = foodCaloriesField;
        so.FindProperty("addFoodButton").objectReferenceValue = addFoodButton;
        so.FindProperty("foodLogText").objectReferenceValue = foodLogText;
        so.ApplyModifiedPropertiesWithoutUndo();

        return (panel, okButton);
    }

    private static GameObject CreateLayoutChild(GameObject parent, string name, float preferredHeight, params System.Type[] components)
    {
        GameObject go = EnsureChild(parent, name, components);

        var layoutElement = go.GetComponent<LayoutElement>();
        if (layoutElement == null) layoutElement = go.AddComponent<LayoutElement>();
        layoutElement.preferredHeight = preferredHeight;
        layoutElement.flexibleWidth = 1f;

        return go;
    }

    private static Text CreateLayoutText(GameObject parent, string name, float height, string defaultText, int fontSize)
    {
        GameObject go = CreateLayoutChild(parent, name, height, typeof(Text));
        var text = go.GetComponent<Text>();
        text.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("Arial.ttf");
        text.fontSize = fontSize;
        text.alignment = TextAnchor.MiddleCenter;
        text.color = Color.black;
        if (string.IsNullOrEmpty(text.text)) text.text = defaultText;
        return text;
    }

    private static InputField CreateLayoutInputField(GameObject parent, string name, float height, string placeholder)
    {
        GameObject go = CreateLayoutChild(parent, name, height, typeof(InputField), typeof(Image));
        go.GetComponent<Image>().color = new Color(1f, 1f, 1f, 0.95f);

        GameObject placeholderGo = EnsureChild(go, "Placeholder", typeof(Text));
        EnsureRect(placeholderGo, Vector2.zero, Vector2.one);
        var placeholderText = placeholderGo.GetComponent<Text>();
        placeholderText.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("Arial.ttf");
        placeholderText.text = placeholder;
        placeholderText.color = new Color(0f, 0f, 0f, 0.5f);
        placeholderText.alignment = TextAnchor.MiddleLeft;

        GameObject textGo = EnsureChild(go, "Text", typeof(Text));
        EnsureRect(textGo, Vector2.zero, Vector2.one);
        var text = textGo.GetComponent<Text>();
        text.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("Arial.ttf");
        text.color = Color.black;
        text.alignment = TextAnchor.MiddleLeft;

        var inputField = go.GetComponent<InputField>();
        inputField.textComponent = text;
        inputField.placeholder = placeholderText;
        return inputField;
    }

    private static Button CreateLayoutButton(GameObject parent, string name, float height, string label)
    {
        GameObject go = CreateLayoutChild(parent, name, height, typeof(Button), typeof(Image));
        go.GetComponent<Image>().color = new Color(0.3f, 0.6f, 0.9f);

        GameObject textGo = EnsureChild(go, "Text", typeof(Text));
        EnsureRect(textGo, Vector2.zero, Vector2.one);
        var text = textGo.GetComponent<Text>();
        text.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("Arial.ttf");
        text.text = label;
        text.color = Color.white;
        text.alignment = TextAnchor.MiddleCenter;

        return go.GetComponent<Button>();
    }

    private static Toggle CreateLayoutToggle(GameObject parent, string name, float height, string label)
    {
        GameObject go = CreateLayoutChild(parent, name, height, typeof(Toggle));

        GameObject bgGo = EnsureChild(go, "Background", typeof(Image));
        EnsureRect(bgGo, new Vector2(0f, 0.5f), new Vector2(0f, 0.5f));
        bgGo.GetComponent<RectTransform>().sizeDelta = new Vector2(40f, 40f);
        bgGo.GetComponent<Image>().color = Color.white;

        GameObject checkGo = EnsureChild(bgGo, "Checkmark", typeof(Image));
        EnsureRect(checkGo, Vector2.zero, Vector2.one);
        checkGo.GetComponent<Image>().color = new Color(0.3f, 0.6f, 0.9f);

        GameObject labelGo = EnsureChild(go, "Label", typeof(Text));
        var labelRect = labelGo.GetComponent<RectTransform>();
        labelRect.anchorMin = new Vector2(0f, 0f);
        labelRect.anchorMax = new Vector2(1f, 1f);
        labelRect.offsetMin = new Vector2(50f, 0f);
        labelRect.offsetMax = Vector2.zero;
        var labelText = labelGo.GetComponent<Text>();
        labelText.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("Arial.ttf");
        labelText.text = label;
        labelText.color = Color.black;
        labelText.alignment = TextAnchor.MiddleLeft;

        var toggle = go.GetComponent<Toggle>();
        toggle.targetGraphic = bgGo.GetComponent<Image>();
        toggle.graphic = checkGo.GetComponent<Image>();
        return toggle;
    }

    private static void EnsureAppController(GameObject mochi, Text speechText, GameObject[] locationObjects, GameObject healthPanel, Button okButton)
    {
        var controller = mochi.GetComponent<MochiAppController>();
        if (controller == null) controller = mochi.AddComponent<MochiAppController>();

        var settingsPanels = new System.Collections.Generic.List<GameObject>(locationObjects);
        if (healthPanel != null) settingsPanels.Add(healthPanel);

        var so = new SerializedObject(controller);
        so.FindProperty("mochiRoot").objectReferenceValue = mochi;
        so.FindProperty("mochiBody").objectReferenceValue = mochi.GetComponent<MochiBody>();
        so.FindProperty("okButton").objectReferenceValue = okButton;
        so.FindProperty("speechText").objectReferenceValue = speechText;

        var activeOnlyProp = so.FindProperty("activeOnlyObjects");
        activeOnlyProp.arraySize = 1;
        activeOnlyProp.GetArrayElementAtIndex(0).objectReferenceValue = speechText != null ? speechText.gameObject : null;

        var settingsProp = so.FindProperty("settingsPanels");
        settingsProp.arraySize = settingsPanels.Count;
        for (int i = 0; i < settingsPanels.Count; i++)
        {
            settingsProp.GetArrayElementAtIndex(i).objectReferenceValue = settingsPanels[i];
        }

        so.ApplyModifiedPropertiesWithoutUndo();
    }

    private static void EnsureSliderVisuals(GameObject sliderGo)
    {
        var slider = sliderGo.GetComponent<Slider>();
        if (slider.fillRect != null && slider.handleRect != null) return;

        GameObject bgGo = EnsureChild(sliderGo, "Background", typeof(Image));
        EnsureRect(bgGo, Vector2.zero, Vector2.one);
        bgGo.GetComponent<Image>().color = new Color(0.8f, 0.8f, 0.8f);

        GameObject fillAreaGo = EnsureChild(sliderGo, "Fill Area", typeof(RectTransform));
        EnsureRect(fillAreaGo, Vector2.zero, Vector2.one);

        GameObject fillGo = EnsureChild(fillAreaGo, "Fill", typeof(Image));
        EnsureRect(fillGo, Vector2.zero, Vector2.one);
        fillGo.GetComponent<Image>().color = new Color(0.3f, 0.6f, 0.9f);

        GameObject handleAreaGo = EnsureChild(sliderGo, "Handle Slide Area", typeof(RectTransform));
        EnsureRect(handleAreaGo, Vector2.zero, Vector2.one);

        GameObject handleGo = EnsureChild(handleAreaGo, "Handle", typeof(Image));
        var handleRect = handleGo.GetComponent<RectTransform>();
        handleRect.sizeDelta = new Vector2(30f, 0f);
        handleGo.GetComponent<Image>().color = Color.white;

        slider.targetGraphic = handleGo.GetComponent<Image>();
        slider.fillRect = fillGo.GetComponent<RectTransform>();
        slider.handleRect = handleGo.GetComponent<RectTransform>();
    }
}
