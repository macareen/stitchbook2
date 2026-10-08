package com.macareen.stitchbook2.domain.execution

import com.macareen.stitchbook2.domain.execution.ExecutionEngineFixtures.nodeId
import com.macareen.stitchbook2.domain.execution.ExecutionEngineFixtures.validated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideProgressSummaryTest {

    @Test
    fun `stitch counts are read in the usual forms`() {
        assertEquals(24, StitchCount.from("Knit to end. (24 sts)"))
        assertEquals(64, StitchCount.from("Row 11: K3, knit to end. (64 sts) (p.1)"))
        assertEquals(60, StitchCount.from("Dec 6 sts evenly [60 stitches]"))
        assertEquals(48, StitchCount.from("Purl across — 48 sts."))
        assertEquals(66, StitchCount.from("Cast on 66 sts. (p.1)"))
        assertNull(StitchCount.from("Knit to end."))
        assertNull(StitchCount.from("Row 2 (RS): Knit."))
    }

    @Test
    fun `progress is weighted by stitches, carrying the last count forward`() {
        // Cast on 10, then 2 rows at 10 sts, then a row that grows to 30.
        val guide = validated(
            listOf(nodeId("cast-on"), nodeId("rows"), nodeId("grow")),
            Instruction(nodeId("cast-on"), "Cast on 10 sts."),
            Range(nodeId("rows"), "row", 1, 2, listOf(nodeId("knit"))),
            Instruction(nodeId("knit"), "Knit."),
            Instruction(nodeId("grow"), "Inc in every st twice. (30 sts)")
        )
        val addresses = GuideTraversal(guide).occurrences().map { it.address }.toList()

        val halfway = GuideProgressSummary.of(guide, addresses.take(3).toSet())

        assertEquals(3, halfway.completedSteps)
        assertEquals(4, halfway.totalSteps)
        assertEquals(30.0 / 60.0, halfway.fraction, 1e-9)
        assertEquals(50, halfway.percent)
        assertEquals(ProgressBasis.STITCHES_ESTIMATED, halfway.basis)
    }

    @Test
    fun `without any counts every step weighs the same`() {
        val guide = validated(
            listOf(nodeId("rows")),
            Range(nodeId("rows"), "round", 1, 4, listOf(nodeId("knit"))),
            Instruction(nodeId("knit"), "Knit.")
        )
        val addresses = GuideTraversal(guide).occurrences().map { it.address }.toList()

        val summary = GuideProgressSummary.of(guide, addresses.take(1).toSet())

        assertEquals(25, summary.percent)
        assertEquals(ProgressBasis.STEPS, summary.basis)
    }

    @Test
    fun `the overview lists sections, spans and repeats with their status and where to jump`() {
        val guide = validated(
            listOf(nodeId("body")),
            Section(nodeId("body"), "Body", listOf(nodeId("cast-on"), nodeId("rows"), nodeId("lace"))),
            Instruction(nodeId("cast-on"), "Cast on 10 sts."),
            Range(nodeId("rows"), "row", 1, 3, listOf(nodeId("knit"))),
            Instruction(nodeId("knit"), "Knit."),
            Repeat(nodeId("lace"), 2, "Rows 4-5", listOf(nodeId("yo"))),
            Instruction(nodeId("yo"), "Yo, k2tog.")
        )
        val addresses = GuideTraversal(guide).occurrences().map { it.address }.toList()
        val completed = addresses.take(2).toSet()

        val overview = OverviewEntry.overviewOf(guide, completed, currentAddress = addresses[2])

        assertEquals(
            listOf(
                Triple(0, "Body", OverviewStatus.CURRENT),
                Triple(1, "Cast on 10 sts.", OverviewStatus.DONE),
                Triple(1, "Rows 1–3: Knit.", OverviewStatus.CURRENT),
                Triple(1, "Rows 4-5 × 2", OverviewStatus.TO_DO),
                Triple(2, "Yo, k2tog.", OverviewStatus.TO_DO)
            ),
            overview.map { Triple(it.depth, it.text, it.status) }
        )
        val rows = overview[2]
        assertEquals(1 to 3, rows.completedSteps to rows.totalSteps)
        assertEquals(addresses[2], rows.jumpTarget)
        assertEquals(addresses[4], overview[3].jumpTarget)
    }
}
