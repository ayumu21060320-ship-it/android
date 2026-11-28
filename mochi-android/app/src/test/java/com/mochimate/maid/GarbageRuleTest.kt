package com.mochimate.maid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GarbageRuleTest {
    private val burnable = GarbageRule("燃えるゴミ", "🔥", setOf(1, 4), emptySet()) // 毎週 月・木
    private val recycle = GarbageRule("資源ゴミ", "♻️", setOf(3), setOf(2, 4))      // 第2・第4 水

    @Test fun weekly() {
        assertTrue(burnable.matches(LocalDate.of(2026, 12, 3)))  // 木
        assertTrue(burnable.matches(LocalDate.of(2026, 11, 30))) // 月
        assertFalse(burnable.matches(LocalDate.of(2026, 12, 2))) // 水
    }

    @Test fun nthWeekday() {
        // 2026年12月の水曜: 2, 9, 16, 23, 30 → 第2=9日, 第4=23日
        assertFalse(recycle.matches(LocalDate.of(2026, 12, 2)))
        assertTrue(recycle.matches(LocalDate.of(2026, 12, 9)))
        assertFalse(recycle.matches(LocalDate.of(2026, 12, 16)))
        assertTrue(recycle.matches(LocalDate.of(2026, 12, 23)))
        assertFalse(recycle.matches(LocalDate.of(2026, 12, 30)))
    }

    @Test fun describe() {
        assertEquals("毎週 月・木", burnable.describe())
        assertEquals("第2・第4 水", recycle.describe())
    }
}
