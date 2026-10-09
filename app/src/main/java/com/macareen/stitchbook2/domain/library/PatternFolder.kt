package com.macareen.stitchbook2.domain.library

import com.macareen.stitchbook2.domain.model.Craft
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.flow.first

/** One PDF found in the pattern folder. The file stays where it is; only its address is kept. */
data class FolderPdf(val documentUri: String, val displayName: String)

/**
 * The folder the person keeps their patterns in (on the phone, or a cloud
 * folder their phone can open). Existing files are never copied, moved,
 * renamed, replaced, or deleted; the only write is saving a new file the
 * person asked for (a pattern PDF downloaded from Ravelry).
 */
interface PatternFolder {
    /** The chosen folder's address, or null when none is chosen. */
    fun folderUri(): String?

    /** A readable name for the chosen folder ("Patterns"), when the provider gives one. */
    suspend fun folderName(): String?

    /** Keeps access to [uri] across restarts. Returns false when the provider won't allow it. */
    suspend fun choose(uri: String): Boolean
    suspend fun forget()

    /** Every PDF in the folder and its subfolders. Throws when the folder can no longer be read. */
    suspend fun listPdfs(): List<FolderPdf>

    /** Whether the folder's grant lets Stitchbook save new files into it. */
    fun canSave(): Boolean

    /**
     * Creates a new PDF named [displayName] at the top of the folder and lets
     * [write] fill it. Throws [PatternFileExistsException] rather than replace
     * a file with that name; if [write] fails, the half-written new file is
     * removed again.
     */
    suspend fun saveNewPdf(displayName: String, write: suspend (OutputStream) -> Unit): FolderPdf
}

/** The folder already has a file with this name; it was left as it is. */
class PatternFileExistsException(val displayName: String) : IOException("A file with this name is already in the pattern folder.")

/** What one folder check found. */
data class PatternFolderSyncResult(
    val added: Int,
    /** Library entries from the folder whose file is no longer there. Kept, never deleted. */
    val missing: Int
)

/**
 * Adds a Library entry for each PDF in the pattern folder that the Library
 * doesn't already have. A file counts as already present when an entry
 * points at the same address or has the same file name, so PDFs the person
 * attached by hand are not duplicated. Existing entries are never changed
 * or removed.
 */
object PatternFolderPlanner {

    fun newItems(pdfs: List<FolderPdf>, existing: List<LibraryItem>, now: Long, newId: () -> String): List<LibraryItem> {
        val knownUris = existing.mapNotNull { it.pdfUri }.toSet()
        val knownNames = existing.mapNotNull { it.pdfFileName?.lowercase() }.toMutableSet()
        return pdfs.mapNotNull { pdf ->
            val name = pdf.displayName.lowercase()
            if (pdf.documentUri in knownUris || name in knownNames) return@mapNotNull null
            knownNames += name
            LibraryItem(
                id = newId(),
                title = titleFor(pdf.displayName),
                craft = Craft.OTHER,
                author = null,
                sourceUrl = null,
                tags = emptyList(),
                notes = null,
                bookmarked = false,
                createdAt = now,
                updatedAt = now,
                pdfUri = pdf.documentUri,
                pdfFileName = pdf.displayName
            )
        }
    }

    /** Entries that came from this folder (their address is inside it) whose file has gone. */
    fun missingCount(pdfs: List<FolderPdf>, existing: List<LibraryItem>, folderUri: String): Int {
        val present = pdfs.map { it.documentUri }.toSet()
        return existing.count { item -> item.pdfUri?.startsWith(folderUri) == true && item.pdfUri !in present }
    }

    /** "Top-down_Raglan.pdf" -> "Top-down Raglan"; hyphens inside words stay. */
    internal fun titleFor(fileName: String): String =
        fileName.substringBeforeLast('.', fileName)
            .replace('_', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
            .ifEmpty { fileName }
}

class SyncPatternFolder(
    private val folder: PatternFolder,
    private val libraryRepository: LibraryRepository,
    private val newId: () -> String,
    private val clock: () -> Long = System::currentTimeMillis
) {
    /** Returns null when no folder is chosen. */
    suspend operator fun invoke(): PatternFolderSyncResult? {
        val folderUri = folder.folderUri() ?: return null
        val pdfs = folder.listPdfs()
        val existing = libraryRepository.observeLibraryItems().first()
        val added = PatternFolderPlanner.newItems(pdfs, existing, clock(), newId)
        added.forEach { libraryRepository.saveLibraryItem(it) }
        return PatternFolderSyncResult(added = added.size, missing = PatternFolderPlanner.missingCount(pdfs, existing, folderUri))
    }
}
