package com.example.data.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.util.zip.ZipInputStream

enum class DocType {
    PDF,
    WORD,
    PPT,
    TXT,
    MARKDOWN,
    IMAGE,
    OTHER
}

data class SlideContent(
    val slideNumber: Int,
    val title: String,
    val bullets: List<String>
)

class DocumentContentParser(private val context: Context) {

    fun determineDocType(name: String, mimeType: String? = null): DocType {
        val lowerName = name.lowercase()
        return when {
            lowerName.endsWith(".pdf") || mimeType == "application/pdf" -> DocType.PDF
            lowerName.endsWith(".docx") || lowerName.endsWith(".doc") ||
                    mimeType?.contains("word") == true -> DocType.WORD
            lowerName.endsWith(".pptx") || lowerName.endsWith(".ppt") ||
                    mimeType?.contains("presentation") == true || mimeType?.contains("powerpoint") == true -> DocType.PPT
            lowerName.endsWith(".md") || lowerName.endsWith(".markdown") -> DocType.MARKDOWN
            lowerName.endsWith(".txt") || mimeType == "text/plain" -> DocType.TXT
            lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") ||
                    lowerName.endsWith(".webp") || lowerName.endsWith(".bmp") ||
                    mimeType?.startsWith("image/") == true -> DocType.IMAGE
            else -> DocType.OTHER
        }
    }

