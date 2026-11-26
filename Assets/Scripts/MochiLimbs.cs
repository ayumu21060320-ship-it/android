using UnityEngine;

// Mochiの手足(本体の子になっているTransform)の動きをまとめて管理します。
// つつかれている間はバタバタと暴れ、離すとバネで元の姿勢に戻ります。
[System.Serializable]
public class MochiLimb
{
    public Transform transform;

    [HideInInspector] public Quaternion restRotation;
    [HideInInspector] public Vector3 currentOffset;
    [HideInInspector] public Vector3 angularVelocity;
    [HideInInspector] public float noiseSeed;
}

public class MochiLimbs : MonoBehaviour
{
    [Header("手足")]
    [Tooltip("バタつかせたい手足のTransformを登録します")]
    [SerializeField] private MochiLimb[] limbs = new MochiLimb[0];

    [Header("バタつく設定")]
    [Tooltip("バタつく時の振れ幅(度)")]
    [SerializeField] private float flailAmplitude = 35f;

    [Tooltip("バタつく速さ")]
    [SerializeField] private float flailSpeed = 6f;

    [Header("戻る設定(バネ)")]
    [SerializeField] private float springStiffness = 150f;
    [SerializeField] private float springDamping = 10f;

    private bool isPoked;

    private void Awake()
    {
        foreach (MochiLimb limb in limbs)
        {
            if (limb.transform == null) continue;
            limb.restRotation = limb.transform.localRotation;
            limb.noiseSeed = Random.Range(0f, 100f);
        }
    }

    public void OnPoke()
    {
        isPoked = true;
    }

    public void OnRelease()
    {
        isPoked = false;
    }

    private void Update()
    {
        foreach (MochiLimb limb in limbs)
        {
            if (limb.transform == null) continue;

            if (isPoked)
            {
                float t = (Time.time + limb.noiseSeed) * flailSpeed;
                Vector3 target = new Vector3(
                    Mathf.Sin(t) * flailAmplitude,
                    Mathf.Sin(t * 1.3f + 1f) * flailAmplitude * 0.6f,
                    Mathf.Sin(t * 0.7f + 2f) * flailAmplitude * 0.5f);

                limb.currentOffset = Vector3.Lerp(limb.currentOffset, target, Time.deltaTime * flailSpeed);
                limb.angularVelocity = Vector3.zero;
            }
            else
            {
                Vector3 displacement = limb.currentOffset;
                Vector3 springForce = -springStiffness * displacement;
                Vector3 dampingForce = -springDamping * limb.angularVelocity;
                Vector3 acceleration = springForce + dampingForce;

                limb.angularVelocity += acceleration * Time.deltaTime;
                limb.currentOffset += limb.angularVelocity * Time.deltaTime;
            }

            limb.transform.localRotation = limb.restRotation * Quaternion.Euler(limb.currentOffset);
        }
    }
}
