package com.mochimate.maid

/** 通知から読み取った支払い */
data class Payment(val amount: Int, val store: String?)

/**
 * 支払いアプリや銀行アプリの通知の文章から、支払った金額とお店の名前を読み取る。
 * 受け取り・チャージ・返金などは支払いではないので無視する。
 * アプリごとに書き方がちがうので、あくまで「それっぽいもの」を拾う作り。
 */
object PaymentParser {

    private val positive = Regex("支払|決済|ご利用|利用しました|購入|お買い上げ|お買上げ|引き落とし|引落|payment|paid|charged", RegexOption.IGNORE_CASE)
    private val negative = Regex("受け取|受取|入金|チャージ|返金|キャンセル|取消|付与|獲得|もらい|プレゼント|残高|送られ|請求が届|予定|リマインド|期限|refund", RegexOption.IGNORE_CASE)

    private val yenAfter = Regex("([0-9０-９][0-9０-９,，]*)\\s*円")
    private val yenBefore = Regex("[¥￥]\\s*([0-9０-９][0-9０-９,，]*)")

    private val storeLabeled = Regex("(?:加盟店|利用先|ご利用先|店舗|お店|支払先|お支払い先)\\s*(?:名)?\\s*[:：]\\s*([^\\n、。]+)")
    // 「○○で(1,280円の)お支払い」の ○○
    private val storeBefore = Regex("([^、。\\n]{1,24}?)\\s*(?:で|にて)\\s*(?:[¥￥]?\\s*[0-9０-９][0-9０-９,，]*\\s*円?\\s*)?(?:の)?\\s*(?:お)?(?:支払|決済|ご利用|お買い)")

    fun parse(title: String?, text: String?): Payment? {
        val all = listOfNotNull(title, text).joinToString("\n")
        if (all.isBlank()) return null
        if (!positive.containsMatchIn(all)) return null
        if (negative.containsMatchIn(all)) return null

        val raw = (yenBefore.find(all) ?: yenAfter.find(all))?.groupValues?.get(1) ?: return null
        val amount = normalizeDigits(raw).replace(",", "").toIntOrNull() ?: return null
        if (amount !in 1..1_000_000) return null

        val store = (storeLabeled.find(all) ?: storeBefore.find(all))
            ?.groupValues?.get(1)
            ?.trim()
            ?.trim('「', '」', '【', '】', ' ')
            ?.takeIf { it.isNotEmpty() && !it.contains(Regex("[0-9０-９]+\\s*円")) }
            ?.take(20)
        return Payment(amount, store)
    }

    private fun normalizeDigits(s: String): String = buildString {
        for (c in s) append(
            when (c) {
                in '０'..'９' -> '0' + (c - '０')
                '，' -> ','
                else -> c
            },
        )
    }
}