    suspend fun readTextOrMarkdown(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext ""
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Gagal membaca berkas: ${e.message}"
        }
    }

    suspend fun parseDocx(uri: Uri): List<String> = withContext(Dispatchers.IO) {
        val paragraphs = mutableListOf<String>()
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext emptyList()
            val zip = ZipInputStream(stream)
            var entry = zip.nextEntry
            var foundDocumentXml = false

            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    foundDocumentXml = true
                    val xmlContent = BufferedReader(InputStreamReader(zip, Charsets.UTF_8)).readText()
                    paragraphs.addAll(extractDocxParagraphs(xmlContent))
                    break
                }
                entry = zip.nextEntry
            }
            zip.close()

            if (!foundDocumentXml) {
                paragraphs.add("Berkas Word (.doc/.docx) terenkripsi atau menggunakan format biner lama.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            paragraphs.add("Gagal memproses berkas Word: ${e.message}")
        }
        paragraphs
    }

    private fun extractDocxParagraphs(xml: String): List<String> {
        val results = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            val currentParagraph = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "p" || parser.name == "w:p") {
                            currentParagraph.clear()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        currentParagraph.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "p" || parser.name == "w:p") {
                            val text = currentParagraph.toString().trim()
                            if (text.isNotEmpty()) {
                                results.add(text)
                            }
                            currentParagraph.clear()
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    suspend fun parsePptx(uri: Uri): List<SlideContent> = withContext(Dispatchers.IO) {
        val slides = mutableListOf<SlideContent>()
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext emptyList()
            val zip = ZipInputStream(stream)
            var entry = zip.nextEntry

            val slideXmlMap = mutableMapOf<Int, String>()

            while (entry != null) {
                val name = entry.name
                if (name.startsWith("ppt/slides/slide") && name.endsWith(".xml")) {
                    val numberStr = name.substringAfter("slide").substringBefore(".xml")
                    val slideNum = numberStr.toIntOrNull() ?: 0
                    val xmlContent = BufferedReader(InputStreamReader(zip, Charsets.UTF_8)).readText()
                    slideXmlMap[slideNum] = xmlContent
                }
                entry = zip.nextEntry
            }
            zip.close()

            val sortedKeys = slideXmlMap.keys.sorted()
            for (key in sortedKeys) {
                val xml = slideXmlMap[key] ?: continue
                val textItems = extractPptxText(xml)
                val title = textItems.firstOrNull() ?: "Slide $key"
                val bullets = if (textItems.size > 1) textItems.drop(1) else emptyList()
                slides.add(SlideContent(slideNumber = key, title = title, bullets = bullets))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        slides
    }

    private fun extractPptxText(xml: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            val currentText = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "p" || parser.name == "a:p") {
                            currentText.clear()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        currentText.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "p" || parser.name == "a:p") {
                            val text = currentText.toString().trim()
                            if (text.isNotEmpty()) {
                                list.add(text)
                            }
                            currentText.clear()
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    suspend fun convertTextToPdf(title: String, paragraphs: List<String>, outputFile: File): Boolean =
        withContext(Dispatchers.IO) {
            val doc = PdfDocument()
            try {
                val pageW = 595
                val pageH = 842
                val marginX = 48f
                val marginY = 54f
                val contentW = pageW - (marginX * 2)

                val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(11, 31, 51)
                    textSize = 22f
                    isFakeBoldText = true
                }
                val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(40, 40, 40)
                    textSize = 13f
                }

                var pageNumber = 1
                var pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create()
                var currentPage = doc.startPage(pageInfo)
                var canvas = currentPage.canvas
                canvas.drawColor(Color.WHITE)

                var currentY = marginY + 20f

                // Draw Title
                canvas.drawText(title, marginX, currentY, titlePaint)
                currentY += 36f

                val lineHeight = 20f

                for (p in paragraphs) {
                    val words = p.split(" ")
                    var line = ""

                    for (word in words) {
                        val testLine = if (line.isEmpty()) word else "$line $word"
                        val textWidth = bodyPaint.measureText(testLine)

                        if (textWidth > contentW) {
                            if (currentY + lineHeight > pageH - marginY) {
                                doc.finishPage(currentPage)
                                pageNumber++
                                pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create()
                                currentPage = doc.startPage(pageInfo)
                                canvas = currentPage.canvas
                                canvas.drawColor(Color.WHITE)
                                currentY = marginY
                            }
                            canvas.drawText(line, marginX, currentY, bodyPaint)
                            currentY += lineHeight
                            line = word
                        } else {
                            line = testLine
                        }
                    }

                    if (line.isNotEmpty()) {
                        if (currentY + lineHeight > pageH - marginY) {
                            doc.finishPage(currentPage)
                            pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create()
                            currentPage = doc.startPage(pageInfo)
                            canvas = currentPage.canvas
                            canvas.drawColor(Color.WHITE)
                            currentY = marginY
                        }
                        canvas.drawText(line, marginX, currentY, bodyPaint)
                        currentY += lineHeight + 8f
                    }
                }

                doc.finishPage(currentPage)

                FileOutputStream(outputFile).use { out ->
                    doc.writeTo(out)
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            } finally {
                doc.close()
            }
        }

    suspend fun convertSlidesToPdf(title: String, slides: List<SlideContent>, outputFile: File): Boolean =
        withContext(Dispatchers.IO) {
            val doc = PdfDocument()
            try {
                // Landscape A4 for presentation slides
                val pageW = 842
                val pageH = 595
                val marginX = 60f
                val marginY = 50f
                val contentW = pageW - (marginX * 2)

                val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(11, 31, 51) // Navy
                    textSize = 24f
                    isFakeBoldText = true
                }
                val slideBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(45, 212, 191) // Bright Teal
                    textSize = 14f
                    isFakeBoldText = true
                }
                val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(50, 50, 50)
                    textSize = 16f
                }
                val bulletDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(45, 212, 191)
                    style = Paint.Style.FILL
                }

                for ((idx, slide) in slides.withIndex()) {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
                    val page = doc.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)

                    // Top Decorative Bar
                    val topBarPaint = Paint().apply { color = Color.rgb(11, 31, 51) }
                    canvas.drawRect(0f, 0f, pageW.toFloat(), 12f, topBarPaint)

                    // Slide number badge
                    canvas.drawText("SLIDE ${slide.slideNumber} / ${slides.size}", marginX, marginY + 20f, slideBadgePaint)

                    // Slide Title
                    val slideTitle = if (slide.title.isNotBlank()) slide.title else "Slide ${slide.slideNumber}"
                    canvas.drawText(slideTitle, marginX, marginY + 60f, headerPaint)

                    // Divider line
                    val dividerPaint = Paint().apply {
                        color = Color.rgb(220, 225, 230)
                        strokeWidth = 2f
                    }
                    canvas.drawLine(marginX, marginY + 76f, pageW - marginX, marginY + 76f, dividerPaint)

                    // Bullet points
                    var curY = marginY + 120f
                    val lineSpacing = 32f

                    for (bullet in slide.bullets) {
                        if (curY > pageH - marginY) break
                        canvas.drawCircle(marginX + 8f, curY - 5f, 4f, bulletDotPaint)
                        canvas.drawText(bullet, marginX + 24f, curY, bodyPaint)
                        curY += lineSpacing
                    }

                    // Bottom Document Title footer
                    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(150, 160, 170)
                        textSize = 11f
                    }
                    canvas.drawText(title, marginX, pageH - 24f, footerPaint)

                    doc.finishPage(page)
                }

                FileOutputStream(outputFile).use { out ->
                    doc.writeTo(out)
                }
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            } finally {
                doc.close()
            }
        }

    suspend fun decodeImageBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(stream, null, options)
            stream.close()

            // Downsample if huge (> 4096px) to avoid OutOfMemory
            var sampleSize = 1
            while (options.outWidth / sampleSize > 3840 || options.outHeight / sampleSize > 3840) {
                sampleSize *= 2
            }

            val decodeStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
            decodeStream.close()
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        val ALL_SUPPORTED_MIME_TYPES = arrayOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
            "text/plain",
            "text/markdown",
            "text/x-markdown",
            "image/jpeg",
            "image/png",
            "image/webp"
        )
    }
}
