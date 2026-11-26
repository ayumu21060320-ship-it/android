using System;
using System.Collections.Generic;
using System.Globalization;
using UnityEngine;

// 食べたものと見積もりカロリーを手入力で記録する、シンプルな食事ログです。
// 日付が変わると自動でリセットされます(写真からの自動判定はまだ未対応)。
public class FoodLog : MonoBehaviour
{
    [Serializable]
    private class Entry
    {
        public string name;
        public float calories;
    }

    [Serializable]
    private class EntryListWrapper
    {
        public List<Entry> entries = new List<Entry>();
    }

    private const string DateKey = "Mochi_FoodLog_Date";
    private const string EntriesKey = "Mochi_FoodLog_Entries";

    private EntryListWrapper todayEntries = new EntryListWrapper();

    private void Awake()
    {
        LoadTodayEntries();
    }

    public void AddEntry(string entryName, float calories)
    {
        todayEntries.entries.Add(new Entry { name = entryName, calories = calories });
        SaveEntries();
    }

    public float GetTotalCalories()
    {
        float total = 0f;
        foreach (Entry entry in todayEntries.entries)
        {
            total += entry.calories;
        }
        return total;
    }

    public IReadOnlyList<(string name, float calories)> GetEntries()
    {
        var list = new List<(string, float)>();
        foreach (Entry entry in todayEntries.entries)
        {
            list.Add((entry.name, entry.calories));
        }
        return list;
    }

    private void LoadTodayEntries()
    {
        string today = DateTime.Now.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
        string savedDate = PlayerPrefs.GetString(DateKey, "");

        if (savedDate == today && PlayerPrefs.HasKey(EntriesKey))
        {
            todayEntries = JsonUtility.FromJson<EntryListWrapper>(PlayerPrefs.GetString(EntriesKey));
            if (todayEntries == null) todayEntries = new EntryListWrapper();
        }
        else
        {
            todayEntries = new EntryListWrapper();
            SaveEntries();
        }
    }

    private void SaveEntries()
    {
        PlayerPrefs.SetString(DateKey, DateTime.Now.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture));
        PlayerPrefs.SetString(EntriesKey, JsonUtility.ToJson(todayEntries));
        PlayerPrefs.Save();
    }
}
