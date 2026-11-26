using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;

// Unity繧ｨ繝・ぅ繧ｿ縺ｮ繝｡繝九Η繝ｼ縲勲ochi > Setup Scene縲阪°繧牙ｮ溯｡後☆繧九・
// Mochi繧ｭ繝｣繝ｩ縺ｮ繧ｷ繝ｼ繝ｳ讒区・(繧ｫ繝｡繝ｩ險ｭ螳壹・譛ｬ菴薙・鬘斐・繧ｹ繧ｯ繝ｪ繝励ヨ縺ｮ繧｢繧ｿ繝・メ)繧定・蜍輔〒陦後≧繝・・繝ｫ縺ｧ縺吶・
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

        Debug.Log("Mochi繧ｷ繝ｼ繝ｳ縺ｮ繧ｻ繝・ヨ繧｢繝・・縺悟ｮ御ｺ・＠縺ｾ縺励◆縲１lay繝懊ち繝ｳ縺ｧ縺､縺､縺・※遒ｺ隱阪＠縺ｦ縺上□縺輔＞縲・);
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
            Debug.LogWarning("Universal Render Pipeline/Lit 繧ｷ繧ｧ繝ｼ繝繝ｼ縺瑚ｦ九▽縺九ｊ縺ｾ縺帙ｓ縺ｧ縺励◆縲ゅョ繝輔か繝ｫ繝医・繝槭ユ繝ｪ繧｢繝ｫ縺ｮ縺ｾ縺ｾ縺ｫ縺励∪縺吶・);
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
            // 鬘斐・繧､繝ｩ繧ｹ繝医ｒ蜈･繧後ｋ縺ｾ縺ｧ縺ｮ莉ｮ陦ｨ遉ｺ(Unity讓呎ｺ悶・蜀・せ繝励Λ繧､繝・
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

        // 鬘・Face)縺ｮ縺吶＄蠕後ｍ縺ｫ縲∝ｰ代＠螟ｧ縺阪＞豼・ｴｺ縺ｮ蜀・ｒ鄂ｮ縺・※繝倥Ν繝｡繝・ヨ縺ｮ繝舌う繧ｶ繝ｼ鬚ｨ縺ｫ隕九○繧・
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
        text.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("LegacyRuntime.ttf");
        text.fontSize = 48;
        text.alignment = TextAnchor.MiddleCenter;
        text.color = Color.black;
        if (string.IsNullOrEmpty(text.text))
        {
            text.text = "螟ｩ豌励ｒ蜿門ｾ嶺ｸｭ...";
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

        // 蜈･蜉帶ｬ・
        GameObject inputGo = EnsureChild(panel, "CityInputField", typeof(InputField), typeof(Image));
        EnsureRect(inputGo, new Vector2(0f, 0f), new Vector2(0.65f, 1f));
        var inputField = inputGo.GetComponent<InputField>();
        var inputImage = inputGo.GetComponent<Image>();
        inputImage.color = new Color(1f, 1f, 1f, 0.9f);

        GameObject placeholderGo = EnsureChild(inputGo, "Placeholder", typeof(Text));
        EnsureRect(placeholderGo, Vector2.zero, Vector2.one);
        var placeholderText = placeholderGo.GetComponent<Text>();
        placeholderText.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("LegacyRuntime.ttf");
        placeholderText.text = "陦励・蜷榊燕繧貞・蜉・萓・ Tokyo)";
        placeholderText.color = new Color(0f, 0f, 0f, 0.5f);
        placeholderText.alignment = TextAnchor.MiddleLeft;

        GameObject inputTextGo = EnsureChild(inputGo, "Text", typeof(Text));
        EnsureRect(inputTextGo, Vector2.zero, Vector2.one);
        var inputText = inputTextGo.GetComponent<Text>();
        inputText.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("LegacyRuntime.ttf");
        inputText.color = Color.black;
        inputText.alignment = TextAnchor.MiddleLeft;

        inputField.textComponent = inputText;
        inputField.placeholder = placeholderText;

        // 讀懃ｴ｢繝懊ち繝ｳ
        GameObject buttonGo = EnsureChild(panel, "SearchButton", typeof(Button), typeof(Image));
        EnsureRect(buttonGo, new Vector2(0.7f, 0f), new Vector2(1f, 1f));
        var buttonImage = buttonGo.GetComponent<Image>();
        buttonImage.color = new Color(0.3f, 0.6f, 0.9f);

        GameObject buttonTextGo = EnsureChild(buttonGo, "Text", typeof(Text));
        EnsureRect(buttonTextGo, Vector2.zero, Vector2.one);
        var buttonText = buttonTextGo.GetComponent<Text>();
        buttonText.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("LegacyRuntime.ttf");
        buttonText.text = "讀懃ｴ｢";
        buttonText.color = Color.white;
        buttonText.alignment = TextAnchor.MiddleCenter;

        // 繧ｹ繝・・繧ｿ繧ｹ陦ｨ遉ｺ(蜈･蜉帶ｬ・・蟆代＠荳・
        GameObject statusGo = EnsureChild(canvasGo, "LocationStatusText", typeof(Text));
        EnsureRect(statusGo, new Vector2(0.1f, -0.02f), new Vector2(0.9f, 0.03f));
        var statusText = statusGo.GetComponent<Text>();
        statusText.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("LegacyRuntime.ttf");
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

        Text title = CreateLayoutText(panel, "TitleText", 70f, "蛛･蠎ｷ邂｡逅・, 36);

        InputField weightField = CreateLayoutInputField(panel, "WeightInputField", 60f, "菴馴㍾(kg)");
        InputField heightField = CreateLayoutInputField(panel, "HeightInputField", 60f, "霄ｫ髟ｷ(cm)");
        InputField ageField = CreateLayoutInputField(panel, "AgeInputField", 60f, "蟷ｴ鮨｢");

        Toggle isMaleToggle = CreateLayoutToggle(panel, "IsMaleToggle", 50f, "逕ｷ諤ｧ");

        GameObject sliderGo = CreateLayoutChild(panel, "ActivitySlider", 50f, typeof(Slider));
        Slider activitySlider = sliderGo.GetComponent<Slider>();
        activitySlider.minValue = 0;
        activitySlider.maxValue = 2;
        activitySlider.wholeNumbers = true;
        EnsureSliderVisuals(sliderGo);

        Button saveButton = CreateLayoutButton(panel, "SaveProfileButton", 60f, "菫晏ｭ倥＠縺ｦ險育ｮ・);
        Text calorieGoalText = CreateLayoutText(panel, "CalorieGoalText", 50f, "1譌･縺ｮ逶ｮ螳・ -- kcal", 28);

        InputField foodNameField = CreateLayoutInputField(panel, "FoodNameInputField", 60f, "鬟溘∋縺溘ｂ縺ｮ");
        InputField foodCaloriesField = CreateLayoutInputField(panel, "FoodCaloriesInputField", 60f, "繧ｫ繝ｭ繝ｪ繝ｼ(kcal)");
        Button addFoodButton = CreateLayoutButton(panel, "AddFoodButton", 60f, "霑ｽ蜉");

        Text foodLogText = CreateLayoutText(panel, "FoodLogText", 300f, "莉頑律縺ｮ蜷郁ｨ・ 0kcal", 24);
        foodLogText.alignment = TextAnchor.UpperLeft;

        Button okButton = CreateLayoutButton(panel, "OkButton", 80f, "縺ｯ縺倥ａ繧・);
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
        text.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("LegacyRuntime.ttf");
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
        placeholderText.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("LegacyRuntime.ttf");
        placeholderText.text = placeholder;
        placeholderText.color = new Color(0f, 0f, 0f, 0.5f);
        placeholderText.alignment = TextAnchor.MiddleLeft;

        GameObject textGo = EnsureChild(go, "Text", typeof(Text));
        EnsureRect(textGo, Vector2.zero, Vector2.one);
        var text = textGo.GetComponent<Text>();
        text.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("LegacyRuntime.ttf");
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
        text.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("LegacyRuntime.ttf");
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
        labelText.font = (Font)AssetDatabase.GetBuiltinExtraResource<Font>("LegacyRuntime.ttf");
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
