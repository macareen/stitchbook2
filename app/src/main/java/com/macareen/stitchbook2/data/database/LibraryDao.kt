package com.macareen.stitchbook2.data.database

import com.macareen.stitchbook2.domain.model.PatternPdfChangeResult
import com.macareen.stitchbook2.domain.model.PickedDocument
import com.macareen.stitchbook2.domain.model.LibraryItem
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import com.macareen.stitchbook2.domain.model.PatternRemovalPreview
import com.macareen.stitchbook2.domain.model.PatternRemovalProject
import com.macareen.stitchbook2.domain.model.PatternRemovalResult
import com.macareen.stitchbook2.domain.model.ProjectStatus
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LibraryDao {

    @Query(
        """
        SELECT * FROM library_items
        ORDER BY updated_at DESC, title COLLATE NOCASE ASC, id ASC
        """
    )
    abstract fun observeAll(): Flow<List<LibraryItemEntity>>

    @Query("SELECT * FROM library_items WHERE id = :id LIMIT 1")
    abstract fun observeById(id: String): Flow<LibraryItemEntity?>

    @Upsert
    abstract suspend fun upsert(item: LibraryItemEntity)

    @Delete
    abstract suspend fun delete(item: LibraryItemEntity)

    @Query("SELECT * FROM library_items WHERE id = :id")
    protected abstract suspend fun getItem(id: String): LibraryItemEntity?

    @Transaction
    open suspend fun saveIfUnchanged(expected: LibraryItem, replacement: LibraryItem): Boolean {
        require(replacement.id == expected.id)
        if (getItem(expected.id)?.toDomain() != expected) return false
        upsert(replacement.toEntity())
        return true
    }

    @Transaction
    open suspend fun changePdfReviewed(
        expected: LibraryItem,
        document: PickedDocument?,
        now: Long
    ): PatternPdfChangeResult {
        val current = getItem(expected.id)?.toDomain()
            ?: return PatternPdfChangeResult.Missing
        if (current != expected) return PatternPdfChangeResult.Changed(current)
        upsert(current.copy(pdfUri = document?.uri, pdfFileName = document?.displayName,
            pdfLastViewedPage = if (document != null && document.uri == current.pdfUri) current.pdfLastViewedPage else null,
            updatedAt = now).toEntity())
        return PatternPdfChangeResult.Applied
    }

    @Query("SELECT id FROM guides WHERE library_item_id = :id ORDER BY id")
    protected abstract suspend fun getGuideIds(id: String): List<String>

    @Query("""
        SELECT id, name, status FROM projects WHERE id IN (
            SELECT project_id FROM project_pattern_links WHERE library_item_id = :id
            UNION SELECT project_id FROM guides WHERE library_item_id = :id
            UNION SELECT pg.project_id FROM project_guides pg JOIN guides g ON g.id = pg.guide_id WHERE g.library_item_id = :id
            UNION SELECT e.project_id FROM executions e JOIN guides g ON g.id = e.guide_id WHERE g.library_item_id = :id
            UNION SELECT pp.project_id FROM project_pieces pp JOIN guides g ON g.id = pp.guide_id WHERE g.library_item_id = :id
        ) ORDER BY name COLLATE NOCASE, id
    """)
    protected abstract suspend fun getDependentProjects(id: String): List<PatternRemovalProjectRow>

    @Transaction
    open suspend fun previewRemoval(id: String): PatternRemovalPreview? {
        val item = getItem(id) ?: return null
        return PatternRemovalPreview(item.toDomain(), getGuideIds(id), getDependentProjects(id).map {
            PatternRemovalProject(it.id, it.name, checkNotNull(ProjectStatus.fromStorageValue(it.status)))
        })
    }

    @Transaction
    open suspend fun removeReviewed(preview: PatternRemovalPreview): PatternRemovalResult {
        val current = previewRemoval(preview.item.id) ?: return PatternRemovalResult.Missing
        if (current != preview) return PatternRemovalResult.Changed(current)
        delete(current.item.toEntity())
        return PatternRemovalResult.Removed
    }
}

data class PatternRemovalProjectRow(val id: String, val name: String, val status: String)
