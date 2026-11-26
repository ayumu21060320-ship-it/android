using UnityEngine;

// 体重・身長・年齢・性別・活動レベルから、1日に必要なカロリー(TDEE)を計算します。
// Mifflin-St Jeor式を使用します。入力値は端末に保存され、次回も引き継がれます。
public class UserProfile : MonoBehaviour
{
    public enum ActivityLevel
    {
        Low,
        Normal,
        High
    }

    private const string WeightKey = "Mochi_Health_WeightKg";
    private const string HeightKey = "Mochi_Health_HeightCm";
    private const string AgeKey = "Mochi_Health_Age";
    private const string IsMaleKey = "Mochi_Health_IsMale";
    private const string ActivityKey = "Mochi_Health_Activity";

    public float WeightKg { get; private set; } = 60f;
    public float HeightCm { get; private set; } = 165f;
    public int Age { get; private set; } = 20;
    public bool IsMale { get; private set; } = true;
    public ActivityLevel Activity { get; private set; } = ActivityLevel.Normal;

    private void Awake()
    {
        Load();
    }

    public void Load()
    {
        WeightKg = PlayerPrefs.GetFloat(WeightKey, WeightKg);
        HeightCm = PlayerPrefs.GetFloat(HeightKey, HeightCm);
        Age = PlayerPrefs.GetInt(AgeKey, Age);
        IsMale = PlayerPrefs.GetInt(IsMaleKey, IsMale ? 1 : 0) == 1;
        Activity = (ActivityLevel)PlayerPrefs.GetInt(ActivityKey, (int)Activity);
    }

    public void Save(float weightKg, float heightCm, int age, bool isMale, ActivityLevel activity)
    {
        WeightKg = weightKg;
        HeightCm = heightCm;
        Age = age;
        IsMale = isMale;
        Activity = activity;

        PlayerPrefs.SetFloat(WeightKey, WeightKg);
        PlayerPrefs.SetFloat(HeightKey, HeightCm);
        PlayerPrefs.SetInt(AgeKey, Age);
        PlayerPrefs.SetInt(IsMaleKey, IsMale ? 1 : 0);
        PlayerPrefs.SetInt(ActivityKey, (int)Activity);
        PlayerPrefs.Save();
    }

    public float CalculateBmr()
    {
        float bmr = 10f * WeightKg + 6.25f * HeightCm - 5f * Age;
        bmr += IsMale ? 5f : -161f;
        return bmr;
    }

    public float CalculateTdee()
    {
        float multiplier;
        switch (Activity)
        {
            case ActivityLevel.Low:
                multiplier = 1.2f;
                break;
            case ActivityLevel.High:
                multiplier = 1.9f;
                break;
            default:
                multiplier = 1.55f;
                break;
        }

        return CalculateBmr() * multiplier;
    }
}
