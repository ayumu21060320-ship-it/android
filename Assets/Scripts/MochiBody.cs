using UnityEngine;
using UnityEngine.EventSystems;

// 3Dのモチ本体(Sphereなどのプリミティブ)にアタッチします。
// つつくと潰れて(Squash)、離すとバネでぷるんと戻ります(Spring)。
// タップ判定には Collider(SphereColliderなど) と、
// カメラに付けた Physics Raycaster + シーン内の EventSystem が必要です。
public class MochiBody : MonoBehaviour, IPointerDownHandler, IPointerUpHandler, IPointerExitHandler, IBeginDragHandler, IDragHandler, IEndDragHandler
{
    [Header("参照")]
    [Tooltip("顔パーツを管理しているMochiFace。未設定なら自動で子から探します。")]
    [SerializeField] private MochiFace face;

    [Tooltip("手足を管理しているMochiLimbs。未設定なら自動で同じGameObjectから探します。")]
    [SerializeField] private MochiLimbs limbs;

    [Header("スワイプで吹っ飛ぶ設定")]
    [Tooltip("スワイプの速さに対して、どれくらい勢いよく飛ばすか")]
    [SerializeField] private float launchSpeedMultiplier = 1f;

    [Tooltip("飛んでいく最大の速さ")]
    [SerializeField] private float maxLaunchSpeed = 8f;

    [Tooltip("飛んでいる間、1秒あたりどれくらい減速するか")]
    [SerializeField] private float flightDamping = 1.2f;

    [Tooltip("これより遅くなったら飛ぶのをやめて、浮遊に戻る")]
    [SerializeField] private float minFlightSpeed = 0.1f;

    [Tooltip("画面内に収めるための移動範囲(左下)")]
    [SerializeField] private Vector2 boundsMin = new Vector2(-3f, -4f);

    [Tooltip("画面内に収めるための移動範囲(右上)")]
    [SerializeField] private Vector2 boundsMax = new Vector2(3f, 4f);

    [Tooltip("端で跳ね返った時に、どれくらい勢いが残るか(1で跳ね返り後も同じ速さ)")]
    [SerializeField, Range(0f, 1f)] private float bounceDamping = 0.6f;

    [Header("無重力でふわふわ漂う設定")]
    [Tooltip("上下に漂う高さ")]
    [SerializeField] private float floatAmplitudeY = 0.15f;

    [Tooltip("左右に漂う幅")]
    [SerializeField] private float floatAmplitudeX = 0.08f;

    [Tooltip("ゆらゆら傾く角度(度)")]
    [SerializeField] private float floatTiltAmplitude = 4f;

    [Tooltip("漂う速さ")]
    [SerializeField] private float floatSpeed = 1f;

    [Header("潰れる設定")]
    [Tooltip("つついた時にどれくらい潰れるか(0.3 = Y方向30%縮んでX/Zに広がる)")]
    [SerializeField, Range(0f, 0.6f)] private float squashAmount = 0.3f;

    [Tooltip("つついている間、潰れた形に近づく速さ")]
    [SerializeField] private float pokeFollowSpeed = 20f;

    [Header("ぷるんと戻る設定(バネ)")]
    [Tooltip("バネの強さ。大きいほど勢いよく戻る")]
    [SerializeField] private float springStiffness = 180f;

    [Tooltip("バネの減衰。大きいほど早く振動が収まる(柔らかい弾力なら6~10あたり)")]
    [SerializeField] private float springDamping = 8f;

    private Vector3 baseScale;
    private Vector3 currentScale;
    private Vector3 scaleVelocity;
    private bool isPoked;

    private Vector3 anchorPosition;
    private float floatTimeOffset;
    private bool isDragging;
    private Camera dragCamera;
    private float dragDepth;
    private Vector3 lastDragWorldPosition;
    private Vector3 dragVelocity;

    private bool isFlying;
    private Vector3 flightVelocity;

    private void Awake()
    {
        baseScale = transform.localScale;
        currentScale = baseScale;
        anchorPosition = transform.position;
        floatTimeOffset = Random.Range(0f, 100f);

        if (face == null)
        {
            face = GetComponentInChildren<MochiFace>();
        }

        if (limbs == null)
        {
            limbs = GetComponent<MochiLimbs>();
        }
    }

    public void OnPointerDown(PointerEventData eventData)
    {
        isPoked = true;
        if (face != null) face.SetExpression(MochiFace.Expression.Poked);
        if (limbs != null) limbs.OnPoke();
    }

    public void OnPointerUp(PointerEventData eventData)
    {
        ReleasePoke();
    }

    public void OnPointerExit(PointerEventData eventData)
    {
        if (isPoked) ReleasePoke();
    }

