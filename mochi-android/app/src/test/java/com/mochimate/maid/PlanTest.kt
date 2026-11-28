package com.mochimate.maid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class PlanTest {
    private val day = LocalDate.of(2026, 12, 3)

    @Test fun remindBeforeStart() {
        val p = Plan(1, "面接", day, LocalTime.of(15, 0), 30)
        assertEquals(LocalDateTime.of(2026, 12, 3, 14, 30), p.remindAt())
        assertEquals("15:00", p.timeText())
    }

    @Test fun allDayRemindsAtEight() {
        val p = Plan(2, "誕生日", day, null, 30)
        assertEquals(LocalDateTime.of(2026, 12, 3, 8, 0), p.remindAt())
        assertEquals("終日", p.timeText())
    }

    @Test fun noReminder() {
        assertNull(Plan(3, "x", day, LocalTime.NOON, -1).remindAt())
    }
}
