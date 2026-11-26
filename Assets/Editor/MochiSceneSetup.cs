using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

// Unityエディタのメニュー「Mochi > Setup Scene」から実行する、
// Mochiキャラのシーン構成(カメラ設定・本体・顔・スクリプトのアタッチ)を自動で行うツールです。
public static class MochiSceneSetup
{
    private static Font cachedUiFont;

    // 日本語(ひらがな・漢字)も表示できるよう、OSにインストールされているフォントから動的に作成します。
    // Unity同梱のArial.ttf/LegacyRuntime.ttfは欧文フォントで日本語グリフを持たないため使いません。
    private static Font GetUiFont()
    {
        if (cachedUiFont != null) return cachedUiFont;

        cachedUiFont = Font.CreateDynamicFontFromOSFont(
            new[] { "Yu Gothic UI", "Meiryo UI", "Meiryo", "MS Gothic", "Arial" },
            32);

        return cachedUiFont;
    }

    // InputField/Button/Toggle/Sliderの見た目部品一式(Unity標準のUI作成メニューと同じ素材)
    private static DefaultControls.Resources GetUiResources()
    {
        return new DefaultControls.Resources
        {
            standard = AssetDatabase.GetBuiltinExtraResource<Sprite>("UI/Skin/UISprite.psd"),
            background = AssetDatabase.GetBuiltinExtraResource<Sprite>("UI/Skin/Background.psd"),
            inputField = AssetDatabase.GetBuiltinExtraResource<Sprite>("UI/Skin/InputFieldBackground.psd"),
            knob = AssetDatabase.GetBuiltinExtraResource<Sprite>("UI/Skin/Knob.psd"),
            checkmark = AssetDatabase.GetBuiltinExtraResource<Sprite>("UI/Skin/Checkmark.psd"),
            dropdown = AssetDatabase.GetBuiltinExtraResource<Sprite>("UI/Skin/DropdownArrow.psd"),
            mask = AssetDatabase.GetBuiltinExtraResource<Sprite>("UI/Skin/UIMask.psd"),
        };
    }

    [MenuItem("Mochi/Setup Scene")]
    public static void SetupScene()
    {
        // スマホアプリなので縦向き固定にする
        PlayerSettings.defaultInterfaceOrientation = UIOrientation.Portrait;
        PlayerSettings.allowedAutorotateToPortrait = true;
        PlayerSettings.allowedAutorotateToPortraitUpsideDown = false;
        PlayerSettings.allowedAutorotateToLandscapeLeft = false;
        PlayerSettings.allowedAutorotateToLandscapeRight = false;

        SetGameViewToPortrait();

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
        GameObject healthPanel = EnsureHealthPanel();
        (GameObject categoryPanel, Toggle weatherToggle, Toggle healthToggle, Button nextButton) = EnsureCategoryPanel();
        GameObject okBar = EnsureOkBar(out Button okButton);
        EnsureAppController(mochi, speechText, locationObjects, healthPanel, categoryPanel, weatherToggle, healthToggle, nextButton, okBar, okButton, weatherService);

        Selection.activeGameObject = mochi;
        EditorSceneManager.MarkSceneDirty(EditorSceneManager.GetActiveScene());

        Debug.Log("Mochiシーンのセットアップが完了しました。Playボタンでつついて確認してください。");
    }

