package com.macareen.stitchbook2.domain.model

import kotlin.math.roundToInt

/** The kinds of sized tool shown side by side in the needle grid. */
enum class NeedleColumn {
    STRAIGHT,
    CIRCULAR,
    DPN,
    INTERCHANGEABLE,
    HOOK;

    companion object {
        fun of(category: ToolCategory): NeedleColumn? = when (category) {
            ToolCategory.STRAIGHT_NEEDLES -> STRAIGHT
            ToolCategory.CIRCULAR_NEEDLES -> CIRCULAR
            ToolCategory.DPN_SET -> DPN
            ToolCategory.INTERCHANGEABLE_TIP -> INTERCHANGEABLE
            ToolCategory.CROCHET_HOOK, ToolCategory.TUNISIAN_HOOK -> HOOK
            else -> null
        }
    }
}

/** One size row: how many of each kind you own at that size (0 means a gap). */
data class NeedleGridRow(
    val sizeMm: Double,
    /** The usual US needle number for this size, when there is one. */
    val usLabel: String?,
    val counts: Map<NeedleColumn, Int>
)

/**
 * A size × kind overview of needles and hooks, like a needle-case chart: rows
 * are metric sizes, columns are the kinds you own. Standard sizes between your
 * smallest and largest are included even when you own none, so gaps show.
 */
data class NeedleGrid(
    val columns: List<NeedleColumn>,
    val rows: List<NeedleGridRow>
) {
    val isEmpty: Boolean get() = rows.isEmpty()

    companion object {
        val EMPTY = NeedleGrid(emptyList(), emptyList())

        private val standardSizesMm = listOf(
            2.0, 2.25, 2.5, 2.75, 3.0, 3.25, 3.5, 3.75, 4.0, 4.5, 5.0, 5.5, 6.0, 6.5,
            7.0, 8.0, 9.0, 10.0, 12.0, 15.0, 20.0, 25.0
        )

        private val usNeedleNumbers = mapOf(
            2.0 to "0", 2.25 to "1", 2.5 to "1.5", 2.75 to "2", 3.0 to "2.5", 3.25 to "3",
            3.5 to "4", 3.75 to "5", 4.0 to "6", 4.5 to "7", 5.0 to "8", 5.5 to "9",
            6.0 to "10", 6.5 to "10.5", 8.0 to "11", 9.0 to "13", 10.0 to "15",
            12.0 to "17", 15.0 to "19", 20.0 to "36", 25.0 to "50"
        )

        fun from(items: List<ToolItem>): NeedleGrid {
            val sized = items.mapNotNull { item ->
                val column = NeedleColumn.of(item.category) ?: return@mapNotNull null
                val size = item.sizeMetricMm?.takeIf { it > 0.0 } ?: return@mapNotNull null
                Triple(roundToQuarter(size), column, item.quantity.coerceAtLeast(1))
            }
            if (sized.isEmpty()) return EMPTY

            val columns = NeedleColumn.entries.filter { column -> sized.any { it.second == column } }
            val owned = sized.map { it.first }.toSet()
            val smallest = owned.min()
            val largest = owned.max()
            val sizes = (standardSizesMm.filter { it in smallest..largest } + owned).toSortedSet()

            val rows = sizes.map { size ->
                val counts = columns.associateWith { column ->
                    sized.filter { it.first == size && it.second == column }.sumOf { it.third }
                }
                NeedleGridRow(sizeMm = size, usLabel = usNeedleNumbers[size], counts = counts)
            }
            return NeedleGrid(columns, rows)
        }

        /** Sizes are compared to the nearest quarter millimetre so 3.5 and 3.50001 share a row. */
        private fun roundToQuarter(sizeMm: Double): Double = (sizeMm * 4).roundToInt() / 4.0
    }
}
