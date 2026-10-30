package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.pdf.PdfRendererManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DocumentRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val pdfRendererManager: PdfRendererManager,
    private val documentContentParser: com.example.data.reader.DocumentContentParser
) {
    private val documentDao = database.documentDao()
    private val bookmarkDao = database.bookmarkDao()

    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    val recentDocuments: Flow<List<DocumentEntity>> = documentDao.getRecentDocuments()
    val favoriteDocuments: Flow<List<DocumentEntity>> = documentDao.getFavoriteDocuments()
    val scannedDocuments: Flow<List<DocumentEntity>> = documentDao.getScannedDocuments()

    fun searchDocuments(query: String): Flow<List<DocumentEntity>> =
        documentDao.searchDocuments(query)

    suspend fun getDocumentById(id: Long): DocumentEntity? =
        documentDao.getDocumentById(id)

    suspend fun importDocumentFromUri(uri: Uri): Long = withContext(Dispatchers.IO) {
        val existing = documentDao.getDocumentByUri(uri.toString())
        if (existing != null) {
            documentDao.updateLastOpened(existing.id)
            return@withContext existing.id
        }

        var fileName = "Document_${System.currentTimeMillis()}"
        var fileSize = 0L

        // Query metadata from ContentResolver
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val detectedMime = context.contentResolver.getType(uri) ?: when {
            fileName.endsWith(".pdf", true) -> "application/pdf"
            fileName.endsWith(".docx", true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            fileName.endsWith(".doc", true) -> "application/msword"
            fileName.endsWith(".pptx", true) -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            fileName.endsWith(".ppt", true) -> "application/vnd.ms-powerpoint"
            fileName.endsWith(".txt", true) -> "text/plain"
            fileName.endsWith(".md", true) -> "text/markdown"
            fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
            fileName.endsWith(".png", true) -> "image/png"
            fileName.endsWith(".webp", true) -> "image/webp"
            else -> "application/octet-stream"
        }

        val docType = documentContentParser.determineDocType(fileName, detectedMime)
        val calculatedPages: Int? = when (docType) {
            com.example.data.reader.DocType.PDF -> {
                val count = pdfRendererManager.getPageCount(uri)
                if (count > 0) count else 1
            }
            com.example.data.reader.DocType.PPT -> {
                val slides = documentContentParser.parsePptx(uri)
                if (slides.isNotEmpty()) slides.size else 1
            }
            com.example.data.reader.DocType.WORD -> {
                val paras = documentContentParser.parseDocx(uri)
                val estimated = (paras.size / 4).coerceAtLeast(1)
                estimated
            }
            com.example.data.reader.DocType.TXT, com.example.data.reader.DocType.MARKDOWN, com.example.data.reader.DocType.IMAGE -> 1
            else -> null
        }

        val entity = DocumentEntity(
            uri = uri.toString(),
            displayName = fileName,
            mimeType = detectedMime,
            size = fileSize,
            pageCount = calculatedPages,
            lastOpenedAt = System.currentTimeMillis()
        )
        documentDao.insertDocument(entity)
    }

    suspend fun registerLocalPdf(file: File, displayName: String, isScanned: Boolean = false): Long = withContext(Dispatchers.IO) {
        val uri = Uri.fromFile(file)
        val pageCount = pdfRendererManager.getPageCount(uri)

        val entity = DocumentEntity(
            uri = uri.toString(),
            displayName = displayName,
            mimeType = "application/pdf",
            size = file.length(),
            pageCount = if (pageCount > 0) pageCount else 1,
            lastOpenedAt = System.currentTimeMillis(),
            isScanned = isScanned
        )
        documentDao.insertDocument(entity)
    }

    suspend fun copyToInternalAndRegister(
        sourceFile: File,
        desiredName: String,
        isScanned: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        val documentsDir = File(context.filesDir, "documents").apply { mkdirs() }
        val safeName = if (desiredName.endsWith(".pdf", ignoreCase = true)) desiredName else "$desiredName.pdf"
        val targetFile = File(documentsDir, safeName)

        sourceFile.copyTo(targetFile, overwrite = true)
        registerLocalPdf(targetFile, safeName, isScanned)
    }

    suspend fun toggleFavorite(id: Long, current: Boolean) {
        documentDao.updateFavorite(id, !current)
    }

    suspend fun recordOpened(id: Long, page: Int = 0) {
        documentDao.updateLastOpened(id, System.currentTimeMillis(), page)
    }

    suspend fun renameDocument(id: Long, newName: String) {
        documentDao.renameDocument(id, newName)
    }

    suspend fun deleteDocument(id: Long) {
        documentDao.deleteById(id)
    }

    // Bookmarks
    fun getBookmarks(docId: Long): Flow<List<BookmarkEntity>> =
        bookmarkDao.getBookmarksForDocument(docId)

    suspend fun toggleBookmark(docId: Long, pageIndex: Int, title: String = "") {
        val isBookmarked = bookmarkDao.isPageBookmarked(docId, pageIndex)
        if (isBookmarked) {
            bookmarkDao.removeBookmarkAtPage(docId, pageIndex)
        } else {
            bookmarkDao.insertBookmark(
                BookmarkEntity(
                    documentId = docId,
                    pageIndex = pageIndex,
                    title = if (title.isNotBlank()) title else "Page ${pageIndex + 1}"
                )
            )
        }
    }

    suspend fun isPageBookmarked(docId: Long, pageIndex: Int): Boolean =
        bookmarkDao.isPageBookmarked(docId, pageIndex)
}
