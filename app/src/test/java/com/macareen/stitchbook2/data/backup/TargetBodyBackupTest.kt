package com.macareen.stitchbook2.data.backup

import com.macareen.stitchbook2.domain.backup.*
import com.macareen.stitchbook2.domain.execution.*
import org.junit.Assert.*
import org.junit.Test

class TargetBodyBackupTest {
    private val target = ManualTarget(TargetUnit.INCHES, 4.5)
    private val nodes = listOf(
        GuideBackupFixtures.node("body", null, 0, "SECTION").copy(targetJson = ManualTargets.encode(target)),
        GuideBackupFixtures.node("a", "body", 0, "INSTRUCTION").copy(instructionText = "Knit (p.2 l.1)"),
        GuideBackupFixtures.node("b", "body", 1, "INSTRUCTION").copy(instructionText = "Purl (p.2 l.2)"))
    private val address = ExecutionAddress(DefinitionRevisionId("revision-1"), NodeId("a"))
    private val execution = GuideBackupFixtures.execution.copy(currentInstructionNodeId = "b", currentFrames = emptyList(),
        completedOccurrences = listOf(CompletedOccurrenceRecord("1:a1:0", "a", emptyList())),
        targetWorkJson = TargetWorkCodec.encode(mapOf(address to TargetWork(2, listOf(TargetObservation(1, 2.0), TargetObservation(2, 4.5))))))
    private fun snapshot() = BackupSnapshot(CURRENT_BACKUP_FORMAT_VERSION).withGuideGraph(GuideBackupFixtures.graph.copy(
        drafts = listOf(GuideBackupFixtures.draft.copy(nodes = nodes)),
        revisions = listOf(GuideBackupFixtures.revision.copy(nodes = nodes)), executions = listOf(execution)))

    @Test fun bodyIdentityWordingProvenanceAndPartialCycleRoundTrip() {
        val source = snapshot()
        val decoded = decodeBackup(encodeBackup(source, 10))
        assertEquals(source, decoded)
        assertTrue(validateBackup(decoded, BackupSnapshot(7), RestoreMode.MERGE).toString(), validateBackup(decoded, BackupSnapshot(7), RestoreMode.MERGE).isEmpty())
        val revision = decoded.guideRevisions!!.single()
        val traversal = GuideTraversal(GuideDefinitionValidator.validate(backupDefinition(revision)))
        assertEquals(listOf("Knit (p.2 l.1)", "Purl (p.2 l.2)"), traversal.occurrences().map { it.instruction.text }.toList())
        assertEquals(target, traversal.targetCycle(address)!!.target)
    }

    @Test fun formatSixCannotClaimBodySemanticsAndInvalidDraftBodiesAreRejected() {
        assertTrue(validateBackup(snapshot().copy(formatVersion = 6), BackupSnapshot(7), RestoreMode.MERGE).isNotEmpty())
        val bad = snapshot().copy(guideDrafts = listOf(GuideBackupFixtures.draft.copy(nodes = nodes.map {
            if (it.nodeId == "b") it.copy(instructionText = "Purl while shaping") else it
        })))
        assertTrue(validateBackup(bad, BackupSnapshot(7), RestoreMode.MERGE).isNotEmpty())
    }

    @Test fun unfinishedUnrelatedDraftSectionsRemainRestorable() {
        val incomplete = snapshot().copy(guideDrafts = listOf(GuideBackupFixtures.draft.copy(nodes = nodes +
            GuideBackupFixtures.node("unfinished", null, 1, "SECTION"))))
        assertTrue(validateBackup(incomplete, BackupSnapshot(7), RestoreMode.MERGE).toString(),
            validateBackup(incomplete, BackupSnapshot(7), RestoreMode.MERGE).isEmpty())
    }
}
