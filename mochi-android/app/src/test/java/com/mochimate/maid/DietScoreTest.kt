package com.mochimate.maid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DietScoreTest {
    private val goal = 2000
    private val gp = goal * 0.20 / 4  // 100g
    private val gf = goal * 0.25 / 9  // 約55.6g
    private val gc = goal * 0.55 / 4  // 275g

    @Test fun perfectDayIs100() {
        val s = DietScore.compute(DayTotal(1950, gp, gf, gc), goal, gp, gf, gc)!!
        assertEquals(100, s.total)
    }

    @Test fun noRecordIsNull() {
        assertNull(DietScore.compute(DayTotal(0, 0.0, 0.0, 0.0), goal, gp, gf, gc))
    }

    @Test fun overeatingLosesCaloriePoints() {
        val s = DietScore.compute(DayTotal(2800, gp, gf, gc), goal, gp, gf, gc)!!
        assertTrue("kcal ${s.kcal}", s.kcal < 40)
        assertEquals(60, s.p + s.f + s.c)
    }

    @Test fun lowProteinLosesProteinPoints() {
        val s = DietScore.compute(DayTotal(2000, gp * 0.4, gf, gc), goal, gp, gf, gc)!!
        assertTrue("p ${s.p}", s.p < 20)
    }
}
