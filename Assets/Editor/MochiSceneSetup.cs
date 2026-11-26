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
        AttachScripts(mochi, face);

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

    private static void AttachScripts(GameObject mochi, GameObject face)
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

        if (mochi.GetComponent<MochiBody>() == null)
        {
            mochi.AddComponent<MochiBody>();
        }
    }
}
