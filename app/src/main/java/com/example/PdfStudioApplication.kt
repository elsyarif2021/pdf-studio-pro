package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.data.local.AppDatabase
import com.example.data.ocr.OcrManager
import com.example.data.pdf.PdfProcessor
import com.example.data.pdf.PdfRendererManager
import com.example.data.repository.DocumentRepository
import com.example.data.scanner.ScannerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class PdfStudioApplication : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var pdfRendererManager: PdfRendererManager
        private set
    lateinit var pdfProcessor: PdfProcessor
        private set
    lateinit var scannerManager: ScannerManager
        private set
    lateinit var ocrManager: OcrManager
        private set
    lateinit var documentRepository: DocumentRepository
        private set
    lateinit var documentContentParser: com.example.data.reader.DocumentContentParser
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        pdfRendererManager = PdfRendererManager(this)
        pdfProcessor = PdfProcessor(this)
        scannerManager = ScannerManager(this)
        ocrManager = OcrManager(this)
        documentContentParser = com.example.data.reader.DocumentContentParser(this)
        documentRepository = DocumentRepository(this, database, pdfRendererManager, documentContentParser)

        // Prepopulate a sample welcome PDF guide on first launch
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val docs = documentRepository.allDocuments.first()
                if (docs.isEmpty()) {
                    createWelcomeGuidePdf()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun createWelcomeGuidePdf() {
        val sampleDir = File(filesDir, "documents").apply { mkdirs() }
        val sampleFile = File(sampleDir, "Panduan_PDF_Studio_Pro.pdf")
        if (!sampleFile.exists()) {
            val doc = PdfDocument()

            // Page 1: Welcome & Overview
            val pageInfo1 = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page1 = doc.startPage(pageInfo1)
            val canvas1 = page1.canvas
            canvas1.drawColor(Color.rgb(11, 31, 51)) // Navy Background

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(45, 212, 191) // Bright Teal
                textSize = 34f
                isFakeBoldText = true
            }
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(204, 251, 241) // Teal Soft
                textSize = 18f
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 15f
            }

            canvas1.drawText("PDF STUDIO PRO", 48f, 100f, titlePaint)
            canvas1.drawText("Ruang Kerja Dokumen Cerdas & Elegan", 48f, 140f, subtitlePaint)

            var y = 220f
            val features = listOf(
                "• Penampil Dokumen PDF Berkualitas Tinggi dengan Zoom Halus",
                "• Pemindai Dokumen Cerdas dengan Filter Magic Color & B&W",
                "• Pengenal Teks OCR Offline Berkecepatan Tinggi (ML Kit)",
                "• Alat PDF Lengkap: Gabung, Pisah, Kompres, Watermark & Putar",
                "• Konversi Gambar ke PDF dan Ekstrak PDF ke Gambar PNG",
                "• 100% Offline, Aman, dan Mengutamakan Privasi Anda"
            )
            for (feature in features) {
                canvas1.drawText(feature, 48f, y, bodyPaint)
                y += 45f
            }

            canvas1.drawText("Dibuat untuk Produktivitas Profesional Tanpa Batas.", 48f, 650f, subtitlePaint)
            doc.finishPage(page1)

            // Page 2: Quick Tips
            val pageInfo2 = PdfDocument.PageInfo.Builder(595, 842, 2).create()
            val page2 = doc.startPage(pageInfo2)
            val canvas2 = page2.canvas
            canvas2.drawColor(Color.WHITE)

            val darkTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(11, 31, 51)
                textSize = 28f
                isFakeBoldText = true
            }
            val darkBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(60, 60, 60)
                textSize = 14f
            }

            canvas2.drawText("Panduan Penggunaan Cepat", 48f, 80f, darkTitle)
            y = 140f
            val tips = listOf(
                "1. Memindai Dokumen: Ketuk ikon Pindai di Beranda atau tombol kamera.",
                "2. Menyesuaikan Sudut: Gunakan filter B&W untuk teks kontras tinggi.",
                "3. Ekstraksi OCR: Buka menu OCR untuk menyalin teks dokumen secara instan.",
                "4. Menggabungkan Dokumen: Akses menu Alat > Gabung PDF untuk menyatukan file.",
                "5. Menambahkan Penanda: Simpan halaman penting saat membaca untuk akses cepat."
            )
            for (tip in tips) {
                canvas2.drawText(tip, 48f, y, darkBody)
                y += 50f
            }

            doc.finishPage(page2)

            FileOutputStream(sampleFile).use { out ->
                doc.writeTo(out)
            }
            doc.close()
        }

        documentRepository.registerLocalPdf(sampleFile, "Panduan PDF Studio Pro.pdf", isScanned = false)
    }
}
