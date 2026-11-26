using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.EventSystems;

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
}
