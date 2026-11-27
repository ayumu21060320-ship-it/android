package com.mochimate.maid

import android.content.Context
import kotlin.math.roundToInt

/** 食品1つ分の栄養(100g あたり) */
data class Food(
    val id: String,
    val name: String,
    val kcal: Double,
    val p: Double,
    val f: Double,
    val c: Double,
    val fiber: Double,
    val salt: Double,
) {
    /** 「＜鳥肉類＞ にわとり ［若どり・主品目］ むね 皮なし 生」→ 少し読みやすく */
    val displayName: String
        get() = name.replace(Regex("＜[^＞]*＞\\s*|（[^）]*類）\\s*"), "").replace("［", "").replace("］", "").trim()
}

/** よく食べるものの呼び名と、1回分の目安量 */
data class FoodAlias(val words: List<String>, val foodId: String, val grams: Int, val unit: String)

/** 検索結果の1件。alias があれば「茶碗1杯」などの目安量つき */
data class FoodHit(val food: Food, val alias: FoodAlias?)

/**
 * 食品データベース(文部科学省「日本食品標準成分表（八訂）増補2023年」をもとに作成)。
 * 約2,500品目。カタカナ・ひらがなの違いは気にせず検索できる。
 */
object FoodDb {

    const val SOURCE = "出典：文部科学省「日本食品標準成分表（八訂）増補2023年」（https://www.mext.go.jp/a_menu/syokuhinseibun/mext_00001.html）を加工して作成"

    @Volatile private var foods: List<Food>? = null
    private var byId: Map<String, Food> = emptyMap()
    private var keys: List<String> = emptyList()

    val ALIASES = listOf(
        FoodAlias(listOf("ごはん", "ご飯", "白米", "めし", "ライス"), "01088", 150, "茶碗1杯"),
        FoodAlias(listOf("おかゆ", "お粥"), "01093", 250, "1杯"),
        FoodAlias(listOf("おにぎり", "おむすび"), "01111", 110, "1個"),
        FoodAlias(listOf("食パン", "トースト"), "01026", 60, "6枚切り1枚"),
        FoodAlias(listOf("ロールパン"), "01034", 30, "1個"),
        FoodAlias(listOf("クロワッサン"), "01209", 40, "1個"),
        FoodAlias(listOf("あんパン"), "15069", 100, "1個"),
        FoodAlias(listOf("うどん"), "01039", 250, "1玉"),
        FoodAlias(listOf("ラーメン", "中華めん"), "01048", 230, "1玉"),
        FoodAlias(listOf("カップラーメン", "カップ麺", "カップめん"), "01191", 78, "1個"),
        FoodAlias(listOf("パスタ", "スパゲッティ", "スパゲティ"), "01064", 250, "1人前"),
        FoodAlias(listOf("そば", "蕎麦"), "01128", 200, "1人前"),
        FoodAlias(listOf("オートミール"), "01004", 30, "1食"),
        FoodAlias(listOf("鶏むね", "むね肉", "胸肉"), "11220", 100, "100g"),
        FoodAlias(listOf("鶏もも", "もも肉"), "11221", 100, "100g"),
        FoodAlias(listOf("ささみ"), "11227", 50, "1本"),
        FoodAlias(listOf("唐揚げ", "からあげ", "から揚げ"), "11289", 120, "4個"),
        FoodAlias(listOf("とんかつ"), "11276", 120, "1枚"),
        FoodAlias(listOf("豚ロース", "豚肉"), "11123", 100, "100g"),
        FoodAlias(listOf("牛肉", "牛もも"), "11077", 100, "100g"),
        FoodAlias(listOf("ハンバーグ"), "18050", 150, "1個"),
        FoodAlias(listOf("ぎょうざ", "餃子"), "18002", 150, "6個"),
        FoodAlias(listOf("鮭", "さけ", "サーモン"), "10134", 80, "1切れ"),
        FoodAlias(listOf("さば", "鯖"), "10154", 80, "1切れ"),
        FoodAlias(listOf("まぐろ", "マグロ", "刺身"), "10252", 80, "1人前"),
        FoodAlias(listOf("卵", "たまご", "玉子", "目玉焼き"), "12004", 50, "1個"),
        FoodAlias(listOf("ゆで卵", "ゆでたまご"), "12005", 50, "1個"),
        FoodAlias(listOf("納豆"), "04046", 45, "1パック"),
        FoodAlias(listOf("豆腐", "とうふ"), "04032", 150, "半丁"),
        FoodAlias(listOf("牛乳", "ミルク"), "13003", 200, "コップ1杯"),
        FoodAlias(listOf("ヨーグルト"), "13025", 100, "1個"),
        FoodAlias(listOf("チーズ"), "13040", 20, "1切れ"),
        FoodAlias(listOf("バナナ"), "07107", 90, "1本"),
        FoodAlias(listOf("りんご", "リンゴ"), "07148", 150, "半分"),
        FoodAlias(listOf("アボカド"), "07006", 70, "半分"),
        FoodAlias(listOf("キャベツ", "サラダ"), "06061", 50, "小皿1杯"),
        FoodAlias(listOf("トマト"), "06182", 150, "1個"),
        FoodAlias(listOf("ブロッコリー"), "06263", 50, "小皿1杯"),
        FoodAlias(listOf("じゃがいも"), "02017", 130, "1個"),
        FoodAlias(listOf("さつまいも", "焼き芋"), "02007", 100, "1/2本"),
        FoodAlias(listOf("ポテチ", "ポテトチップス"), "15103", 60, "1袋"),
        FoodAlias(listOf("アイス", "アイスクリーム"), "13043", 100, "1個"),
        FoodAlias(listOf("コーラ"), "16053", 500, "500ml"),
        FoodAlias(listOf("ビール"), "16006", 350, "1缶"),
    )

