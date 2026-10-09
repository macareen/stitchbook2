package com.macareen.stitchbook2.domain.model

import kotlin.math.ceil
import kotlin.math.roundToInt

/** One run of an even spread: work [plain] stitches then the shaping stitch, [times] times. */
data class ShapingRun(val plain: Int, val times: Int)

/**
 * How to spread increases or decreases evenly across a row, as one or two
 * runs (the remainder is shared out so the gaps differ by at most one), plus
 * the plain stitches before the first shaping and after the last.
 */
data class ShapingPlan(
    val isIncrease: Boolean,
    val count: Int,
    val before: Int,
    val runs: List<ShapingRun>,
    val after: Int
)

/** Pure craft arithmetic for the calculators: no Android, no storage. */
object CraftCalculators {

    /**
     * Spreads the change from [current] to [desired] stitches evenly.
     * An increase adds one stitch per shaping; a decrease works two stitches
     * together. [centered] splits the leftover gap between both ends, which
     * suits flat pieces; otherwise the row ends right after the last shaping
     * (or with the leftover), which suits the round.
     * Returns null when nothing changes or a decrease can't fit.
     */
    fun distribute(current: Int, desired: Int, centered: Boolean): ShapingPlan? {
        if (current <= 0 || desired <= 0 || current == desired) return null
        val isIncrease = desired > current
        val count = kotlin.math.abs(desired - current)
        // Stitches worked plain between shapings; a decrease uses 2 stitches itself.
        val plainTotal = if (isIncrease) current else current - 2 * count
        if (plainTotal < 0) return null

        // Gaps of plain stitches: one before each shaping, and when centred one
        // more at the end, so the leftover is shared by both edges.
        val gaps = count + if (centered) 1 else 0
        val gapSizes = MutableList(gaps) { plainTotal / gaps }
        // Extra stitches go to the inner gaps first, so the edges stay the shortest.
        val firstInner = if (centered && gaps > 2) 1 else 0
        repeat(plainTotal % gaps) { i -> gapSizes[firstInner + i % (gaps - firstInner)] += 1 }

        // Read as: work `before`, shape, then each run (plain, shape), then `after`.
        val before: Int
        val after: Int
        val between: List<Int>
        if (centered) {
            val ends = gapSizes.first() + gapSizes.last()
            before = ends / 2
            after = ends - before
            between = gapSizes.subList(1, gaps - 1)
        } else {
            before = gapSizes.first()
            after = 0
            between = gapSizes.drop(1)
        }
        val runs = between.groupingBy { it }.eachCount()
            .entries.sortedBy { it.key }
            .map { ShapingRun(plain = it.key, times = it.value) }
        return ShapingPlan(isIncrease, count, before, runs, after)
    }

    enum class YarnUnit { METRES, YARDS, GRAMS }

    private const val METRES_PER_YARD = 0.9144

    /** Skeins of your yarn needed to match the pattern's; null when the units can't be compared. */
    fun skeinsNeeded(
        patternSkeins: Double,
        patternPerSkein: Double,
        patternUnit: YarnUnit,
        yourPerSkein: Double,
        yourUnit: YarnUnit
    ): Int? {
        if (patternSkeins <= 0 || patternPerSkein <= 0 || yourPerSkein <= 0) return null
        val total = patternSkeins * patternPerSkein
        val totalInYourUnit = convert(total, patternUnit, yourUnit) ?: return null
        return ceil(totalInYourUnit / yourPerSkein - 1e-9).toInt()
    }

    /** Length converts both ways; grams only to grams (weight says nothing about length without the yarn's ratio). */
    fun convert(amount: Double, from: YarnUnit, to: YarnUnit): Double? = when {
        from == to -> amount
        from == YarnUnit.GRAMS || to == YarnUnit.GRAMS -> null
        from == YarnUnit.METRES -> amount / METRES_PER_YARD
        else -> amount * METRES_PER_YARD
    }

    /** A pattern count scaled from its gauge to yours (both per the same width or height). */
    fun adaptCount(patternCount: Int, patternGauge: Double, yourGauge: Double): Int? {
        if (patternCount <= 0 || patternGauge <= 0 || yourGauge <= 0) return null
        return (patternCount * yourGauge / patternGauge).roundToInt()
    }
}
