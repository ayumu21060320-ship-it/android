using UnityEngine;
using UnityEngine.EventSystems;

// 3Dのモチ本体(Sphereなどのプリミティブ)にアタッチします。
// つつくと潰れて(Squash)、離すとバネでぷるんと戻ります(Spring)。
// タップ判定には Collider(SphereColliderなど) と、
// カメラに付けた Physics Raycaster + シーン内の EventSystem が必要です。
public class MochiBody : MonoBehaviour, IPointerDownHandler, IPointerUpHandler, IPointerExitHandler
{
    [Header("参照")]
    [Tooltip("顔パーツを管理しているMochiFace。未設定なら自動で子から探します。")]
    [SerializeField] private MochiFace face;

    [Tooltip("手足を管理しているMochiLimbs。未設定なら自動で同じGameObjectから探します。")]
    [SerializeField] private MochiLimbs limbs;

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

    private void Awake()
    {
        baseScale = transform.localScale;
        currentScale = baseScale;

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

    private void Update()
    {
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
}
