package com.example.ui.screens.tools

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.pdf.PdfProcessor
import com.example.data.pdf.PdfRendererManager
import com.example.data.repository.DocumentRepository
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ToolItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color,
    val bgColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    pdfProcessor: PdfProcessor,
    pdfRendererManager: PdfRendererManager,
    documentRepository: DocumentRepository,
    onDocumentCreated: (Long) -> Unit,
    onOpenMultiDocEditor: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("Memproses Dokumen...") }

    // Active tool state
    var activeTool by remember { mutableStateOf<String?>(null) }
    var batchSelectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // Tools list
    val tools = listOf(
        ToolItem(
            id = "multi_editor",
            title = "Editor Multi Dokumen",
            description = "Susun, putar & kelola halaman lintas banyak berkas PDF",
            icon = Icons.Default.DashboardCustomize,
            iconColor = Color(0xFF0284C7),
            bgColor = Color(0xFFE0F2FE)
        ),
        ToolItem(
            id = "batch_watermark",
            title = "Watermark Massal",
            description = "Beri tanda air ke banyak berkas PDF sekaligus",
            icon = Icons.Default.BrandingWatermark,
            iconColor = Color(0xFF4F46E5),
            bgColor = Color(0xFFEEF2FF)
        ),
        ToolItem(
            id = "batch_compress",
            title = "Kompres Massal",
            description = "Perkecil ukuran banyak berkas PDF dalam 1 klik",
            icon = Icons.Default.FolderZip,
            iconColor = StatusSuccess,
            bgColor = Color(0xFFDCFCE7)
        ),
        ToolItem(
            id = "merge",
            title = stringResource(R.string.tool_merge),
            description = "Gabungkan beberapa berkas PDF menjadi satu",
            icon = Icons.Default.CallMerge,
            iconColor = Color(0xFF9333EA),
            bgColor = Color(0xFFF3E8FF)
        ),
        ToolItem(
            id = "split",
            title = stringResource(R.string.tool_split),
            description = "Pisahkan halaman atau rentang tertentu dari PDF",
            icon = Icons.Default.CallSplit,
            iconColor = Color(0xFFD97706),
            bgColor = Color(0xFFFEF3C7)
        ),
        ToolItem(
            id = "compress",
            title = stringResource(R.string.tool_compress),
            description = "Perkecil ukuran berkas PDF untuk kemudahan berbagi",
            icon = Icons.Default.Compress,
            iconColor = StatusSuccess,
            bgColor = Color(0xFFDCFCE7)
        ),
        ToolItem(
            id = "image_to_pdf",
            title = stringResource(R.string.tool_img_to_pdf),
            description = "Konversi foto atau koleksi gambar menjadi PDF",
            icon = Icons.Default.Image,
            iconColor = Color(0xFFEA580C),
            bgColor = Color(0xFFFFEDD5)
        ),
        ToolItem(
            id = "pdf_to_img",
            title = stringResource(R.string.tool_pdf_to_img),
            description = "Ekstrak setiap halaman PDF menjadi gambar PNG",
            icon = Icons.Default.PhotoLibrary,
            iconColor = RoyalBlue,
            bgColor = Color(0xFFEFF6FF)
        ),
        ToolItem(
            id = "rotate",
            title = stringResource(R.string.tool_rotate),
            description = "Putar orientasi halaman PDF 90° atau 180°",
            icon = Icons.Default.RotateRight,
            iconColor = PremiumTeal,
            bgColor = TealSoft
        ),
        ToolItem(
            id = "watermark",
            title = stringResource(R.string.tool_watermark),
            description = "Beri tanda air teks pengaman (RAHASIA, DRAFT)",
            icon = Icons.Default.BrandingWatermark,
            iconColor = Color(0xFF4F46E5),
            bgColor = Color(0xFFEEF2FF)
        ),
        ToolItem(
            id = "delete_pages",
            title = stringResource(R.string.tool_delete_pages),
            description = "Hapus halaman yang tidak diinginkan dari PDF",
            icon = Icons.Default.DeleteSweep,
            iconColor = StatusError,
            bgColor = Color(0xFFFEE2E2)
        )
    )

    val multiEditorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onOpenMultiDocEditor(uris)
        }
    }

    val batchWatermarkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            batchSelectedUris = uris
            activeTool = "batch_watermark_dialog"
        }
    }

    val batchCompressLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isProcessing = true
            processingMessage = "Mengompresi ${uris.size} berkas PDF sekaligus..."
            scope.launch {
                val outDir = File(context.filesDir, "documents").apply { mkdirs() }
                val results = pdfProcessor.batchCompress(uris, qualityPercentage = 60, outDir)
                for (file in results) {
                    documentRepository.registerLocalPdf(file, file.name)
                }
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    Toast.makeText(context, "Selesai! ${results.size} berkas berhasil dikompresi.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Launchers for different tools
    // 1. Merge Launcher (Multiple PDFs)
    val mergePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.size >= 2) {
            isProcessing = true
            processingMessage = "Menggabungkan ${uris.size} file PDF..."
            scope.launch {
                val outDir = File(context.filesDir, "documents").apply { mkdirs() }
                val targetFile = File(outDir, "Merged_${System.currentTimeMillis()}.pdf")
                val success = pdfProcessor.mergePdfs(uris, targetFile)
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    if (success) {
                        val docId = documentRepository.registerLocalPdf(targetFile, targetFile.name)
                        Toast.makeText(context, "Berhasil menggabungkan PDF!", Toast.LENGTH_SHORT).show()
                        onDocumentCreated(docId)
                    } else {
                        Toast.makeText(context, "Gagal menggabungkan PDF", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else if (uris.isNotEmpty()) {
            Toast.makeText(context, "Pilih minimal 2 file PDF untuk digabungkan", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. Image to PDF Launcher
    val imagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isProcessing = true
            processingMessage = "Mengonversi ${uris.size} gambar ke PDF..."
            scope.launch {
                val outDir = File(context.filesDir, "documents").apply { mkdirs() }
                val targetFile = File(outDir, "Images_${System.currentTimeMillis()}.pdf")
                val success = pdfProcessor.imagesToPdf(uris, targetFile)
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    if (success) {
                        val docId = documentRepository.registerLocalPdf(targetFile, targetFile.name)
                        Toast.makeText(context, "PDF dari gambar berhasil dibuat!", Toast.LENGTH_SHORT).show()
                        onDocumentCreated(docId)
                    } else {
                        Toast.makeText(context, "Gagal membuat PDF", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // PDF Pickers for single document operations
    var pendingToolAction by remember { mutableStateOf<String?>(null) }
    var singlePdfUri by remember { mutableStateOf<Uri?>(null) }

    val singlePdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            singlePdfUri = uri
            when (pendingToolAction) {
                "compress" -> {
                    isProcessing = true
                    processingMessage = "Mengompresi PDF..."
                    scope.launch {
                        val outDir = File(context.filesDir, "documents").apply { mkdirs() }
                        val targetFile = File(outDir, "Compressed_${System.currentTimeMillis()}.pdf")
                        val success = pdfProcessor.compressPdf(uri, qualityPercentage = 60, targetFile)
                        withContext(Dispatchers.Main) {
                            isProcessing = false
                            if (success) {
                                val docId = documentRepository.registerLocalPdf(targetFile, targetFile.name)
                                Toast.makeText(context, "Ukuran PDF berhasil diperkecil!", Toast.LENGTH_SHORT).show()
                                onDocumentCreated(docId)
                            } else {
                                Toast.makeText(context, "Gagal mengompresi PDF", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                "pdf_to_img" -> {
                    isProcessing = true
                    processingMessage = "Mengekstrak gambar halaman..."
                    scope.launch {
                        val outDir = File(context.filesDir, "extracted_images_${System.currentTimeMillis()}")
                        val files = pdfProcessor.pdfToImages(uri, outDir)
                        withContext(Dispatchers.Main) {
                            isProcessing = false
                            if (files.isNotEmpty()) {
                                Toast.makeText(context, "Berhasil mengekstrak ${files.size} gambar halaman!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Gagal mengekstrak gambar", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                "rotate" -> {
                    isProcessing = true
                    processingMessage = "Memutar orientasi halaman..."
                    scope.launch {
                        val outDir = File(context.filesDir, "documents").apply { mkdirs() }
                        val targetFile = File(outDir, "Rotated_${System.currentTimeMillis()}.pdf")
                        val success = pdfProcessor.rotatePdfPages(uri, rotationDegrees = 90, targetFile)
                        withContext(Dispatchers.Main) {
                            isProcessing = false
                            if (success) {
                                val docId = documentRepository.registerLocalPdf(targetFile, targetFile.name)
                                Toast.makeText(context, "PDF berhasil diputar 90°!", Toast.LENGTH_SHORT).show()
                                onDocumentCreated(docId)
                            } else {
                                Toast.makeText(context, "Gagal memutar PDF", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                "watermark" -> {
                    activeTool = "watermark"
                }
                "split" -> {
                    activeTool = "split"
                }
                "delete_pages" -> {
                    activeTool = "delete_pages"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.nav_tools),
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tools, key = { it.id }) { tool ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                when (tool.id) {
                                    "multi_editor" -> {
                                        multiEditorLauncher.launch(arrayOf("application/pdf"))
                                    }
                                    "batch_watermark" -> {
                                        batchWatermarkLauncher.launch(arrayOf("application/pdf"))
                                    }
                                    "batch_compress" -> {
                                        batchCompressLauncher.launch(arrayOf("application/pdf"))
                                    }
                                    "merge" -> {
                                        mergePdfLauncher.launch(arrayOf("application/pdf"))
                                    }
                                    "image_to_pdf" -> {
                                        imagesLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                    else -> {
                                        pendingToolAction = tool.id
                                        singlePdfPicker.launch(arrayOf("application/pdf"))
                                    }
                                }
                            }
                            .testTag("tool_card_${tool.id}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(tool.bgColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = tool.title,
                                    tint = tool.iconColor,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = tool.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tool.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                minLines = 2
                            )
                        }
                    }
                }
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = BrightTeal)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(processingMessage, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Watermark Dialog
    if (activeTool == "watermark" && singlePdfUri != null) {
        var watermarkText by remember { mutableStateOf("RAHASIA") }
        AlertDialog(
            onDismissRequest = { activeTool = null },
            title = { Text("Tambahkan Watermark") },
            text = {
                Column {
                    Text("Teks tanda air yang akan dicetak di setiap halaman:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        label = { Text("Teks Watermark") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = singlePdfUri!!
                        activeTool = null
                        isProcessing = true
                        processingMessage = "Menerapkan watermark..."
                        scope.launch {
                            val outDir = File(context.filesDir, "documents").apply { mkdirs() }
                            val targetFile = File(outDir, "Watermarked_${System.currentTimeMillis()}.pdf")
                            val success = pdfProcessor.addWatermark(uri, watermarkText, outputFile = targetFile)
                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                if (success) {
                                    val docId = documentRepository.registerLocalPdf(targetFile, targetFile.name)
                                    Toast.makeText(context, "Watermark berhasil ditambahkan!", Toast.LENGTH_SHORT).show()
                                    onDocumentCreated(docId)
                                } else {
                                    Toast.makeText(context, "Gagal menambahkan watermark", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
                ) {
                    Text("Terapkan", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Split Dialog
    if (activeTool == "split" && singlePdfUri != null) {
        var startPageStr by remember { mutableStateOf("1") }
        var endPageStr by remember { mutableStateOf("2") }
        AlertDialog(
            onDismissRequest = { activeTool = null },
            title = { Text("Pisah Dokumen PDF") },
            text = {
                Column {
                    Text("Tentukan rentang halaman yang ingin diekstrak:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startPageStr,
                            onValueChange = { startPageStr = it.filter { c -> c.isDigit() } },
                            label = { Text("Dari Halaman") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endPageStr,
                            onValueChange = { endPageStr = it.filter { c -> c.isDigit() } },
                            label = { Text("Sampai") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = singlePdfUri!!
                        val start = (startPageStr.toIntOrNull() ?: 1) - 1
                        val end = (endPageStr.toIntOrNull() ?: 2) - 1
                        activeTool = null
                        isProcessing = true
                        processingMessage = "Memisahkan halaman..."
                        scope.launch {
                            val outDir = File(context.filesDir, "documents").apply { mkdirs() }
                            val targetFile = File(outDir, "Split_${System.currentTimeMillis()}.pdf")
                            val success = pdfProcessor.splitPdf(uri, start, end, targetFile)
                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                if (success) {
                                    val docId = documentRepository.registerLocalPdf(targetFile, targetFile.name)
                                    Toast.makeText(context, "PDF berhasil dipisah!", Toast.LENGTH_SHORT).show()
                                    onDocumentCreated(docId)
                                } else {
                                    Toast.makeText(context, "Gagal memisahkan PDF", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
                ) {
                    Text("Pisahkan", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Delete Pages Dialog
    if (activeTool == "delete_pages" && singlePdfUri != null) {
        var pageToDeleteStr by remember { mutableStateOf("1") }
        AlertDialog(
            onDismissRequest = { activeTool = null },
            title = { Text("Hapus Halaman dari PDF") },
            text = {
                Column {
                    Text("Masukkan nomor halaman yang ingin dihapus (pisahkan dengan koma jika lebih dari satu):")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pageToDeleteStr,
                        onValueChange = { pageToDeleteStr = it },
                        label = { Text("Nomor Halaman (cth: 1, 3)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = singlePdfUri!!
                        val indices = pageToDeleteStr.split(",")
                            .mapNotNull { it.trim().toIntOrNull() }
                            .map { it - 1 }
                            .toSet()
                        activeTool = null
                        isProcessing = true
                        processingMessage = "Menghapus halaman..."
                        scope.launch {
                            val outDir = File(context.filesDir, "documents").apply { mkdirs() }
                            val targetFile = File(outDir, "Cleaned_${System.currentTimeMillis()}.pdf")
                            val success = pdfProcessor.deletePdfPages(uri, indices, targetFile)
                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                if (success) {
                                    val docId = documentRepository.registerLocalPdf(targetFile, targetFile.name)
                                    Toast.makeText(context, "Halaman berhasil dihapus!", Toast.LENGTH_SHORT).show()
                                    onDocumentCreated(docId)
                                } else {
                                    Toast.makeText(context, "Gagal menghapus halaman", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Hapus Halaman", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Batch Watermark Dialog
    if (activeTool == "batch_watermark_dialog" && batchSelectedUris.isNotEmpty()) {
        var wmText by remember { mutableStateOf("RAHASIA") }
        AlertDialog(
            onDismissRequest = { activeTool = null },
            title = { Text("Watermark Massal (${batchSelectedUris.size} Berkas)") },
            text = {
                Column {
                    Text("Teks tanda air yang akan diterapkan ke seluruh halaman pada ${batchSelectedUris.size} berkas PDF:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = wmText,
                        onValueChange = { wmText = it },
                        label = { Text("Teks Watermark") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uris = batchSelectedUris
                        activeTool = null
                        isProcessing = true
                        processingMessage = "Menerapkan watermark ke ${uris.size} berkas..."
                        scope.launch {
                            val outDir = File(context.filesDir, "documents").apply { mkdirs() }
                            val results = pdfProcessor.batchApplyWatermark(uris, wmText, outDir)
                            for (f in results) {
                                documentRepository.registerLocalPdf(f, f.name)
                            }
                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                Toast.makeText(context, "Selesai! Watermark diterapkan pada ${results.size} berkas.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
                ) {
                    Text("Terapkan Semua", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { activeTool = null }) { Text("Batal") }
            }
        )
    }
}
