using System.Globalization;
using System.Text;
using UnityEngine;
using UnityEngine.UI;

// 健康管理パネルのUI制御です。
// 体のプロフィール入力から必要カロリーを計算し、食事ログへの追加・
// 今日の合計/残りカロリー表示をまとめて行います。
public class HealthPanel : MonoBehaviour
{
    [Header("参照")]
    [SerializeField] private UserProfile userProfile;
    [SerializeField] private FoodLog foodLog;

    [Header("プロフィール入力")]
    [SerializeField] private InputField weightInputField;
    [SerializeField] private InputField heightInputField;
    [SerializeField] private InputField ageInputField;
    [SerializeField] private Toggle isMaleToggle;
    [SerializeField] private Slider activitySlider;
    [SerializeField] private Button saveProfileButton;
    [SerializeField] private Text calorieGoalText;

    [Header("食事ログ")]
    [SerializeField] private InputField foodNameInputField;
    [SerializeField] private InputField foodCaloriesInputField;
    [SerializeField] private Button addFoodButton;
    [SerializeField] private Text foodLogText;

    private void Awake()
    {
        if (saveProfileButton != null) saveProfileButton.onClick.AddListener(OnSaveProfile);
        if (addFoodButton != null) addFoodButton.onClick.AddListener(OnAddFood);
    }

    private void Start()
    {
        if (userProfile != null)
        {
            if (weightInputField != null) weightInputField.text = userProfile.WeightKg.ToString("0.#", CultureInfo.InvariantCulture);
            if (heightInputField != null) heightInputField.text = userProfile.HeightCm.ToString("0.#", CultureInfo.InvariantCulture);
            if (ageInputField != null) ageInputField.text = userProfile.Age.ToString(CultureInfo.InvariantCulture);
            if (isMaleToggle != null) isMaleToggle.isOn = userProfile.IsMale;
            if (activitySlider != null) activitySlider.value = (int)userProfile.Activity;
        }

        RefreshCalorieGoal();
        RefreshFoodLog();
    }

    private void OnSaveProfile()
    {
        if (userProfile == null) return;

        float weight = ParseFloat(weightInputField, userProfile.WeightKg);
        float height = ParseFloat(heightInputField, userProfile.HeightCm);
        int age = ParseInt(ageInputField, userProfile.Age);
        bool isMale = isMaleToggle != null ? isMaleToggle.isOn : userProfile.IsMale;
        UserProfile.ActivityLevel activity = activitySlider != null
            ? (UserProfile.ActivityLevel)Mathf.RoundToInt(activitySlider.value)
            : userProfile.Activity;

        userProfile.Save(weight, height, age, isMale, activity);
        RefreshCalorieGoal();
        RefreshFoodLog();
    }

    private void OnAddFood()
    {
        if (foodLog == null || foodNameInputField == null || foodCaloriesInputField == null) return;
        if (string.IsNullOrWhiteSpace(foodNameInputField.text)) return;

        float calories = ParseFloat(foodCaloriesInputField, 0f);
        foodLog.AddEntry(foodNameInputField.text, calories);

        foodNameInputField.text = string.Empty;
        foodCaloriesInputField.text = string.Empty;

        RefreshFoodLog();
    }

    private void RefreshCalorieGoal()
    {
        if (calorieGoalText == null || userProfile == null) return;
        calorieGoalText.text = string.Format(CultureInfo.InvariantCulture, "1日の目安: {0:0}kcal", userProfile.CalculateTdee());
    }

    private void RefreshFoodLog()
    {
        if (foodLogText == null || foodLog == null) return;

        float total = foodLog.GetTotalCalories();
        float goal = userProfile != null ? userProfile.CalculateTdee() : 0f;

        var sb = new StringBuilder();
        sb.AppendFormat(CultureInfo.InvariantCulture, "今日の合計: {0:0}kcal / 残り: {1:0}kcal\n", total, goal - total);

        foreach ((string name, float calories) in foodLog.GetEntries())
        {
            sb.AppendFormat(CultureInfo.InvariantCulture, "・{0} ({1:0}kcal)\n", name, calories);
        }

        foodLogText.text = sb.ToString();
    }

    private static float ParseFloat(InputField field, float fallback)
    {
        if (field == null) return fallback;
        return float.TryParse(field.text, NumberStyles.Float, CultureInfo.InvariantCulture, out float value) ? value : fallback;
    }

    private static int ParseInt(InputField field, int fallback)
    {
        if (field == null) return fallback;
        return int.TryParse(field.text, out int value) ? value : fallback;
    }
}