    [MenuItem("Mochi/Set Game View To Portrait")]
    public static void SetGameViewToPortrait()
    {
        try
        {
            const string sizeName = "Mochi Portrait (1080x1920)";

            var assembly = typeof(Editor).Assembly;
            var sizesType = assembly.GetType("UnityEditor.GameViewSizes");
            var groupTypeEnum = assembly.GetType("UnityEditor.GameViewSizeGroupType");
            var groupClassType = assembly.GetType("UnityEditor.GameViewSizeGroup");
            var sizeClassType = assembly.GetType("UnityEditor.GameViewSize");
            var sizeTypeEnum = assembly.GetType("UnityEditor.GameViewSizeType");
            var gameViewType = assembly.GetType("UnityEditor.GameView");

            var singletonType = typeof(ScriptableSingleton<>).MakeGenericType(sizesType);
            object sizesInstance = singletonType.GetProperty("instance").GetValue(null, null);

            object currentGroupType = sizesType.GetProperty("currentGroupType").GetValue(sizesInstance, null);
            object group = sizesType.GetMethod("GetGroup").Invoke(sizesInstance, new[] { currentGroupType });

            var getDisplayTexts = groupClassType.GetMethod("GetDisplayTexts");
            string[] displayTexts = (string[])getDisplayTexts.Invoke(group, null);

            int index = System.Array.FindIndex(displayTexts, t => t.StartsWith(sizeName));

            if (index < 0)
            {
                object fixedResolution = System.Enum.Parse(sizeTypeEnum, "FixedResolution");
                var ctor = sizeClassType.GetConstructor(new[] { sizeTypeEnum, typeof(int), typeof(int), typeof(string) });
                object newSize = ctor.Invoke(new object[] { fixedResolution, 1080, 1920, sizeName });

                groupClassType.GetMethod("AddCustomSize").Invoke(group, new[] { newSize });

                displayTexts = (string[])getDisplayTexts.Invoke(group, null);
                index = System.Array.FindIndex(displayTexts, t => t.StartsWith(sizeName));
            }

            if (index >= 0)
            {
                EditorWindow gameView = EditorWindow.GetWindow(gameViewType);
                var selectedSizeIndexProp = gameViewType.GetProperty(
                    "selectedSizeIndex",
                    System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.Public | System.Reflection.BindingFlags.NonPublic);
                selectedSizeIndexProp.SetValue(gameView, index, null);
            }
        }
        catch (System.Exception e)
        {
            Debug.LogWarning("Game viewを自動で縦長にできませんでした。手動でGameタブの解像度を縦長に切り替えてください: " + e.Message);
        }
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
        text.font = GetUiFont();
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

        // 入力欄(Unity標準のInputField部品を使う)
        GameObject inputGo = CreateInputFieldRaw(panel, "CityInputField");
        EnsureRect(inputGo, new Vector2(0f, 0f), new Vector2(0.65f, 1f));
        var inputField = inputGo.GetComponent<InputField>();

        var placeholderText = inputField.placeholder.GetComponent<Text>();
        placeholderText.font = GetUiFont();
        placeholderText.fontSize = 32;
        placeholderText.text = "街の名前を入力(例: Tokyo)";

        var inputText = inputField.textComponent;
        inputText.font = GetUiFont();
        inputText.fontSize = 32;

        // 検索ボタン
        GameObject buttonGo = CreateButtonRaw(panel, "SearchButton");
        EnsureRect(buttonGo, new Vector2(0.7f, 0f), new Vector2(1f, 1f));
        buttonGo.GetComponent<Image>().color = new Color(0.3f, 0.6f, 0.9f);

        var buttonText = buttonGo.GetComponentInChildren<Text>();
        buttonText.font = GetUiFont();
        buttonText.fontSize = 32;
        buttonText.text = "検索";
        buttonText.color = Color.white;

        // ステータス表示(入力欄の少し下)
        GameObject statusGo = EnsureChild(canvasGo, "LocationStatusText", typeof(Text));
        EnsureRect(statusGo, new Vector2(0.1f, -0.02f), new Vector2(0.9f, 0.03f));
        var statusText = statusGo.GetComponent<Text>();
        statusText.font = GetUiFont();
        statusText.fontSize = 30;
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

    private static GameObject EnsureHealthPanel()
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

        Text title = CreateLayoutText(panel, "TitleText", 80f, "健康管理", 44);

        InputField weightField = CreateLayoutInputField(panel, "WeightInputField", 80f, "体重(kg)");
        InputField heightField = CreateLayoutInputField(panel, "HeightInputField", 80f, "身長(cm)");
        InputField ageField = CreateLayoutInputField(panel, "AgeInputField", 80f, "年齢");

        Toggle isMaleToggle = CreateLayoutToggle(panel, "IsMaleToggle", 70f, "男性");

        Slider activitySlider = CreateLayoutSlider(panel, "ActivitySlider", 70f);
        activitySlider.minValue = 0;
        activitySlider.maxValue = 2;
        activitySlider.wholeNumbers = true;

        Button saveButton = CreateLayoutButton(panel, "SaveProfileButton", 80f, "保存して計算");
        Text calorieGoalText = CreateLayoutText(panel, "CalorieGoalText", 60f, "1日の目安: -- kcal", 32);

        InputField foodNameField = CreateLayoutInputField(panel, "FoodNameInputField", 80f, "食べたもの");
        InputField foodCaloriesField = CreateLayoutInputField(panel, "FoodCaloriesInputField", 80f, "カロリー(kcal)");
        Button addFoodButton = CreateLayoutButton(panel, "AddFoodButton", 80f, "追加");

        Text foodLogText = CreateLayoutText(panel, "FoodLogText", 300f, "今日の合計: 0kcal", 28);
        foodLogText.alignment = TextAnchor.UpperLeft;

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

        return panel;
    }

    // --- Unity標準のUI作成メニューと同じ部品(DefaultControls)をそのまま使う生成ヘルパー ---