    private void ReleasePoke()
    {
        isPoked = false;
        if (face != null) face.SetExpression(MochiFace.Expression.Idle);
        if (limbs != null) limbs.OnRelease();
    }

    public void OnBeginDrag(PointerEventData eventData)
    {
        dragCamera = eventData.pressEventCamera != null ? eventData.pressEventCamera : Camera.main;
        dragDepth = dragCamera.WorldToScreenPoint(transform.position).z;
        isDragging = true;
        isFlying = false;
        lastDragWorldPosition = transform.position;
        dragVelocity = Vector3.zero;
    }

    public void OnDrag(PointerEventData eventData)
    {
        if (dragCamera == null) return;

        Vector3 screenPoint = new Vector3(eventData.position.x, eventData.position.y, dragDepth);
        Vector3 worldPoint = dragCamera.ScreenToWorldPoint(screenPoint);
        Vector3 newPosition = new Vector3(worldPoint.x, worldPoint.y, transform.position.z);

        if (Time.deltaTime > 0f)
        {
            dragVelocity = (newPosition - lastDragWorldPosition) / Time.deltaTime;
        }
        lastDragWorldPosition = newPosition;

        transform.position = newPosition;
    }

    public void OnEndDrag(PointerEventData eventData)
    {
        isDragging = false;

        Vector3 launchVelocity = dragVelocity * launchSpeedMultiplier;
        if (launchVelocity.magnitude > maxLaunchSpeed)
        {
            launchVelocity = launchVelocity.normalized * maxLaunchSpeed;
        }

        if (launchVelocity.magnitude > minFlightSpeed)
        {
            flightVelocity = launchVelocity;
            isFlying = true;
        }
        else
        {
            anchorPosition = transform.position;
        }
    }

    private void Update()
    {
        if (isDragging)
        {
            // 位置はOnDragで直接動かしているので、ここでは何もしない
        }
        else if (isFlying)
        {
            UpdateFlight();
        }
        else
        {
            // 無重力空間を漂っているような、ゆっくりとした上下左右の動きと傾き
            float t = (Time.time + floatTimeOffset) * floatSpeed;
            Vector3 floatOffset = new Vector3(
                Mathf.Sin(t * 0.6f + 1.3f) * floatAmplitudeX,
                Mathf.Sin(t) * floatAmplitudeY,
                0f);

            transform.position = anchorPosition + floatOffset;
            transform.rotation = Quaternion.Euler(0f, 0f, Mathf.Sin(t * 0.8f + 0.5f) * floatTiltAmplitude);
        }

        if (isPoked)
        {
            // Y方向(高さ)を潰し、X/Z方向(幅・奥行き)に広げる
            Vector3 targetScale = new Vector3(
                baseScale.x * (1f + squashAmount),
                baseScale.y * (1f - squashAmount),
                baseScale.z * (1f + squashAmount));

            currentScale = Vector3.Lerp(currentScale, targetScale, Time.deltaTime * pokeFollowSpeed);
            scaleVelocity = Vector3.zero;
        }
        else
        {
            // バネ-ダンパーの式: 戻そうとする力 - 揺れを抑える力
            Vector3 displacement = currentScale - baseScale;
            Vector3 springForce = -springStiffness * displacement;
            Vector3 dampingForce = -springDamping * scaleVelocity;
            Vector3 acceleration = springForce + dampingForce;

            scaleVelocity += acceleration * Time.deltaTime;
            currentScale += scaleVelocity * Time.deltaTime;
        }

        transform.localScale = currentScale;
    }

    private void UpdateFlight()
    {
        Vector3 position = transform.position + flightVelocity * Time.deltaTime;

        // 空気抵抗はないはずだが、画面内に留まるようゆっくり減速させる
        flightVelocity *= Mathf.Clamp01(1f - flightDamping * Time.deltaTime);

        if (position.x < boundsMin.x || position.x > boundsMax.x)
        {
            flightVelocity.x = -flightVelocity.x * bounceDamping;
            position.x = Mathf.Clamp(position.x, boundsMin.x, boundsMax.x);
        }

        if (position.y < boundsMin.y || position.y > boundsMax.y)
        {
            flightVelocity.y = -flightVelocity.y * bounceDamping;
            position.y = Mathf.Clamp(position.y, boundsMin.y, boundsMax.y);
        }

        transform.position = position;

        // 飛んでいく向きに合わせて少し傾ける
        float tilt = Mathf.Clamp(-flightVelocity.x * 5f, -30f, 30f);
        transform.rotation = Quaternion.Euler(0f, 0f, tilt);

        if (flightVelocity.magnitude < minFlightSpeed)
        {
            isFlying = false;
            anchorPosition = transform.position;
        }
    }
}
