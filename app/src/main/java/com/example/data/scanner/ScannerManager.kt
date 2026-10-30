package com.example.data.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ScannerManager(private val context: Context) {

    enum class ScanFilter {
        ORIGINAL,
        MAGIC,
        BW,
        GRAYSCALE
    }

    suspend fun saveCapturedImage(bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val scanDir = File(context.filesDir, "scans").apply { mkdirs() }
        val file = File(scanDir, "scan_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        file
    }

    suspend fun applyFilterAndRotation(
        imageUri: Uri,
        filter: ScanFilter,
        rotationDegrees: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri) ?: return@withContext null
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (original == null) return@withContext null

            // Apply rotation
            val rotatedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
                original.recycle()
                rotated
            } else {
                original
            }

            // Apply filter
            val result = when (filter) {
                ScanFilter.ORIGINAL -> rotatedBitmap
                ScanFilter.GRAYSCALE -> applyGrayscale(rotatedBitmap)
                ScanFilter.MAGIC -> applyMagicColor(rotatedBitmap)
                ScanFilter.BW -> applyBlackAndWhite(rotatedBitmap)
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun applyGrayscale(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    private fun applyMagicColor(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()

        // Enhance contrast and color saturation
        val contrast = 1.35f
        val brightness = -10f
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        // Add subtle saturation boost
        val satMatrix = ColorMatrix().apply { setSaturation(1.2f) }
        colorMatrix.postConcat(satMatrix)

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    private fun applyBlackAndWhite(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        var sumLum = 0L
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            sumLum += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
        }
        val threshold = (sumLum / pixels.size).toInt().coerceIn(100, 160)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] = if (lum > threshold) Color.WHITE else Color.BLACK
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    suspend fun exportScannedPagesToPdf(
        pageBitmaps: List<Bitmap>,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        try {
            for ((index, bitmap) in pageBitmaps.withIndex()) {
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                document.finishPage(page)
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
}
