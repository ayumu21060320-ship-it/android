using UnityEngine;

// Mochiの頭の上に、吹き出し(自分自身のRectTransform)を追従させます。
// SpeechCanvasがScreen Space - Overlayであることを前提に、
// Mochiのワールド座標をスクリーン座標に変換して追いかけます。
[RequireComponent(typeof(RectTransform))]
public class MochiSpeechBubble : MonoBehaviour
{
    [Tooltip("追従する対象(Mochi本体)")]
    [SerializeField] private Transform target;

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