    private static GameObject CreateInputFieldRaw(GameObject parent, string name)
    {
        Transform existing = parent.transform.Find(name);
        if (existing != null) return existing.gameObject;

        GameObject go = DefaultControls.CreateInputField(GetUiResources());
        go.name = name;
        go.transform.SetParent(parent.transform, false);
        Undo.RegisterCreatedObjectUndo(go, "Create " + name);
        return go;
    }

    private static GameObject CreateButtonRaw(GameObject parent, string name)
    {
        Transform existing = parent.transform.Find(name);
        if (existing != null) return existing.gameObject;

        GameObject go = DefaultControls.CreateButton(GetUiResources());
        go.name = name;
        go.transform.SetParent(parent.transform, false);
        Undo.RegisterCreatedObjectUndo(go, "Create " + name);
        return go;
    }

    private static GameObject CreateToggleRaw(GameObject parent, string name)
    {
        Transform existing = parent.transform.Find(name);
        if (existing != null) return existing.gameObject;

        GameObject go = DefaultControls.CreateToggle(GetUiResources());
        go.name = name;
        go.transform.SetParent(parent.transform, false);
        Undo.RegisterCreatedObjectUndo(go, "Create " + name);
        return go;
    }

    private static GameObject CreateSliderRaw(GameObject parent, string name)
    {
        Transform existing = parent.transform.Find(name);
        if (existing != null) return existing.gameObject;

        GameObject go = DefaultControls.CreateSlider(GetUiResources());
        go.name = name;
        go.transform.SetParent(parent.transform, false);
        Undo.RegisterCreatedObjectUndo(go, "Create " + name);
        return go;
    }

    private static T AddLayoutElement<T>(GameObject go, float preferredHeight) where T : Component
    {
        var layoutElement = go.GetComponent<LayoutElement>();
        if (layoutElement == null) layoutElement = go.AddComponent<LayoutElement>();
        layoutElement.preferredHeight = preferredHeight;
        layoutElement.flexibleWidth = 1f;

        return go.GetComponent<T>();
    }

    // --- VerticalLayoutGroupの中に並べる、見出し・入力欄・ボタン・トグル・スライダー ---

    private static Text CreateLayoutText(GameObject parent, string name, float height, string defaultText, int fontSize)
    {
        GameObject go = EnsureChild(parent, name, typeof(Text));
        AddLayoutElement<Text>(go, height);

        var text = go.GetComponent<Text>();
        text.font = GetUiFont();
        text.fontSize = fontSize;
        text.alignment = TextAnchor.MiddleCenter;
        text.color = Color.black;
        if (string.IsNullOrEmpty(text.text)) text.text = defaultText;
        return text;
    }

    private static InputField CreateLayoutInputField(GameObject parent, string name, float height, string placeholder)
    {
        GameObject go = CreateInputFieldRaw(parent, name);
        var inputField = AddLayoutElement<InputField>(go, height);

        var placeholderText = inputField.placeholder.GetComponent<Text>();
        placeholderText.font = GetUiFont();
        placeholderText.fontSize = 32;
        placeholderText.text = placeholder;

        var text = inputField.textComponent;
        text.font = GetUiFont();
        text.fontSize = 32;

        return inputField;
    }

    private static Button CreateLayoutButton(GameObject parent, string name, float height, string label)
    {
        GameObject go = CreateButtonRaw(parent, name);
        var button = AddLayoutElement<Button>(go, height);

        go.GetComponent<Image>().color = new Color(0.3f, 0.6f, 0.9f);

        var text = go.GetComponentInChildren<Text>();
        text.font = GetUiFont();
        text.fontSize = 34;
        text.text = label;
        text.color = Color.white;

        return button;
    }

    private static Toggle CreateLayoutToggle(GameObject parent, string name, float height, string label)
    {
        GameObject go = CreateToggleRaw(parent, name);
        var toggle = AddLayoutElement<Toggle>(go, height);

        var labelText = go.GetComponentInChildren<Text>();
        labelText.font = GetUiFont();
        labelText.fontSize = 32;
        labelText.text = label;
        labelText.color = Color.black;

        return toggle;
    }

    private static Slider CreateLayoutSlider(GameObject parent, string name, float height)
    {
        GameObject go = CreateSliderRaw(parent, name);
        return AddLayoutElement<Slider>(go, height);
    }

