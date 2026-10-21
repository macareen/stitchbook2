package com.macareen.stitchbook2.data.backup

import com.macareen.stitchbook2.domain.backup.AddressFrameRecord
import com.macareen.stitchbook2.domain.backup.BackupSnapshot
import com.macareen.stitchbook2.domain.backup.CURRENT_BACKUP_FORMAT_VERSION
import com.macareen.stitchbook2.domain.backup.GuideBackupGraph
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideBackupJsonCodecTest {
    @Test fun castOnBackupValidationRejectsWrongOccurrenceAndOldFormat() {
        val counts = """[{"nodeId":"step","count":3,"frames":[{"containerId":"repeat","type":"repeat","value":2}]}]"""
        val graph = GuideBackupFixtures.graph.copy(revisions = GuideBackupFixtures.graph.revisions.map { revision -> revision.copy(nodes = revision.nodes.map { if (it.type == "INSTRUCTION") it.copy(instructionText = "Cast on 6 sts.") else it }) },
            executions = listOf(GuideBackupFixtures.execution.copy(castOnCountsJson = counts)))
        val incoming = BackupSnapshot(CURRENT_BACKUP_FORMAT_VERSION).withGuideGraph(graph)
        fun problems(value: BackupSnapshot) = com.macareen.stitchbook2.domain.backup.validateBackup(value, BackupSnapshot(CURRENT_BACKUP_FORMAT_VERSION), com.macareen.stitchbook2.domain.backup.RestoreMode.MERGE)
        org.junit.Assert.assertTrue(problems(incoming).isEmpty())
        org.junit.Assert.assertTrue(problems(incoming.copy(formatVersion = 4)).isNotEmpty())
        val bad = graph.copy(executions = graph.executions.map { it.copy(castOnCountsJson = counts.replace("\"value\":2", "\"value\":9")) })
        org.junit.Assert.assertTrue(problems(BackupSnapshot(CURRENT_BACKUP_FORMAT_VERSION).withGuideGraph(bad)).isNotEmpty())
    }

    @Test fun castOnCountsAreStructuredJsonAndLegacyMissingFieldDefaultsToEmpty() {
        val revision = com.macareen.stitchbook2.domain.execution.DefinitionRevisionId("revision-1")
        val address = com.macareen.stitchbook2.domain.execution.ExecutionAddress(revision,
            com.macareen.stitchbook2.domain.execution.NodeId("step"), listOf(com.macareen.stitchbook2.domain.execution.AncestryFrame.RepeatIteration(com.macareen.stitchbook2.domain.execution.NodeId("repeat"), 2)))
        val record = GuideBackupFixtures.execution.copy(castOnCountsJson = com.macareen.stitchbook2.domain.execution.CastOnCounts.encode(mapOf(address to 3)))
        val json = record.toJson()
        assertEquals(3, json.getJSONArray("castOnCounts").getJSONObject(0).getInt("count"))
        assertEquals(record, json.toExecutionRecord())
        json.remove("castOnCounts")
        assertEquals("[]", json.toExecutionRecord().castOnCountsJson)
    }

    @Test fun archiveVisibilityRoundTripsAndLegacyRecordsDefaultToActive() {
        val record = GuideBackupFixtures.graph.guides.first().copy(isArchived = true)
        val json = record.toJson()
        assertEquals(record, json.toGuideRecord())
        json.remove("isArchived")
        assertEquals(false, json.toGuideRecord().isArchived)
    }
    @Test fun archivedGuideGraphRoundTripsThroughTheFullBackup() {
        val graph = GuideBackupFixtures.graph.copy(guides = GuideBackupFixtures.graph.guides.map { it.copy(isArchived = true) })
        val snapshot = BackupSnapshot(formatVersion = CURRENT_BACKUP_FORMAT_VERSION).withGuideGraph(graph)
        assertEquals(graph, decodeBackup(encodeBackup(snapshot, exportedAt = 42)).guideGraph())
    }


    @Test
    fun theGuideGraphRoundTripsThroughJson() {
        val snapshot = BackupSnapshot(formatVersion = CURRENT_BACKUP_FORMAT_VERSION).withGuideGraph(GuideBackupFixtures.graph)

        val decoded = decodeBackup(encodeBackup(snapshot, exportedAt = 42))

        assertEquals(CURRENT_BACKUP_FORMAT_VERSION, decoded.formatVersion)
        assertEquals(GuideBackupFixtures.graph, decoded.guideGraph())
    }

    @Test
    fun nodeAndFrameOrderInTheFileDoesNotAffectEquality() {
        val shuffled = GuideBackupFixtures.graph.copy(
            revisions = GuideBackupFixtures.graph.revisions.map { it.copy(nodes = it.nodes.reversed()) },
            executions = GuideBackupFixtures.graph.executions.map {
                it.copy(currentFrames = it.currentFrames + AddressFrameRecord(1, "section", "RANGE_VALUE", 3)).let { execution ->
                    execution.copy(currentFrames = execution.currentFrames.reversed())
                }
            }
        )
        val snapshot = BackupSnapshot(formatVersion = CURRENT_BACKUP_FORMAT_VERSION).withGuideGraph(shuffled)

        val decoded = decodeBackup(encodeBackup(snapshot, exportedAt = 0))

        assertEquals(GuideBackupFixtures.graph.revisions, decoded.guideRevisions)
        assertEquals(listOf(0, 1), decoded.executions.orEmpty().single().currentFrames.map { it.frameOrder })
    }

    @Test
    fun theManifestCountsGuideRecords() {
        val snapshot = BackupSnapshot(formatVersion = CURRENT_BACKUP_FORMAT_VERSION).withGuideGraph(GuideBackupFixtures.graph)

        val counts = JSONObject(encodeBackup(snapshot, exportedAt = 0)).getJSONObject("manifest").getJSONObject("recordCounts")

        assertEquals(2, counts.getInt("GUIDES"))
        assertEquals(1, counts.getInt("EXECUTIONS"))
        assertEquals(1, counts.getInt("PROJECT_GUIDES"))
    }

    @Test
    fun aFormat2FileDecodesWithNoGuideTypes() {
        val json = """
            {"format":"stitchbook-backup","version":2,"exportedAt":0,"manifest":{},
             "projects":[{"id":"p","name":"n","craft":"KNITTING","projectType":"OTHER","status":"ACTIVE",
                          "notes":null,"createdAt":0,"updatedAt":0}]}
        """.trimIndent()

        val decoded = decodeBackup(json)

        assertEquals(2, decoded.formatVersion)
        assertEquals(1, decoded.projects?.size)
        assertNull(decoded.guideGraph())
        assertNull(decoded.guides)
        assertNull(decoded.guideDrafts)
        assertNull(decoded.guideRevisions)
        assertNull(decoded.executions)
        assertNull(decoded.activeExecutions)
        assertNull(decoded.projectGuides)
    }

    @Test
    fun anEmptyGuideGraphIsWrittenAsEmptyArraysNotOmitted() {
        val snapshot = BackupSnapshot(formatVersion = CURRENT_BACKUP_FORMAT_VERSION).withGuideGraph(GuideBackupGraph())

        val root = JSONObject(encodeBackup(snapshot, exportedAt = 0))

        listOf("guides", "guideDrafts", "guideRevisions", "executions", "activeExecutions", "projectGuides").forEach {
            assertEquals(it, 0, root.getJSONArray(it).length())
        }
    }
}
