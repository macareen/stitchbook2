package com.macareen.stitchbook2.domain.ravelry

import com.macareen.stitchbook2.domain.library.PatternFileExistsException
import com.macareen.stitchbook2.domain.library.PatternFolder
import com.macareen.stitchbook2.domain.model.LibraryItem
import com.macareen.stitchbook2.domain.repository.LibraryRepository
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Saves a Ravelry library pattern's purchased PDF into the person's pattern
 * folder and links it to the Library entry, so the file lives with their
 * other patterns and opens in the app. A file already in the folder is never
 * replaced: if one with the same name is there, the entry is linked to it.
 */
class DownloadRavelryPdf(
    private val api: RavelryApi,
    private val credentialStore: RavelryCredentialStore,
    private val folder: PatternFolder,
    private val libraryRepository: LibraryRepository,
    private val clock: () -> Long = System::currentTimeMillis
) {
    enum class Problem {
        NOT_FROM_RAVELRY, NO_KEY, NO_FOLDER, FOLDER_READ_ONLY, KEY_REFUSED, OFFLINE,
        NOT_IN_LIBRARY, NO_PDF, NOT_A_PDF, UNREADABLE_ANSWER, SAVE_FAILED
    }

    sealed interface Result {
        /** [otherPdfs] counts further PDFs on the same volume that were not downloaded. */
        data class Saved(val fileName: String, val alreadyInFolder: Boolean, val otherPdfs: Int) : Result
        data class Failed(val problem: Problem) : Result
    }

    /** Whether [item] is something this can fetch a PDF for. */
    fun canDownload(item: LibraryItem): Boolean = item.pdfUri == null && (volumeIdOf(item) != null || item.ravelryPatternId != null)

    suspend operator fun invoke(libraryItemId: String): Result = try {
        download(libraryItemId)
    } catch (error: CancellationException) {
        throw error
    } catch (_: RavelryAuthException) {
        Result.Failed(Problem.KEY_REFUSED)
    } catch (error: RavelryUnavailableException) {
        Result.Failed(if (error.cause is IOException) Problem.OFFLINE else Problem.UNREADABLE_ANSWER)
    } catch (_: NotAPdfException) {
        Result.Failed(Problem.NOT_A_PDF)
    } catch (_: IOException) {
        Result.Failed(Problem.SAVE_FAILED)
    }

    private suspend fun download(libraryItemId: String): Result {
        val item = libraryRepository.observeLibraryItem(libraryItemId).first()
            ?: return Result.Failed(Problem.NOT_FROM_RAVELRY)
        if (!canDownload(item)) return Result.Failed(Problem.NOT_FROM_RAVELRY)
        val credentials = credentialStore.load() ?: return Result.Failed(Problem.NO_KEY)
        if (folder.folderUri() == null) return Result.Failed(Problem.NO_FOLDER)
        if (!folder.canSave()) return Result.Failed(Problem.FOLDER_READ_ONLY)

        val volumeId = volumeIdOf(item) ?: findVolumeId(credentials, item.ravelryPatternId)
            ?: return Result.Failed(Problem.NOT_IN_LIBRARY)
        val pdfs = pdfAttachments(api.volumeAttachments(credentials, volumeId))
        val attachment = pdfs.firstOrNull() ?: return Result.Failed(Problem.NO_PDF)
        val fileName = fileNameFor(attachment.fileName, item.title)

        val existing = folder.listPdfs().firstOrNull { it.displayName.equals(fileName, ignoreCase = true) }
        val saved = existing ?: try {
            val url = api.downloadLink(credentials, attachment.id)
            folder.saveNewPdf(fileName) { out -> PdfSignatureCheck(out).use { api.downloadFile(url, it) } }
        } catch (error: PatternFileExistsException) {
            // The name was taken after the check above: link that file rather than replace it.
            folder.listPdfs().firstOrNull { it.displayName.equals(error.displayName, ignoreCase = true) } ?: throw error
        }

        val latest = libraryRepository.observeLibraryItem(libraryItemId).first() ?: item
        libraryRepository.saveLibraryItem(
            latest.copy(pdfUri = saved.documentUri, pdfFileName = saved.displayName, pdfLastViewedPage = null, updatedAt = clock())
        )
        return Result.Saved(saved.displayName, alreadyInFolder = existing != null, otherPdfs = pdfs.size - 1)
    }

    private suspend fun findVolumeId(credentials: RavelryCredentials, patternId: String?): Long? {
        val id = patternId?.trim()?.toLongOrNull() ?: return null
        return api.library(credentials, api.currentUsername(credentials)).firstOrNull { it.patternId == id }?.id
    }

    companion object {
        /** Entries imported from a Ravelry library volume carry its id in their own id. */
        fun volumeIdOf(item: LibraryItem): Long? =
            item.id.takeIf { it.startsWith(RavelryImportPlanner.VOLUME_ID_PREFIX) }
                ?.removePrefix(RavelryImportPlanner.VOLUME_ID_PREFIX)
                ?.toLongOrNull()

        /** PDFs first; an attachment without a name might still be one, so it is kept last. */
        internal fun pdfAttachments(attachments: List<RavelryAttachment>): List<RavelryAttachment> {
            val named = attachments.filter { it.fileName?.endsWith(".pdf", ignoreCase = true) == true }
            return named + attachments.filter { it.fileName.isNullOrBlank() }
        }

        /** A safe file name for any storage provider, always ending in ".pdf". */
        internal fun fileNameFor(attachmentName: String?, title: String): String {
            val base = (attachmentName?.takeIf { it.isNotBlank() } ?: title)
                .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .trim('.')
                .ifEmpty { "Ravelry pattern" }
            val stem = if (base.endsWith(".pdf", ignoreCase = true)) base.dropLast(4).trim() else base
            return stem.take(MAX_NAME_LENGTH).trim().ifEmpty { "Ravelry pattern" } + ".pdf"
        }

        private const val MAX_NAME_LENGTH = 120
    }
}

/** The download was not a PDF (for example a sign-in page or a zip); nothing was kept. */
class NotAPdfException : IOException("The downloaded file is not a PDF.")

/**
 * Passes bytes through only once the first five read "%PDF-", so a web page
 * or archive served in a PDF's place never lands in the pattern folder.
 */
internal class PdfSignatureCheck(out: OutputStream) : FilterOutputStream(out) {
    private val header = ByteArray(SIGNATURE.size)
    private var headerLength = 0

    override fun write(b: Int) {
        if (headerLength < SIGNATURE.size) {
            header[headerLength++] = b.toByte()
            if (headerLength == SIGNATURE.size) {
                if (!header.contentEquals(SIGNATURE)) throw NotAPdfException()
                out.write(header)
            }
        } else {
            out.write(b)
        }
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var index = off
        while (headerLength < SIGNATURE.size && index < off + len) write(b[index++].toInt())
        if (index < off + len) out.write(b, index, off + len - index)
    }

    override fun close() {
        if (headerLength < SIGNATURE.size) throw NotAPdfException()
        super.close()
    }

    private companion object {
        val SIGNATURE = "%PDF-".toByteArray(Charsets.US_ASCII)
    }
}
