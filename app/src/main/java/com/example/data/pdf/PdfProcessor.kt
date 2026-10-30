package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfProcessor(private val context: Context) {

    enum class PageSize(val width: Int, val height: Int) {
        A4(595, 842),
        LETTER(612, 792),
        FIT_IMAGE(0, 0)
    }

    suspend fun imagesToPdf(
        imageUris: List<Uri>,
        outputFile: File,
        pageSize: PageSize = PageSize.A4
    ): Boolean = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        try {
            for ((index, uri) in imageUris.withIndex()) {
                val inputStream = context.contentResolver.openInputStream(uri) ?: continue
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (bitmap == null) continue

                val targetWidth = if (pageSize == PageSize.FIT_IMAGE) bitmap.width else pageSize.width
                val targetHeight = if (pageSize == PageSize.FIT_IMAGE) bitmap.height else pageSize.height

                val pageInfo = PdfDocument.PageInfo.Builder(targetWidth, targetHeight, index + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                // Fill white background
                canvas.drawColor(Color.WHITE)

                // Scale bitmap to fit target dimensions preserving aspect ratio
                val scale = minOf(
                    targetWidth.toFloat() / bitmap.width.toFloat(),
                    targetHeight.toFloat() / bitmap.height.toFloat()
                )
                val scaledWidth = bitmap.width * scale
                val scaledHeight = bitmap.height * scale
                val left = (targetWidth - scaledWidth) / 2f
                val top = (targetHeight - scaledHeight) / 2f

                val destRect = Rect(left.toInt(), top.toInt(), (left + scaledWidth).toInt(), (top + scaledHeight).toInt())
                canvas.drawBitmap(bitmap, null, destRect, null)

                document.finishPage(page)
                bitmap.recycle()
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            document.close()
        }
    }

    suspend fun mergePdfs(
        sourceUris: List<Uri>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        var overallPageIndex = 1
        try {
            for (uri in sourceUris) {
                val pfd = openParcelDescriptor(uri) ?: continue
                val renderer = PdfRenderer(pfd)
                try {
                    for (i in 0 until renderer.pageCount) {
                        val pdfPage = renderer.openPage(i)
                        val w = pdfPage.width
                        val h = pdfPage.height

                        val pageInfo = PdfDocument.PageInfo.Builder(w, h, overallPageIndex++).create()
                        val outPage = document.startPage(pageInfo)
                        val canvas = outPage.canvas
                        canvas.drawColor(Color.WHITE)

                        // Render bitmap representation
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        pdfPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                        canvas.drawBitmap(bmp, 0f, 0f, null)
                        document.finishPage(outPage)

                        bmp.recycle()
                        pdfPage.close()
                    }
                } finally {
                    renderer.close()
                    pfd.close()
                }
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            document.close()
        }
    }

    suspend fun splitPdf(
        sourceUri: Uri,
        startPageZeroBased: Int,
        endPageZeroBased: Int,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = openParcelDescriptor(sourceUri) ?: return@withContext false
            renderer = PdfRenderer(pfd)

            val start = startPageZeroBased.coerceIn(0, renderer.pageCount - 1)
            val end = endPageZeroBased.coerceIn(start, renderer.pageCount - 1)

            var newPageIndex = 1
            for (i in start..end) {
                val pdfPage = renderer.openPage(i)
                val w = pdfPage.width
                val h = pdfPage.height

                val pageInfo = PdfDocument.PageInfo.Builder(w, h, newPageIndex++).create()
                val outPage = document.startPage(pageInfo)
                val canvas = outPage.canvas
                canvas.drawColor(Color.WHITE)

                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                pdfPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                canvas.drawBitmap(bmp, 0f, 0f, null)
                document.finishPage(outPage)

                bmp.recycle()
                pdfPage.close()
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            renderer?.close()
            pfd?.close()
            document.close()
        }
    }

    suspend fun addWatermark(
        sourceUri: Uri,
        watermarkText: String,
        opacity: Float = 0.35f,
        rotationDegrees: Float = -45f,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = openParcelDescriptor(sourceUri) ?: return@withContext false
            renderer = PdfRenderer(pfd)

            val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                alpha = (opacity * 255).toInt().coerceIn(10, 255)
                textSize = 54f
                style = Paint.Style.FILL
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }

            for (i in 0 until renderer.pageCount) {
                val pdfPage = renderer.openPage(i)
                val w = pdfPage.width
                val h = pdfPage.height

                val pageInfo = PdfDocument.PageInfo.Builder(w, h, i + 1).create()
                val outPage = document.startPage(pageInfo)
                val canvas = outPage.canvas
                canvas.drawColor(Color.WHITE)

                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                pdfPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                canvas.drawBitmap(bmp, 0f, 0f, null)

                // Draw watermark in center
                canvas.save()
                canvas.rotate(rotationDegrees, w / 2f, h / 2f)
                canvas.drawText(watermarkText, w / 2f, h / 2f, watermarkPaint)
                canvas.restore()

                document.finishPage(outPage)

                bmp.recycle()
                pdfPage.close()
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            renderer?.close()
            pfd?.close()
            document.close()
        }
    }

    suspend fun compressPdf(
        sourceUri: Uri,
        qualityPercentage: Int = 65,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = openParcelDescriptor(sourceUri) ?: return@withContext false
            renderer = PdfRenderer(pfd)

            val scaleFactor = when {
                qualityPercentage >= 80 -> 1.0f
                qualityPercentage >= 50 -> 0.75f
                else -> 0.55f
            }

            for (i in 0 until renderer.pageCount) {
                val pdfPage = renderer.openPage(i)
                val origW = pdfPage.width
                val origH = pdfPage.height

                val renderW = (origW * scaleFactor).toInt().coerceAtLeast(100)
                val renderH = (origH * scaleFactor).toInt().coerceAtLeast(100)

                val pageInfo = PdfDocument.PageInfo.Builder(origW, origH, i + 1).create()
                val outPage = document.startPage(pageInfo)
                val canvas = outPage.canvas
                canvas.drawColor(Color.WHITE)

                val bmp = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.RGB_565)
                bmp.eraseColor(Color.WHITE)
                pdfPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val destRect = Rect(0, 0, origW, origH)
                canvas.drawBitmap(bmp, null, destRect, null)

                document.finishPage(outPage)

                bmp.recycle()
                pdfPage.close()
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            renderer?.close()
            pfd?.close()
            document.close()
        }
    }

    suspend fun rotatePdfPages(
        sourceUri: Uri,
        rotationDegrees: Int, // 90, 180, 270
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = openParcelDescriptor(sourceUri) ?: return@withContext false
            renderer = PdfRenderer(pfd)

            for (i in 0 until renderer.pageCount) {
                val pdfPage = renderer.openPage(i)
                val origW = pdfPage.width
                val origH = pdfPage.height

                val isSwapped = rotationDegrees % 180 != 0
                val targetW = if (isSwapped) origH else origW
                val targetH = if (isSwapped) origW else origH

                val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, i + 1).create()
                val outPage = document.startPage(pageInfo)
                val canvas = outPage.canvas
                canvas.drawColor(Color.WHITE)

                val bmp = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                pdfPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                val matrix = Matrix().apply {
                    postRotate(rotationDegrees.toFloat(), origW / 2f, origH / 2f)
                    if (isSwapped) {
                        postTranslate((targetW - origW) / 2f, (targetH - origH) / 2f)
                    }
                }
                canvas.drawBitmap(bmp, matrix, null)

                document.finishPage(outPage)

                bmp.recycle()
                pdfPage.close()
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            renderer?.close()
            pfd?.close()
            document.close()
        }
    }

    suspend fun deletePdfPages(
        sourceUri: Uri,
        pageIndicesToDelete: Set<Int>, // 0-based
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = openParcelDescriptor(sourceUri) ?: return@withContext false
            renderer = PdfRenderer(pfd)

            var newPageIndex = 1
            for (i in 0 until renderer.pageCount) {
                if (i in pageIndicesToDelete) continue

                val pdfPage = renderer.openPage(i)
                val w = pdfPage.width
                val h = pdfPage.height

                val pageInfo = PdfDocument.PageInfo.Builder(w, h, newPageIndex++).create()
                val outPage = document.startPage(pageInfo)
                val canvas = outPage.canvas
                canvas.drawColor(Color.WHITE)

                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                pdfPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                canvas.drawBitmap(bmp, 0f, 0f, null)
                document.finishPage(outPage)

                bmp.recycle()
                pdfPage.close()
            }

            if (newPageIndex == 1) {
                // All pages were deleted, cannot produce empty PDF
                return@withContext false
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            renderer?.close()
            pfd?.close()
            document.close()
        }
    }

    suspend fun pdfToImages(
        sourceUri: Uri,
        outputDir: File
    ): List<File> = withContext(Dispatchers.IO) {
        val createdFiles = mutableListOf<File>()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            outputDir.mkdirs()
            pfd = openParcelDescriptor(sourceUri) ?: return@withContext emptyList()
            renderer = PdfRenderer(pfd)

            for (i in 0 until renderer.pageCount) {
                val pdfPage = renderer.openPage(i)
                val w = pdfPage.width * 2
                val h = pdfPage.height * 2

                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                pdfPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                val imageFile = File(outputDir, "page_${i + 1}.png")
                FileOutputStream(imageFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                createdFiles.add(imageFile)

                bmp.recycle()
                pdfPage.close()
            }
            createdFiles
        } catch (e: Exception) {
            e.printStackTrace()
            createdFiles
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    // MULTI-DOCUMENT EDITING & BATCH PROCESSING

    data class MultiDocPage(
        val docUri: Uri,
        val docName: String,
        val pageIndex: Int,
        var rotation: Int = 0,
        var watermarkText: String? = null
    )

    suspend fun assembleMultiDocument(
        pages: List<MultiDocPage>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        val rendererMap = mutableMapOf<Uri, Pair<ParcelFileDescriptor, PdfRenderer>>()
        try {
            var overallIndex = 1
            for (pageItem in pages) {
                val pair = rendererMap.getOrPut(pageItem.docUri) {
                    val pfd = openParcelDescriptor(pageItem.docUri) ?: return@withContext false
                    val rend = PdfRenderer(pfd)
                    Pair(pfd, rend)
                }
                val renderer = pair.second
                if (pageItem.pageIndex !in 0 until renderer.pageCount) continue

                val pdfPage = renderer.openPage(pageItem.pageIndex)
                val origW = pdfPage.width
                val origH = pdfPage.height
                val isSwapped = pageItem.rotation % 180 != 0
                val targetW = if (isSwapped) origH else origW
                val targetH = if (isSwapped) origW else origH

                val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, overallIndex++).create()
                val outPage = document.startPage(pageInfo)
                val canvas = outPage.canvas
                canvas.drawColor(Color.WHITE)

                val bmp = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                pdfPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                if (pageItem.rotation != 0) {
                    val matrix = Matrix().apply {
                        postRotate(pageItem.rotation.toFloat(), origW / 2f, origH / 2f)
                        if (isSwapped) {
                            postTranslate((targetW - origW) / 2f, (targetH - origH) / 2f)
                        }
                    }
                    canvas.drawBitmap(bmp, matrix, null)
                } else {
                    canvas.drawBitmap(bmp, 0f, 0f, null)
                }

                // Optional watermark per page
                pageItem.watermarkText?.let { wm ->
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.DKGRAY
                        alpha = 75
                        textSize = 48f
                        style = Paint.Style.FILL
                        textAlign = Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                    canvas.save()
                    canvas.rotate(-45f, targetW / 2f, targetH / 2f)
                    canvas.drawText(wm, targetW / 2f, targetH / 2f, paint)
                    canvas.restore()
                }

                document.finishPage(outPage)
                bmp.recycle()
                pdfPage.close()
            }

            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            rendererMap.values.forEach {
                try {
                    it.second.close()
                    it.first.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            document.close()
        }
    }

    suspend fun batchApplyWatermark(
        sourceUris: List<Uri>,
        watermarkText: String,
        outputDir: File
    ): List<File> = withContext(Dispatchers.IO) {
        val results = mutableListOf<File>()
        outputDir.mkdirs()
        for ((idx, uri) in sourceUris.withIndex()) {
            val outFile = File(outputDir, "Watermarked_Batch_${idx + 1}_${System.currentTimeMillis()}.pdf")
            if (addWatermark(uri, watermarkText, outputFile = outFile)) {
                results.add(outFile)
            }
        }
        results
    }

    suspend fun batchCompress(
        sourceUris: List<Uri>,
        qualityPercentage: Int,
        outputDir: File
    ): List<File> = withContext(Dispatchers.IO) {
        val results = mutableListOf<File>()
        outputDir.mkdirs()
        for ((idx, uri) in sourceUris.withIndex()) {
            val outFile = File(outputDir, "Compressed_Batch_${idx + 1}_${System.currentTimeMillis()}.pdf")
            if (compressPdf(uri, qualityPercentage, outputFile = outFile)) {
                results.add(outFile)
            }
        }
        results
    }

    suspend fun batchRotate(
        sourceUris: List<Uri>,
        rotationDegrees: Int,
        outputDir: File
    ): List<File> = withContext(Dispatchers.IO) {
        val results = mutableListOf<File>()
        outputDir.mkdirs()
        for ((idx, uri) in sourceUris.withIndex()) {
            val outFile = File(outputDir, "Rotated_Batch_${idx + 1}_${System.currentTimeMillis()}.pdf")
            if (rotatePdfPages(uri, rotationDegrees, outputFile = outFile)) {
                results.add(outFile)
            }
        }
        results
    }

    private fun openParcelDescriptor(uri: Uri): ParcelFileDescriptor? {
        return try {
            if (uri.scheme == "file") {
                ParcelFileDescriptor.open(File(uri.path ?: return null), ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
