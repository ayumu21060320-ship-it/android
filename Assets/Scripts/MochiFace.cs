using UnityEngine;

// 顔パーツ(Mochi本体の子に作ったSpriteRenderer付きGameObject)にアタッチします。
// 3D空間に置いたスプライトとして、状態に応じて表情(画像)を切り替えるだけの役割です。
// 天気・アラームの表情(ステップ2以降)もここにExpressionを増やして対応していきます。
[RequireComponent(typeof(SpriteRenderer))]
public class MochiFace : MonoBehaviour
{
    public enum Expression
    {
        Idle,
        Poked
    }

    [Header("表情スプライト")]
    [Tooltip("何もされていない時の顔")]
    [SerializeField] private Sprite idleSprite;

    [Tooltip("つつかれている時の顔(びっくり顔など)")]
    [SerializeField] private Sprite pokedSprite;

    private SpriteRenderer spriteRenderer;
    private Expression currentExpression = Expression.Idle;

    private void Awake()
    {
        spriteRenderer = GetComponent<SpriteRenderer>();
        ApplySprite();
    }

    public void SetExpression(Expression expression)
    {
        if (currentExpression == expression) return;
        currentExpression = expression;
        ApplySprite();
    }

    private void ApplySprite()
    {
        if (spriteRenderer == null) return;

        switch (currentExpression)
        {
            case Expression.Poked:
                spriteRenderer.sprite = pokedSprite != null ? pokedSprite : idleSprite;
                break;
            case Expression.Idle:
            default:
                spriteRenderer.sprite = idleSprite;
                break;
        }
    }
}
