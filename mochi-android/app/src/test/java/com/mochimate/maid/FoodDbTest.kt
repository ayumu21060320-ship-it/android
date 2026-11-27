package com.mochimate.maid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FoodDbTest {

    private val rows: Map<String, List<String>> by lazy {
        File("src/main/assets/foods.tsv").readLines(Charsets.UTF_8).drop(1)
            .map { it.split('\t') }.associateBy { it[0] }
    }

    @Test fun dataLooksComplete() {
        assertTrue("食品数 ${rows.size}", rows.size > 2400)
        // ごはん(精白米 うるち米)は 100g あたり 156kcal
        assertEquals("156", rows["01088"]!![3])
    }

    @Test fun everyAliasPointsToARealFood() {
        for (a in FoodDb.ALIASES) {
            assertTrue("${a.words.first()} → ${a.foodId} が成分表にない", rows.containsKey(a.foodId))
            assertTrue("${a.words.first()} の量", a.grams > 0)
        }
    }

    @Test fun normalizeIgnoresKanaAndSpaces() {
        assertEquals(FoodDb.normalize("ばなな"), FoodDb.normalize("バナナ"))
        assertEquals("こめ水稲めし", FoodDb.normalize("こめ ［水稲めし］"))
    }

    @Test fun scaledNutrition() {
        val f = Food("x", "x", 156.0, 2.5, 0.3, 37.1, 1.5, 0.0)
        val n = FoodDb.scaled(f, 150.0)
        assertEquals(234, n.kcal)
        assertEquals(3.75, n.p, 0.001)
    }
}
