package com.macareen.stitchbook2.data.backup

import com.macareen.stitchbook2.domain.backup.*
import com.macareen.stitchbook2.domain.execution.*
import org.junit.Assert.*
import org.junit.Test

class ManualTargetBackupTest {
    private val target = ManualTarget(TargetUnit.INCHES, 4.5)
    private val node = GuideBackupFixtures.node("cycle", null, 0, "INSTRUCTION").copy(targetJson = ManualTargets.encode(target))
    private val address = ExecutionAddress(DefinitionRevisionId("revision-1"), NodeId("cycle"))
    private val execution = GuideBackupFixtures.execution.copy(currentInstructionNodeId = "cycle", currentFrames = emptyList(),
        completedOccurrences = emptyList(), targetWorkJson = TargetWorkCodec.encode(mapOf(address to TargetWork(2, listOf(TargetObservation(1, 2.0), TargetObservation(2, 4.5))))))
    private fun snapshot(work: ExecutionRecord = execution) = BackupSnapshot(CURRENT_BACKUP_FORMAT_VERSION).withGuideGraph(
        GuideBackupFixtures.graph.copy(drafts = listOf(GuideBackupFixtures.draft.copy(nodes = listOf(node))),
            revisions = listOf(GuideBackupFixtures.revision.copy(nodes = listOf(node))), executions = listOf(work)))

    @Test fun targetsAndAllCycleObservationsSurvivePortableJson() {
        val source = snapshot()
        val decoded = decodeBackup(encodeBackup(source, 10))
        assertEquals(source, decoded)
        assertTrue(validateBackup(decoded, BackupSnapshot(6), RestoreMode.MERGE).isEmpty())
        assertEquals(target, ManualTargets.decode(decoded.guideRevisions!!.single().nodes.single().targetJson!!))
        assertEquals(2, TargetWorkCodec.decode(decoded.executions!!.single().targetWorkJson, address.definitionRevisionId).getValue(address).observations.size)
    }

    @Test fun targetDataCannotBeSmuggledIntoOlderBackupFormats() {
        assertTrue(validateBackup(snapshot().copy(formatVersion = 5), BackupSnapshot(6), RestoreMode.MERGE).isNotEmpty())
        val old = BackupSnapshot(5).withGuideGraph(GuideBackupFixtures.graph)
        val legacyJson = org.json.JSONObject(encodeBackup(old, 10)).put("version", 5)
        assertEquals(old, decodeBackup(legacyJson.toString()))
        assertTrue(validateBackup(old, BackupSnapshot(6), RestoreMode.MERGE).isEmpty())
    }

    @Test fun invalidUnitsMalformedObservationsAndWrongOccurrenceAreRejectedBeforeRestore() {
        val badNode = node.copy(targetJson = "{\"unit\":\"ROWS\",\"value\":1}")
        val bad = snapshot().copy(guideRevisions = listOf(GuideBackupFixtures.revision.copy(nodes = listOf(badNode))))
        assertTrue(validateBackup(bad, BackupSnapshot(6), RestoreMode.MERGE).isNotEmpty())
        val wrong = execution.copy(targetWorkJson = TargetWorkCodec.encode(mapOf(address.copy(instructionNodeId = NodeId("missing")) to TargetWork())))
        assertTrue(validateBackup(snapshot(wrong), BackupSnapshot(6), RestoreMode.MERGE).isNotEmpty())
        assertTrue(validateBackup(snapshot(execution.copy(targetWorkJson = "invalid")), BackupSnapshot(6), RestoreMode.MERGE).isNotEmpty())
    }

    @Test fun completedTargetRequiresEvidenceAndExplicitReachedFlag() {
        val signature = "5:cycle1:0"
        val completed = execution.copy(status = "COMPLETED", currentInstructionNodeId = null, completedAt = 200,
            completedOccurrences = listOf(CompletedOccurrenceRecord(signature, "cycle", emptyList())))
        assertTrue(validateBackup(snapshot(completed).copy(activeExecutions = emptyList()), BackupSnapshot(6), RestoreMode.MERGE).isNotEmpty())
        val confirmed = completed.copy(targetWorkJson = TargetWorkCodec.encode(mapOf(address to TargetWork(2, listOf(TargetObservation(2, 4.5)), true))))
        assertTrue(validateBackup(snapshot(confirmed).copy(activeExecutions = emptyList()), BackupSnapshot(6), RestoreMode.MERGE).isEmpty())
    }
}
