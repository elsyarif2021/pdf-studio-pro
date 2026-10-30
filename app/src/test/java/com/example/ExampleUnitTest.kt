package com.example

import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.DocumentEntity
import com.example.ui.components.formatDate
import com.example.ui.components.formatFileSize
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testFileSizeFormatting() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("512.0 B", formatFileSize(512))
        assertEquals("1.0 KB", formatFileSize(1024))
        assertEquals("2.5 MB", formatFileSize((2.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun testDateFormatting() {
        val timestamp = 1700000000000L
        val formatted = formatDate(timestamp)
        assertTrue(formatted.isNotEmpty())
    }

    @Test
    fun testDocumentEntityDefaults() {
        val doc = DocumentEntity(
            uri = "content://sample/test.pdf",
            displayName = "test.pdf",
            size = 2048,
            pageCount = 5
        )
        assertEquals("test.pdf", doc.displayName)
        assertEquals(5, doc.pageCount)
        assertFalse(doc.isFavorite)
        assertFalse(doc.isScanned)
        assertEquals(0, doc.lastReadPage)
    }

    @Test
    fun testBookmarkEntityCreation() {
        val bookmark = BookmarkEntity(
            documentId = 101L,
            pageIndex = 3,
            title = "Section 2"
        )
        assertEquals(101L, bookmark.documentId)
        assertEquals(3, bookmark.pageIndex)
        assertEquals("Section 2", bookmark.title)
    }

    @Test
    fun testPageRangeClamping() {
        val totalPages = 10
        val requestedStart = -5
        val requestedEnd = 25
        val clampedStart = requestedStart.coerceIn(0, totalPages - 1)
        val clampedEnd = requestedEnd.coerceIn(clampedStart, totalPages - 1)

        assertEquals(0, clampedStart)
        assertEquals(9, clampedEnd)
    }
}