    /** 初回だけ assets から読み込む(約2,500行) */
    fun load(context: Context): List<Food> {
        foods?.let { return it }
        synchronized(this) {
            foods?.let { return it }
            val list = context.assets.open("foods.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.drop(1).mapNotNull { line ->
                    val c = line.split('\t')
                    if (c.size < 9) null
                    else Food(c[0], c[2], c[3].toDouble(), c[4].toDouble(), c[5].toDouble(), c[6].toDouble(), c[7].toDouble(), c[8].toDouble())
                }.toList()
            }
            byId = list.associateBy { it.id }
            keys = list.map { normalize(it.name) }
            foods = list
            return list
        }
    }

    fun byId(context: Context, id: String): Food? { load(context); return byId[id] }

    /** 名前で探す。よく食べるもの(呼び名)が先、そのあと成分表の名前で前のほうに一致するもの */
    fun search(context: Context, query: String, limit: Int = 60): List<FoodHit> {
        val all = load(context)
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        val hits = ArrayList<FoodHit>()
        val seen = HashSet<String>()
        for (a in ALIASES) {
            if (a.words.any { normalize(it).startsWith(q) || q.startsWith(normalize(it)) }) {
                byId[a.foodId]?.let { if (seen.add(it.id)) hits += FoodHit(it, a) }
            }
        }
        val scored = all.indices.mapNotNull { i ->
            val k = keys[i]
            val pos = k.indexOf(q)
            if (pos < 0) null else i to pos
        }.sortedWith(compareBy({ it.second }, { keys[it.first].length }))
        for ((i, _) in scored) {
            val f = all[i]
            if (seen.add(f.id)) hits += FoodHit(f, null)
            if (hits.size >= limit) break
        }
        return hits
    }

    /** カタカナ→ひらがな、空白や記号を取りのぞいて、比べやすくする */
    fun normalize(s: String): String = buildString {
        for (ch in s) {
            val c = when (ch) {
                in 'ァ'..'ヶ' -> ch - 0x60
                else -> ch
            }
            if (c.isWhitespace() || c in "［］＜＞（）()・、 ") continue
            append(c.lowercaseChar())
        }
    }

    /** g 数に合わせた栄養を計算する */
    fun scaled(food: Food, grams: Double) = Nutrition(
        kcal = (food.kcal * grams / 100).roundToInt(),
        p = food.p * grams / 100,
        f = food.f * grams / 100,
        c = food.c * grams / 100,
    )
}

/** 1回分の栄養 */
data class Nutrition(val kcal: Int, val p: Double, val f: Double, val c: Double)
