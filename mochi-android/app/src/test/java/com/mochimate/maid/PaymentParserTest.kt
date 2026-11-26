package com.mochimate.maid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentParserTest {

    @Test fun payPayStyle() {
        val p = PaymentParser.parse("PayPay", "セブン-イレブン 新宿店で1,280円のお支払いが完了しました")
        assertEquals(1280, p?.amount)
        assertEquals("セブン-イレブン 新宿店", p?.store)
    }

    @Test fun labeledStore() {
        val p = PaymentParser.parse("カードご利用のお知らせ", "ご利用金額：¥3,500\n利用先：AMAZON.CO.JP")
        assertEquals(3500, p?.amount)
        assertEquals("AMAZON.CO.JP", p?.store)
    }

    @Test fun fullWidthDigits() {
        assertEquals(980, PaymentParser.parse("決済完了", "９８０円を支払いました")?.amount)
    }

    @Test fun ignoresReceive() {
        assertNull(PaymentParser.parse("PayPay", "〇〇さんから500円を受け取りました"))
    }

    @Test fun ignoresCharge() {
        assertNull(PaymentParser.parse("PayPay", "3,000円をチャージしました"))
    }

    @Test fun ignoresRefund() {
        assertNull(PaymentParser.parse("決済", "1,000円のお支払いを取消・返金しました"))
    }

    @Test fun ignoresNonPayment() {
        assertNull(PaymentParser.parse("LINE", "今日のランチ1000円だった"))
    }

    @Test fun ignoresNoAmount() {
        assertNull(PaymentParser.parse("PayPay", "お支払いが完了しました"))
    }

    @Test fun guessCategory() {
        assertEquals("食費", MoneyStore.guessCategory("セブン-イレブン"))
        assertEquals("交通", MoneyStore.guessCategory("JR東日本"))
        assertEquals("その他", MoneyStore.guessCategory(null))
    }
}
