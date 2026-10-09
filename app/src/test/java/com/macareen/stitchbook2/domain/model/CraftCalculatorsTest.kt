package com.macareen.stitchbook2.domain.model

import com.macareen.stitchbook2.domain.model.CraftCalculators.YarnUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CraftCalculatorsTest {

    /** Stitches the plan uses on the needle before shaping, to check nothing is lost. */
    private fun ShapingPlan.stitchesWorked(): Int {
        val perShaping = if (isIncrease) 0 else 2
        return before + after + perShaping * count + runs.sumOf { it.plain * it.times }
    }

    @Test
    fun hundredToHundredTwentyIsOneIncreaseEveryFiveStitches() {
        val plan = CraftCalculators.distribute(100, 120, centered = false)!!

        assertEquals(20, plan.count)
        assertEquals(5, plan.before)
        assertEquals(listOf(ShapingRun(plain = 5, times = 19)), plan.runs)
        assertEquals(0, plan.after)
        assertEquals(100, plan.stitchesWorked())
    }

    @Test
    fun anUnevenSpreadUsesTwoGapSizesThatDifferByOne() {
        val plan = CraftCalculators.distribute(100, 107, centered = false)!!

        assertEquals(setOf(14, 15), plan.runs.map { it.plain }.toSet() + plan.before)
        assertEquals(100, plan.stitchesWorked())
    }

    @Test
    fun centeringSharesTheLeftoverBetweenBothEdges() {
        val plan = CraftCalculators.distribute(100, 110, centered = true)!!

        assertEquals(100, plan.stitchesWorked())
        assertTrue(kotlin.math.abs(plan.before - plan.after) <= 1)
        assertEquals(9, plan.runs.sumOf { it.times })
    }

    @Test
    fun decreasesWorkTwoTogetherAndKeepEveryStitch() {
        val plan = CraftCalculators.distribute(100, 80, centered = false)!!

        assertEquals(false, plan.isIncrease)
        assertEquals(20, plan.count)
        assertEquals(100, plan.stitchesWorked())
        assertEquals(listOf(ShapingRun(plain = 3, times = 19)), plan.runs)
    }

    @Test
    fun impossibleOrEmptyChangesHaveNoPlan() {
        assertNull(CraftCalculators.distribute(10, 10, centered = false))
        assertNull(CraftCalculators.distribute(10, 2, centered = false))
    }

    @Test
    fun yarnConverterRoundsUpToWholeSkeins() {
        // 5 skeins of 200 m is 1094 yd; skeins of 220 yd need 5 (4.97 rounds up).
        assertEquals(5, CraftCalculators.skeinsNeeded(5.0, 200.0, YarnUnit.METRES, 220.0, YarnUnit.YARDS))
        assertEquals(4, CraftCalculators.skeinsNeeded(7.0, 50.0, YarnUnit.GRAMS, 100.0, YarnUnit.GRAMS))
        assertNull(CraftCalculators.skeinsNeeded(5.0, 50.0, YarnUnit.GRAMS, 200.0, YarnUnit.METRES))
    }

    @Test
    fun swatchAdapterScalesByYourGauge() {
        // Pattern 22 sts / 10 cm, yours 20: 110 sts become 100.
        assertEquals(100, CraftCalculators.adaptCount(110, 22.0, 20.0))
        assertNull(CraftCalculators.adaptCount(110, 0.0, 20.0))
    }
}
