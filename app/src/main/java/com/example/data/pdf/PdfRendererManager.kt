package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class PdfRendererManager(private val context: Context) {

    // Cache up to 50 pages in memory for instant, buttery-smooth page flipping
    private val bitmapCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(50) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    private val sessionMutex = Mutex()
    private var activeUri: Uri? = null
    private var activePfd: ParcelFileDescriptor? = null
    private var activeRenderer: PdfRenderer? = null

    private suspend fun getOrCreateRenderer(uri: Uri): PdfRenderer? = sessionMutex.withLock {
        if (uri == activeUri && activeRenderer != null) {
            return activeRenderer
        }

        // Close previous session
        closeSessionInternal()

        try {
            val pfd = openParcelFileDescriptor(uri) ?: return null
            val renderer = PdfRenderer(pfd)
            activeUri = uri
            activePfd = pfd
            activeRenderer = renderer
            renderer
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun closeSessionInternal() {
        try {
            activeRenderer?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            activePfd?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        activeRenderer = null
        activePfd = null
        activeUri = null
    }

    suspend fun getPageCount(uri: Uri): Int = withContext(Dispatchers.IO) {
        val renderer = getOrCreateRenderer(uri) ?: return@withContext 0
        renderer.pageCount
    }

    suspend fun renderPage(
        uri: Uri,
        pageIndex: Int,
        targetWidth: Int = 1080
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${uri}_page_${pageIndex}_w$targetWidth"
        bitmapCache.get(cacheKey)?.let { return@withContext it }

        val renderer = getOrCreateRenderer(uri) ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

        sessionMutex.withLock {
            var page: PdfRenderer.Page? = null
            try {
                page = renderer.openPage(pageIndex)
                val originalWidth = page.width
                val originalHeight = page.height

                val scale = targetWidth.toFloat() / originalWidth.toFloat()
                val finalWidth = targetWidth
                val finalHeight = (originalHeight * scale).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmapCache.put(cacheKey, bitmap)
                bitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                try {
                    page?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun prefetchAdjacentPages(
        uri: Uri,
        currentPage: Int,
        totalPages: Int,
        targetWidth: Int = 1080
    ) = withContext(Dispatchers.IO) {
        // Pre-render previous and next pages so user swiping is instantaneous
        val pagesToPrefetch = listOf(currentPage - 1, currentPage + 1, currentPage + 2)
        for (pageIdx in pagesToPrefetch) {
            if (pageIdx in 0 until totalPages) {
                val cacheKey = "${uri}_page_${pageIdx}_w$targetWidth"
                if (bitmapCache.get(cacheKey) == null) {
                    renderPage(uri, pageIdx, targetWidth)
                }
            }
        }
    }

    fun closeActiveSession() {
        closeSessionInternal()
    }

    fun clearCache() {
        bitmapCache.evictAll()
    }

    private fun openParcelFileDescriptor(uri: Uri): ParcelFileDescriptor? {
        return try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: return null)
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
