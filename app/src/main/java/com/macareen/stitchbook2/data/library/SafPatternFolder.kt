package com.macareen.stitchbook2.data.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.core.net.toUri
import com.macareen.stitchbook2.domain.library.FolderPdf
import com.macareen.stitchbook2.domain.library.PatternFileExistsException
import com.macareen.stitchbook2.domain.library.PatternFolder
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A pattern folder chosen with the system folder picker (Storage Access
 * Framework). Works with any provider that offers folder access: phone
 * storage, SD cards, and cloud apps that support it. Reads, and saves new
 * files the person asked for (Ravelry PDFs) when the provider granted
 * read+write; existing files are never changed.
 */
class SafPatternFolder(context: Context) : PatternFolder {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun folderUri(): String? = preferences.getString(KEY_TREE_URI, null)

    override suspend fun folderName(): String? = withContext(Dispatchers.IO) {
        val tree = folderUri()?.toUri() ?: return@withContext null
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        runCatching {
            appContext.contentResolver.query(root, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }

    override suspend fun choose(uri: String): Boolean = withContext(Dispatchers.IO) {
        val tree = uri.toUri()
        val granted = runCatching {
            appContext.contentResolver.takePersistableUriPermission(tree, GRANT_FLAGS)
        }.recoverCatching {
            // Some providers only grant reading; that's enough to list patterns.
            appContext.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        if (granted) {
            folderUri()?.takeIf { it != uri }?.let(::release)
            preferences.edit { putString(KEY_TREE_URI, uri) }
        }
        granted
    }

    override suspend fun forget() = withContext(Dispatchers.IO) {
        folderUri()?.let(::release)
        preferences.edit { remove(KEY_TREE_URI) }
    }

    override suspend fun listPdfs(): List<FolderPdf> = withContext(Dispatchers.IO) {
        val tree = folderUri()?.toUri() ?: return@withContext emptyList()
        val found = mutableListOf<FolderPdf>()
        try {
            walk(tree, DocumentsContract.getTreeDocumentId(tree), depth = 0, found)
        } catch (e: SecurityException) {
            throw IOException("Access to the pattern folder was removed.", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("The pattern folder can no longer be opened.", e)
        }
        found
    }

    override fun canSave(): Boolean {
        val tree = folderUri()?.toUri() ?: return false
        return appContext.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
    }

    override suspend fun saveNewPdf(displayName: String, write: suspend (OutputStream) -> Unit): FolderPdf {
        val (tree, created) = withContext(Dispatchers.IO) {
            val tree = folderUri()?.toUri() ?: throw IOException("No pattern folder is chosen.")
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            if (topLevelNames(tree, rootId).any { it.equals(displayName, ignoreCase = true) }) {
                throw PatternFileExistsException(displayName)
            }
            val root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
            val created = try {
                DocumentsContract.createDocument(appContext.contentResolver, root, PDF_MIME, displayName)
            } catch (e: SecurityException) {
                throw IOException("The pattern folder does not allow new files.", e)
            } ?: throw IOException("The pattern folder's app did not create the file.")
            tree to created
        }
        try {
            withContext(Dispatchers.IO) {
                appContext.contentResolver.openOutputStream(created, "w")?.use { write(it) }
                    ?: throw IOException("The new file could not be opened for writing.")
            }
        } catch (e: Throwable) {
            // Only the file made just now is removed; nothing that was already there is touched.
            withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(appContext.contentResolver, created) } }
            throw e
        }
        val savedName = withContext(Dispatchers.IO) { displayNameOf(created) } ?: displayName
        return FolderPdf(
            documentUri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getDocumentId(created)).toString(),
            displayName = savedName
        )
    }

    private fun topLevelNames(tree: Uri, rootId: String): List<String> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val cursor = appContext.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?: throw IOException("The pattern folder's app did not answer.")
        return cursor.use { buildList { while (it.moveToNext()) it.getString(0)?.let(::add) } }
    }

    private fun displayNameOf(document: Uri): String? = runCatching {
        appContext.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    private fun walk(tree: Uri, documentId: String, depth: Int, found: MutableList<FolderPdf>) {
        if (depth > MAX_DEPTH || found.size >= MAX_FILES) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val cursor = appContext.contentResolver.query(children, columns, null, null, null)
            ?: throw IOException("The pattern folder's app did not answer.")
        val folders = mutableListOf<String>()
        cursor.use {
            while (it.moveToNext() && found.size < MAX_FILES) {
                val id = it.getString(0) ?: continue
                val name = it.getString(1) ?: continue
                val mime = it.getString(2)
                when {
                    mime == DocumentsContract.Document.MIME_TYPE_DIR -> folders += id
                    mime == PDF_MIME || name.endsWith(".pdf", ignoreCase = true) -> found += FolderPdf(
                        documentUri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(),
                        displayName = name
                    )
                }
            }
        }
        folders.forEach { walk(tree, it, depth + 1, found) }
    }

    private fun release(uri: String) {
        runCatching { appContext.contentResolver.releasePersistableUriPermission(uri.toUri(), GRANT_FLAGS) }
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private companion object {
        const val PREFERENCES = "pattern_folder"
        const val KEY_TREE_URI = "tree_uri"
        const val PDF_MIME = "application/pdf"
        const val MAX_DEPTH = 5
        const val MAX_FILES = 5_000
        const val GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