    private static void EnsureAppController(
        GameObject mochi,
        Text speechText,
        GameObject[] locationObjects,
        GameObject healthPanel,
        GameObject categoryPanel,
        Toggle weatherToggle,
        Toggle healthToggle,
        Button categoryNextButton,
        GameObject okBar,
        Button okButton,
        WeatherService weatherService)
    {
        var controller = mochi.GetComponent<MochiAppController>();
        if (controller == null) controller = mochi.AddComponent<MochiAppController>();

        var so = new SerializedObject(controller);

        so.FindProperty("categoryPanel").objectReferenceValue = categoryPanel;
        so.FindProperty("wantWeatherToggle").objectReferenceValue = weatherToggle;
        so.FindProperty("wantHealthToggle").objectReferenceValue = healthToggle;
        so.FindProperty("categoryNextButton").objectReferenceValue = categoryNextButton;

        var weatherSettingsProp = so.FindProperty("weatherSettingsObjects");
        weatherSettingsProp.arraySize = locationObjects.Length;
        for (int i = 0; i < locationObjects.Length; i++)
        {
            weatherSettingsProp.GetArrayElementAtIndex(i).objectReferenceValue = locationObjects[i];
        }

        var healthSettingsProp = so.FindProperty("healthSettingsObjects");
        healthSettingsProp.arraySize = healthPanel != null ? 1 : 0;
        if (healthPanel != null)
        {
            healthSettingsProp.GetArrayElementAtIndex(0).objectReferenceValue = healthPanel;
        }

        so.FindProperty("weatherService").objectReferenceValue = weatherService;

        so.FindProperty("okBarRoot").objectReferenceValue = okBar;
        so.FindProperty("okButton").objectReferenceValue = okButton;

        so.FindProperty("mochiRoot").objectReferenceValue = mochi;
        so.FindProperty("mochiBody").objectReferenceValue = mochi.GetComponent<MochiBody>();
        so.FindProperty("speechText").objectReferenceValue = speechText;

        var activeOnlyProp = so.FindProperty("activeOnlyObjects");
        activeOnlyProp.arraySize = 1;
        activeOnlyProp.GetArrayElementAtIndex(0).objectReferenceValue = speechText != null ? speechText.gameObject : null;

        so.ApplyModifiedPropertiesWithoutUndo();
    }

    private static (GameObject panel, Toggle weatherToggle, Toggle healthToggle, Button nextButton) EnsureCategoryPanel()
    {
        GameObject canvasGo = GameObject.Find("CategoryCanvas");
        if (canvasGo == null)
        {
            canvasGo = new GameObject("CategoryCanvas", typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster));
            Undo.RegisterCreatedObjectUndo(canvasGo, "Create CategoryCanvas");

            var canvas = canvasGo.GetComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;

            var scaler = canvasGo.GetComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1080f, 1920f);
        }

        GameObject panel = EnsureChild(canvasGo, "CategoryPanel", typeof(Image), typeof(VerticalLayoutGroup));
        EnsureRect(panel, Vector2.zero, Vector2.one);
        panel.GetComponent<Image>().color = new Color(0.95f, 0.95f, 0.95f, 1f);

        var layout = panel.GetComponent<VerticalLayoutGroup>();
        layout.padding = new RectOffset(60, 60, 160, 60);
        layout.spacing = 40f;
        layout.childControlHeight = true;
        layout.childControlWidth = true;
        layout.childForceExpandHeight = false;
        layout.childForceExpandWidth = true;
        layout.childAlignment = TextAnchor.UpperCenter;

        CreateLayoutText(panel, "TitleText", 100f, "なにをお世話してほしい?", 44);
        Toggle weatherToggle = CreateLayoutToggle(panel, "WantWeatherToggle", 80f, "天気を教えてほしい");
        Toggle healthToggle = CreateLayoutToggle(panel, "WantHealthToggle", 80f, "健康管理をしてほしい");
        Button nextButton = CreateLayoutButton(panel, "NextButton", 100f, "つぎへ");

        return (panel, weatherToggle, healthToggle, nextButton);
    }

    private static GameObject EnsureOkBar(out Button okButton)
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

        GameObject bar = EnsureChild(canvasGo, "OkBar", typeof(RectTransform));
        EnsureRect(bar, new Vector2(0.1f, 0.02f), new Vector2(0.9f, 0.1f));

        GameObject buttonGo = CreateButtonRaw(bar, "OkButton");
        EnsureRect(buttonGo, Vector2.zero, Vector2.one);
        buttonGo.GetComponent<Image>().color = new Color(0.95f, 0.6f, 0.2f);

        var text = buttonGo.GetComponentInChildren<Text>();
        text.font = GetUiFont();
        text.fontSize = 38;
        text.text = "はじめる";
        text.color = Color.white;

        okButton = buttonGo.GetComponent<Button>();
        return bar;
    }
}
