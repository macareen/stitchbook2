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

    @Test
    fun theGuideGraphRoundTripsThroughJson() {
        val snapshot = BackupSnapshot(formatVersion = CURRENT_BACKUP_FORMAT_VERSION).withGuideGraph(GuideBackupFixtures.graph)

        val decoded = decodeBackup(encodeBackup(snapshot, exportedAt = 42))

        assertEquals(3, decoded.formatVersion)
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
