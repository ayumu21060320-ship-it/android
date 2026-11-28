package com.mochimate.maid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiliBrainTest {
    @Test fun splitsLinesIntoBubbles() {
        assertEquals(listOf("おかえりなさい！", "今日おそかったですね…"), LiliBrain.split("おかえりなさい！\n\n今日おそかったですね…\n"))
    }

    @Test fun dropsSpeakerLabel() {
        assertEquals(listOf("えへへ"), LiliBrain.split("リリ：えへへ"))
    }

    @Test fun neverEmptyAndCapped() {
        assertEquals(listOf("…えへへ"), LiliBrain.split("  \n "))
        assertTrue(LiliBrain.split((1..10).joinToString("\n") { "行$it" }).size <= 4)
    }

    @Test fun personaKeepsKeyRules() {
        val p = LiliPersona.SYSTEM
        assertTrue(p.contains("リリ"))
        assertTrue(p.contains("箇条書き"))
        assertTrue(p.contains("よりそいホットライン"))
    }
}
