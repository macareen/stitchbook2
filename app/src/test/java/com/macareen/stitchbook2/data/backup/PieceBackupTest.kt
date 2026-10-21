package com.macareen.stitchbook2.data.backup

import com.macareen.stitchbook2.domain.backup.*
import com.macareen.stitchbook2.domain.model.ProjectPiece
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PieceBackupTest {
    private val left = ProjectPiece("left", "project-1", "guide-1", "Left sleeve", false, 1, 2)
    private val right = left.copy(id = "right", name = "Right sleeve", isArchived = true)
    private fun graph(): GuideBackupGraph {
        val original = GuideBackupFixtures.graph
        val a = GuideBackupFixtures.execution.copy(id = "left-work", pieceId = left.id)
        val b = a.copy(id = "right-work", pieceId = right.id, updatedAt = a.updatedAt + 1)
        return original.copy(pieces = listOf(left, right), executions = listOf(a, b), activeExecutions = listOf(
            ActiveExecutionRecord(a.guideId, "project-1", a.id, left.id), ActiveExecutionRecord(b.guideId, "project-1", b.id, right.id)))
    }
    private fun snapshot(graph: GuideBackupGraph = graph()) = BackupSnapshot(CURRENT_BACKUP_FORMAT_VERSION).withGuideGraph(graph)
    @Test fun aPieceIdCannotChangeGuideOwnershipDuringImport() {
        val existing = BackupSnapshot(4, projectPieces = listOf(left.copy(guideId = "another-local-guide")))
        assertTrue(validateBackup(snapshot(), existing, RestoreMode.MERGE).any { it.type == BackupRecordType.PROJECT_PIECES })
    }
    @Test fun legacyProjectReplacementCannotCascadeUnmentionedPieceWork() {
        val incoming = BackupSnapshot(2, projects = emptyList())
        assertTrue(validateBackup(incoming, snapshot(), RestoreMode.REPLACE).any { it.type == BackupRecordType.PROJECT_PIECES })
        assertTrue(validateBackup(incoming, snapshot(), RestoreMode.MERGE).isEmpty())
    }
    @Test fun pieceIdentityArchiveAndSeparateActiveProgressRoundTrip() {
        val source = snapshot()
        assertEquals(source, decodeBackup(encodeBackup(source, 10)))
        assertTrue(validateBackup(source, BackupSnapshot(4), RestoreMode.MERGE).isEmpty())
        assertEquals(2, source.keysOf(BackupRecordType.ACTIVE_EXECUTIONS)?.toSet()?.size)
        assertEquals(2, source.counts()[BackupRecordType.PROJECT_PIECES])
    }
    @Test fun malformedPieceScopeIsRejectedBeforeRestore() {
        val original = graph()
        val wrong = original.copy(executions = original.executions.map { it.copy(projectId = "wrong") })
        assertTrue(validateBackup(snapshot(wrong), BackupSnapshot(4), RestoreMode.MERGE).any { it.type == BackupRecordType.EXECUTIONS })
        val badPointer = original.copy(activeExecutions = original.activeExecutions.map { it.copy(pieceKey = "wrong") })
        assertTrue(validateBackup(snapshot(badPointer), BackupSnapshot(4), RestoreMode.MERGE).any { it.type == BackupRecordType.ACTIVE_EXECUTIONS })
    }
    @Test fun missingPieceAndAnotherProjectsOwnedGuideAreRejected() {
        assertTrue(validateBackup(snapshot(graph().copy(pieces = emptyList())), BackupSnapshot(4), RestoreMode.MERGE).any { it.type == BackupRecordType.EXECUTIONS })
        val wrongOwner = graph().copy(pieces = listOf(left.copy(projectId = "other"), right))
        assertTrue(validateBackup(snapshot(wrongOwner), BackupSnapshot(4), RestoreMode.MERGE).any { it.type == BackupRecordType.PROJECT_PIECES })
    }
    @Test fun currentFormatCannotOmitThePieceSection() {
        val json = JSONObject(encodeBackup(snapshot(), 10)).apply { remove("projectPieces") }
        assertTrue(validateBackup(decodeBackup(json.toString()), BackupSnapshot(4), RestoreMode.MERGE).isNotEmpty())
    }
    @Test fun legacyGuideBackupStillReadsWithoutPieces() {
        val json = JSONObject(encodeBackup(BackupSnapshot(3).withGuideGraph(GuideBackupFixtures.graph), 10)).apply { put("version", 3); remove("projectPieces") }
        val decoded = decodeBackup(json.toString())
        assertEquals(emptyList<ProjectPiece>(), decoded.guideGraph()?.pieces)
        assertTrue(validateBackup(decoded, BackupSnapshot(4), RestoreMode.MERGE).isEmpty())
    }
    @Test fun newPieceDataCannotMasqueradeAsALegacyBackup() {
        assertTrue(validateBackup(snapshot().copy(formatVersion = 3), BackupSnapshot(4), RestoreMode.MERGE).isNotEmpty())
    }
    @Test fun missingProjectMergeReportsPieceLossAndKeepsTheProgress() {
        val plan = requireNotNull(planGuideMerge(snapshot(), BackupSnapshot(4)))
        assertTrue(plan.graph.pieces.isEmpty())
        assertEquals(2, plan.graph.executions.size)
        assertTrue(plan.graph.executions.all { it.pieceId == null && it.projectId == null })
        assertTrue(plan.notices.any { it.type == BackupRecordType.PROJECT_PIECES })
    }
    @Test fun scopeIdentityCannotCollideWithSeparatorsInsideIds() {
        assertNotEquals(activeExecutionKey("g", "p/x", ""), activeExecutionKey("g/p", "x", ""))
        assertNotEquals(activeExecutionKey("g", "p", "left"), activeExecutionKey("g", "p", "right"))
        assertNotEquals(activeExecutionKey("g", "p/left", ""), activeExecutionKey("g", "p", "left"))
    }
}
