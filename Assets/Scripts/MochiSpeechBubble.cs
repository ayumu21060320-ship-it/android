using UnityEngine;

// Mochiの頭の上に、吹き出し(自分自身のRectTransform)を追従させます。
// SpeechCanvasがScreen Space - Overlayであることを前提に、
// Mochiのワールド座標をスクリーン座標に変換して追いかけます。
// また、Mochiを1回タップするたびに表示/非表示を切り替えます(ずっと出しっぱなしにしない)。
[RequireComponent(typeof(RectTransform))]
public class MochiSpeechBubble : MonoBehaviour
{
    [Tooltip("追従する対象(Mochi本体)")]
    [SerializeField] private Transform target;

    [Tooltip("タップ検知に使うMochiBody。設定するとタップのたびに表示/非表示が切り替わります。")]
    [SerializeField] private MochiBody mochiBody;

    [Tooltip("基準にするカメラ。未設定ならCamera.mainを使います。")]
    [SerializeField] private Camera worldCamera;

    [Tooltip("Mochiの真上からどれだけずらすか(スクリーンピクセル)")]
    [SerializeField] private Vector2 screenOffset = new Vector2(0f, 180f);

    private RectTransform rect;
    private Canvas canvas;

    private void Awake()
    {
        rect = (RectTransform)transform;
        canvas = GetComponentInParent<Canvas>();

        // OnEnable/OnDisableだと、この吹き出し自身をSetActive(false)した瞬間に
        // 購読も解除されてしまい、次のタップで再表示できなくなるためAwake/OnDestroyで管理する
        if (mochiBody != null)
        {
            mochiBody.OnSingleTapped += ToggleVisible;
        }
    }

    private void OnDestroy()
    {
        if (mochiBody != null)
        {
            mochiBody.OnSingleTapped -= ToggleVisible;
        }
    }

    private void ToggleVisible()
    {
        gameObject.SetActive(!gameObject.activeSelf);
    }

    private void LateUpdate()
    {
        if (target == null || canvas == null) return;

        Camera cam = worldCamera != null ? worldCamera : Camera.main;
        if (cam == null) return;

        Vector2 screenPoint = cam.WorldToScreenPoint(target.position);
        screenPoint += screenOffset;

        RectTransform canvasRect = (RectTransform)canvas.transform;
        Camera eventCamera = canvas.renderMode == RenderMode.ScreenSpaceOverlay ? null : cam;

        if (RectTransformUtility.ScreenPointToLocalPointInRectangle(canvasRect, screenPoint, eventCamera, out Vector2 localPoint))
        {
            rect.anchoredPosition = localPoint;
        }
    }
}
