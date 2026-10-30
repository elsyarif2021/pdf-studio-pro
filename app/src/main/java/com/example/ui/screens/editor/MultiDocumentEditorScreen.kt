package com.example.ui.screens.editor

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.DocumentEntity
import com.example.data.pdf.PdfProcessor
import com.example.data.pdf.PdfRendererManager
import com.example.data.repository.DocumentRepository
import com.example.ui.components.TextInputDialog
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class EditablePageItem(
    val id: String,
    val docUri: Uri,
    val docName: String,
    val pageIndex: Int,
    var rotation: Int = 0,
    var watermarkText: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiDocumentEditorScreen(
    initialDocuments: List<DocumentEntity>,
    pdfProcessor: PdfProcessor,
    pdfRendererManager: PdfRendererManager,
    documentRepository: DocumentRepository,
    onBack: () -> Unit,
    onDocumentSaved: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sourceDocs = remember { mutableStateListOf<DocumentEntity>().apply { addAll(initialDocuments) } }
    val editablePages = remember { mutableStateListOf<EditablePageItem>() }
    var selectedPageIndex by remember { mutableStateOf<Int?>(null) }

    var isProcessing by remember { mutableStateOf(false) }
    var processingMsg by remember { mutableStateOf("Memuat halaman...") }

    var showWatermarkDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var isBatchWatermark by remember { mutableStateOf(false) }

    // Launcher to add more PDFs to this multi-doc editor
    val addPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isProcessing = true
                processingMsg = "Membuka ${uris.size} berkas tambahan..."
                for (uri in uris) {
                    val id = documentRepository.importDocumentFromUri(uri)
                    documentRepository.getDocumentById(id)?.let { doc ->
                        if (sourceDocs.none { it.id == doc.id }) {
                            sourceDocs.add(doc)
                            val pCount = pdfRendererManager.getPageCount(Uri.parse(doc.uri))
                            for (p in 0 until pCount) {
                                editablePages.add(
                                    EditablePageItem(
                                        id = "${doc.id}_$p",
                                        docUri = Uri.parse(doc.uri),
                                        docName = doc.displayName,
                                        pageIndex = p
                                    )
                                )
                            }
                        }
                    }
                }
                isProcessing = false
            }
        }
    }

    // Populate pages on first launch
    LaunchedEffect(sourceDocs.toList()) {
        if (editablePages.isEmpty()) {
            isProcessing = true
            processingMsg = "Memuat halaman dokumen..."
            for (doc in sourceDocs) {
                val docUri = Uri.parse(doc.uri)
                val count = pdfRendererManager.getPageCount(docUri)
                for (p in 0 until count) {
                    editablePages.add(
                        EditablePageItem(
                            id = "${doc.id}_$p",
                            docUri = docUri,
                            docName = doc.displayName,
                            pageIndex = p
                        )
                    )
                }
            }
            isProcessing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Editor Multi Dokumen", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${sourceDocs.size} Berkas • ${editablePages.size} Halaman Total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Add more PDFs
                    IconButton(onClick = { addPdfLauncher.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Tambah Berkas")
                    }

                    // Save / Export button
                    Button(
                        onClick = { showExportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = BrightTeal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Simpan", color = PrimaryNavy, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            // Action bar for selected page or bulk tools
            Surface(
                color = PrimaryNavy,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rotate
                    IconButton(
                        onClick = {
                            selectedPageIndex?.let { idx ->
                                val item = editablePages[idx]
                                editablePages[idx] = item.copy(rotation = (item.rotation + 90) % 360)
                            }
                        },
                        enabled = selectedPageIndex != null
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.RotateRight, "Rotate", tint = if (selectedPageIndex != null) BrightTeal else Color.Gray)
                            Text("Putar", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }

                    // Move Left / Up
                    IconButton(
                        onClick = {
                            selectedPageIndex?.let { idx ->
                                if (idx > 0) {
                                    val item = editablePages.removeAt(idx)
                                    editablePages.add(idx - 1, item)
                                    selectedPageIndex = idx - 1
                                }
                            }
                        },
                        enabled = selectedPageIndex != null && selectedPageIndex!! > 0
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ArrowBack, "Move Left", tint = if (selectedPageIndex != null && selectedPageIndex!! > 0) BrightTeal else Color.Gray)
                            Text("Maju", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }

                    // Move Right / Down
                    IconButton(
                        onClick = {
                            selectedPageIndex?.let { idx ->
                                if (idx < editablePages.size - 1) {
                                    val item = editablePages.removeAt(idx)
                                    editablePages.add(idx + 1, item)
                                    selectedPageIndex = idx + 1
                                }
                            }
                        },
                        enabled = selectedPageIndex != null && selectedPageIndex!! < editablePages.size - 1
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ArrowForward, "Move Right", tint = if (selectedPageIndex != null && selectedPageIndex!! < editablePages.size - 1) BrightTeal else Color.Gray)
                            Text("Mundur", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }

                    // Watermark (single or batch)
                    IconButton(onClick = {
                        isBatchWatermark = selectedPageIndex == null
                        showWatermarkDialog = true
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.BrandingWatermark, "Watermark", tint = BrightTeal)
                            Text(if (selectedPageIndex != null) "Watermark" else "Batch WM", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }

                    // Delete Page
                    IconButton(
                        onClick = {
                            selectedPageIndex?.let { idx ->
                                editablePages.removeAt(idx)
                                selectedPageIndex = null
                            }
                        },
                        enabled = selectedPageIndex != null && editablePages.size > 1
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Delete, "Delete", tint = if (selectedPageIndex != null && editablePages.size > 1) StatusError else Color.Gray)
                            Text("Hapus", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (editablePages.isEmpty() && !isProcessing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Pilih minimal satu berkas PDF untuk diedit.", color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(editablePages, key = { index, item -> "${item.id}_$index" }) { index, pageItem ->
                        val isSelected = selectedPageIndex == index
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) BrightTeal else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedPageIndex = if (isSelected) null else index
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Thumbnail
                                ThumbnailView(
                                    uri = pageItem.docUri,
                                    pageIndex = pageItem.pageIndex,
                                    rotation = pageItem.rotation,
                                    pdfRendererManager = pdfRendererManager
                                )

                                // Watermark badge if present
                                pageItem.watermarkText?.let { wm ->
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .background(Color.Black.copy(alpha = 0.45f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(wm, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                // Top Header Bar: Document Origin Badge
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(PrimaryNavy.copy(alpha = 0.85f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = pageItem.docName,
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Bottom Footer Badge: New Page Index
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .clip(RoundedCornerShape(topStart = 8.dp))
                                        .background(BrightTeal)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "Hal ${index + 1}",
                                        color = PrimaryNavy,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
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
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = BrightTeal)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(processingMsg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Watermark Dialog
    if (showWatermarkDialog) {
        var wmText by remember { mutableStateOf("RAHASIA") }
        AlertDialog(
            onDismissRequest = { showWatermarkDialog = false },
            title = { Text(if (isBatchWatermark) "Beri Watermark ke Semua Halaman" else "Watermark Halaman Ini") },
            text = {
                OutlinedTextField(
                    value = wmText,
                    onValueChange = { wmText = it },
                    label = { Text("Teks Watermark") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isBatchWatermark) {
                            for (i in editablePages.indices) {
                                editablePages[i] = editablePages[i].copy(watermarkText = wmText)
                            }
                        } else {
                            selectedPageIndex?.let { idx ->
                                editablePages[idx] = editablePages[idx].copy(watermarkText = wmText)
                            }
                        }
                        showWatermarkDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
                ) {
                    Text("Terapkan", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWatermarkDialog = false }) { Text("Batal") }
            }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        TextInputDialog(
            title = "Simpan Dokumen Hasil Edit",
            initialValue = "MultiDoc_Edited_${System.currentTimeMillis()}",
            label = "Nama Berkas Baru",
            onConfirm = { fileName ->
                showExportDialog = false
                isProcessing = true
                processingMsg = "Merakit dokumen multi-berkas..."
                scope.launch {
                    val outDir = File(context.filesDir, "documents").apply { mkdirs() }
                    val safeName = if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"
                    val targetFile = File(outDir, safeName)

                    val pagesToAssemble = editablePages.map {
                        PdfProcessor.MultiDocPage(
                            docUri = it.docUri,
                            docName = it.docName,
                            pageIndex = it.pageIndex,
                            rotation = it.rotation,
                            watermarkText = it.watermarkText
                        )
                    }

                    val success = pdfProcessor.assembleMultiDocument(pagesToAssemble, targetFile)
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        if (success) {
                            val newDocId = documentRepository.registerLocalPdf(targetFile, safeName)
                            Toast.makeText(context, "Dokumen gabungan berhasil disimpan!", Toast.LENGTH_SHORT).show()
                            onDocumentSaved(newDocId)
                        } else {
                            Toast.makeText(context, "Gagal merakit dokumen multi-berkas", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
fun ThumbnailView(
    uri: Uri,
    pageIndex: Int,
    rotation: Int,
    pdfRendererManager: PdfRendererManager
) {
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, pageIndex) {
        bmp = pdfRendererManager.renderPage(uri, pageIndex, targetWidth = 400)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .graphicsLayer {
                        rotationZ = rotation.toFloat()
                    }
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = BrightTeal)
        }
    }
}
