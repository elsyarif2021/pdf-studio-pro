package com.example.ui.screens.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.local.entity.DocumentEntity
import com.example.data.ocr.OcrManager
import com.example.data.pdf.PdfProcessor
import com.example.data.pdf.PdfRendererManager
import com.example.data.reader.DocType
import com.example.data.reader.DocumentContentParser
import com.example.data.reader.SlideContent
import com.example.data.repository.DocumentRepository
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    documents: List<DocumentEntity>,
    initialDocumentIndex: Int = 0,
    pdfRendererManager: PdfRendererManager,
    documentRepository: DocumentRepository,
    documentContentParser: DocumentContentParser,
    pdfProcessor: PdfProcessor,
    ocrManager: OcrManager,
    onBack: () -> Unit,
    onAddDocuments: (List<Uri>) -> Unit,
    onCloseDocument: (DocumentEntity) -> Unit,
    onOpenMultiDocEditor: () -> Unit,
    onDocumentConvertedToPdf: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeIndex by remember(documents) {
        mutableStateOf(initialDocumentIndex.coerceIn(0, (documents.size - 1).coerceAtLeast(0)))
    }

    val activeDocument = documents.getOrNull(activeIndex) ?: documents.firstOrNull()

    if (activeDocument == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val addDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onAddDocuments(uris)
        }
    }

    val uri = remember(activeDocument.uri) { Uri.parse(activeDocument.uri) }
    val docType = remember(activeDocument.id, activeDocument.displayName, activeDocument.mimeType) {
        documentContentParser.determineDocType(activeDocument.displayName, activeDocument.mimeType)
    }

    var isNightMode by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var fontSizeSp by remember { mutableFloatStateOf(16f) }
    var isConvertingToPdf by remember { mutableStateOf(false) }

    // OCR extracted text modal state
    var ocrResultText by remember { mutableStateOf<String?>(null) }
    var isRunningOcr by remember { mutableStateOf(false) }

    // Specific states based on document type
    var pdfPageCount by remember(activeDocument.id) { mutableStateOf(activeDocument.pageCount ?: 1) }
    var isPdfLoading by remember(activeDocument.id) { mutableStateOf(true) }
    var isHorizontalMode by remember { mutableStateOf(true) }

    // Word state
    var wordParagraphs by remember(activeDocument.id) { mutableStateOf<List<String>>(emptyList()) }
    var isWordLoading by remember(activeDocument.id) { mutableStateOf(false) }

    // PPT state
    var pptSlides by remember(activeDocument.id) { mutableStateOf<List<SlideContent>>(emptyList()) }
    var isPptLoading by remember(activeDocument.id) { mutableStateOf(false) }

    // TXT/Markdown state
    var textContent by remember(activeDocument.id) { mutableStateOf("") }
    var isTextLoading by remember(activeDocument.id) { mutableStateOf(false) }

    val bookmarks by documentRepository.getBookmarks(activeDocument.id).collectAsState(initial = emptyList())

    // Load content conditionally based on docType
    LaunchedEffect(uri, docType) {
        when (docType) {
            DocType.PDF -> {
                isPdfLoading = true
                val count = pdfRendererManager.getPageCount(uri)
                if (count > 0) {
                    pdfPageCount = count
                    pdfRendererManager.prefetchAdjacentPages(uri, activeDocument.lastReadPage, count)
                }
                isPdfLoading = false
            }
            DocType.WORD -> {
                isWordLoading = true
                wordParagraphs = documentContentParser.parseDocx(uri)
                isWordLoading = false
            }
            DocType.PPT -> {
                isPptLoading = true
                pptSlides = documentContentParser.parsePptx(uri)
                isPptLoading = false
            }
            DocType.TXT, DocType.MARKDOWN -> {
                isTextLoading = true
                textContent = documentContentParser.readTextOrMarkdown(uri)
                isTextLoading = false
            }
            DocType.IMAGE, DocType.OTHER -> {
                // Handled in composable
            }
        }
    }

    DisposableEffect(uri) {
        onDispose {
            if (docType == DocType.PDF) {
                pdfRendererManager.closeActiveSession()
            }
        }
    }

    val pdfListState = rememberLazyListState(initialFirstVisibleItemIndex = activeDocument.lastReadPage)
    val pdfPagerState = rememberPagerState(
        initialPage = activeDocument.lastReadPage.coerceIn(0, (pdfPageCount - 1).coerceAtLeast(0)),
        pageCount = { pdfPageCount }
    )

    val currentPdfPage = if (isHorizontalMode) {
        pdfPagerState.currentPage
    } else {
        pdfListState.firstVisibleItemIndex
    }

    LaunchedEffect(currentPdfPage, pdfPageCount, activeDocument.id) {
        if (docType == DocType.PDF) {
            documentRepository.recordOpened(activeDocument.id, currentPdfPage)
            if (pdfPageCount > 0) {
                pdfRendererManager.prefetchAdjacentPages(uri, currentPdfPage, pdfPageCount)
            }
        }
    }

    BackHandler {
        onBack()
    }

    fun shareCurrentDoc() {
        try {
            val shareUri = if (uri.scheme == "file") {
                val file = File(uri.path ?: return)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                uri
            }
            val mime = when (docType) {
                DocType.PDF -> "application/pdf"
                DocType.WORD -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                DocType.PPT -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                DocType.TXT -> "text/plain"
                DocType.MARKDOWN -> "text/markdown"
                DocType.IMAGE -> "image/*"
                DocType.OTHER -> "*/*"
            }
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Bagikan Dokumen"))
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membagikan: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun convertCurrentDocumentToPdf() {
        scope.launch {
            isConvertingToPdf = true
            val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
            val cleanBaseName = activeDocument.displayName.substringBeforeLast(".")
            val targetPdfFile = File(docsDir, "${cleanBaseName}_Converted_${System.currentTimeMillis()}.pdf")

            val success = when (docType) {
                DocType.WORD -> {
                    documentContentParser.convertTextToPdf(cleanBaseName, wordParagraphs, targetPdfFile)
                }
                DocType.PPT -> {
                    documentContentParser.convertSlidesToPdf(cleanBaseName, pptSlides, targetPdfFile)
                }
                DocType.TXT, DocType.MARKDOWN -> {
                    val lines = textContent.lines()
                    documentContentParser.convertTextToPdf(cleanBaseName, lines, targetPdfFile)
                }
                DocType.IMAGE -> {
                    pdfProcessor.imagesToPdf(listOf(uri), targetPdfFile)
                }
                else -> false
            }

            isConvertingToPdf = false
            if (success && targetPdfFile.exists()) {
                val newDocId = documentRepository.registerLocalPdf(targetPdfFile, targetPdfFile.name, isScanned = false)
                Toast.makeText(context, "Berhasil dikonversi ke PDF!", Toast.LENGTH_SHORT).show()
                onDocumentConvertedToPdf(newDocId)
            } else {
                Toast.makeText(context, "Gagal mengonversi ke PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = activeDocument.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subtitle = when (docType) {
                                    DocType.PDF -> stringResource(R.string.reader_page_indicator, currentPdfPage + 1, pdfPageCount)
                                    DocType.WORD -> "Microsoft Word (${wordParagraphs.size} paragraf)"
                                    DocType.PPT -> "Presentasi PPT (${pptSlides.size} slide)"
                                    DocType.TXT -> "Berkas Teks TXT"
                                    DocType.MARKDOWN -> "Dokumen Markdown (.md)"
                                    DocType.IMAGE -> "Berkas Gambar"
                                    DocType.OTHER -> "Dokumen"
                                } + if (documents.size > 1) " • Tab ${activeIndex + 1}/${documents.size}" else ""
                                Text(
                                    text = subtitle,
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
                            // If non-PDF: show "Konversi ke PDF" button
                            if (docType != DocType.PDF) {
                                Button(
                                    onClick = { convertCurrentDocumentToPdf() },
                                    enabled = !isConvertingToPdf,
                                    colors = ButtonDefaults.buttonColors(containerColor = BrightTeal),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = PrimaryNavy,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isConvertingToPdf) "Mengonversi..." else "Ke PDF",
                                        color = PrimaryNavy,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // If text/word/markdown: Font size adjuster
                            if (docType == DocType.WORD || docType == DocType.TXT || docType == DocType.MARKDOWN) {
                                IconButton(onClick = {
                                    fontSizeSp = if (fontSizeSp >= 22f) 14f else fontSizeSp + 2f
                                }) {
                                    Icon(Icons.Default.FormatSize, contentDescription = "Ukuran Huruf")
                                }
                            }

                            // If PDF: Bookmark toggle for current page
                            if (docType == DocType.PDF) {
                                val isCurrentBookmarked = bookmarks.any { it.pageIndex == currentPdfPage }
                                IconButton(onClick = {
                                    scope.launch {
                                        documentRepository.toggleBookmark(activeDocument.id, currentPdfPage)
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (isCurrentBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = "Bookmark",
                                        tint = if (isCurrentBookmarked) StatusWarning else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Switch vertical / horizontal mode for PDF
                                IconButton(onClick = { isHorizontalMode = !isHorizontalMode }) {
                                    Icon(
                                        imageVector = if (isHorizontalMode) Icons.Default.ViewAgenda else Icons.Default.ViewCarousel,
                                        contentDescription = "Mode Switch"
                                    )
                                }
                            }

                            // Multi-doc visual organizer
                            IconButton(onClick = onOpenMultiDocEditor) {
                                Icon(
                                    imageVector = Icons.Default.DashboardCustomize,
                                    contentDescription = "Edit Multi Dokumen",
                                    tint = BrightTeal
                                )
                            }

                            // More actions overflow menu
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Buka Berkas Lain...") },
                                        leadingIcon = { Icon(Icons.Outlined.FileOpen, null) },
                                        onClick = {
                                            menuExpanded = false
                                            addDocumentLauncher.launch(DocumentContentParser.ALL_SUPPORTED_MIME_TYPES)
                                        }
                                    )
                                    if (docType == DocType.PDF) {
                                        DropdownMenuItem(
                                            text = { Text("Daftar Penanda (${bookmarks.size})") },
                                            leadingIcon = { Icon(Icons.Outlined.Bookmarks, null) },
                                            onClick = {
                                                menuExpanded = false
                                                showBookmarksSheet = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Lompat ke Halaman") },
                                            leadingIcon = { Icon(Icons.Outlined.FindInPage, null) },
                                            onClick = {
                                                menuExpanded = false
                                                showJumpDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Cetak PDF") },
                                            leadingIcon = { Icon(Icons.Outlined.Print, null) },
                                            onClick = {
                                                menuExpanded = false
                                                printPdf(context, uri, activeDocument.displayName)
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(if (isNightMode) "Mode Terang" else "Mode Malam") },
                                        leadingIcon = { Icon(Icons.Outlined.DarkMode, null) },
                                        onClick = {
                                            menuExpanded = false
                                            isNightMode = !isNightMode
                                        }
                                    )
                                    if (docType == DocType.IMAGE) {
                                        DropdownMenuItem(
                                            text = { Text("Ekstrak Teks (OCR)") },
                                            leadingIcon = { Icon(Icons.Outlined.TextFields, null) },
                                            onClick = {
                                                menuExpanded = false
                                                scope.launch {
                                                    isRunningOcr = true
                                                    val res = ocrManager.recognizeTextFromUri(uri)
                                                    isRunningOcr = false
                                                    ocrResultText = res.getOrNull() ?: "Teks tidak ditemukan dalam gambar."
                                                }
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Bagikan") },
                                        leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                        onClick = {
                                            menuExpanded = false
                                            shareCurrentDoc()
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = if (isNightMode) DarkSurface else MaterialTheme.colorScheme.surface
                        )
                    )

                    // Multi-File Tabs Strip
                    Surface(
                        color = if (isNightMode) NavySurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(documents, key = { _, doc -> doc.id }) { index, doc ->
                                val isSelected = index == activeIndex
                                val tabDocType = documentContentParser.determineDocType(doc.displayName, doc.mimeType)
                                val (tabIcon, tabTint) = getDocTypeIcon(tabDocType)

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) BrightTeal else if (isNightMode) DarkSurface else Color.White,
                                    border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                    shadowElevation = if (isSelected) 2.dp else 0.dp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { activeIndex = index }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = tabIcon,
                                            contentDescription = null,
                                            tint = if (isSelected) PrimaryNavy else tabTint,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = doc.displayName,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) PrimaryNavy else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            modifier = Modifier.widthIn(max = 140.dp),
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (documents.size > 1) {
                                            IconButton(
                                                onClick = { onCloseDocument(doc) },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Close Tab",
                                                    tint = if (isSelected) PrimaryNavy else Color.Gray,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // "+ Tambah" Quick Button
                            item {
                                OutlinedButton(
                                    onClick = { addDocumentLauncher.launch(DocumentContentParser.ALL_SUPPORTED_MIME_TYPES) },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tambah", style = MaterialTheme.typography.labelSmall)
                                }
                            }
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
                .background(if (isNightMode) Color(0xFF0B1724) else Color(0xFFEDF2F7))
        ) {
            when (docType) {
                DocType.PDF -> {
                    if (isPdfLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = BrightTeal)
                        }
                    } else if (isHorizontalMode) {
                        HorizontalPager(
                            state = pdfPagerState,
                            beyondViewportPageCount = 2,
                            modifier = Modifier.fillMaxSize(),
                            pageSpacing = 16.dp
                        ) { pageIndex ->
                            ZoomablePdfPage(
                                uri = uri,
                                pageIndex = pageIndex,
                                pdfRendererManager = pdfRendererManager,
                                isNightMode = isNightMode,
                                onSingleTap = { showControls = !showControls }
                            )
                        }
                    } else {
                        LazyColumn(
                            state = pdfListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp)
                        ) {
                            items(pdfPageCount) { pageIndex ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    ZoomablePdfPage(
                                        uri = uri,
                                        pageIndex = pageIndex,
                                        pdfRendererManager = pdfRendererManager,
                                        isNightMode = isNightMode,
                                        onSingleTap = { showControls = !showControls }
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Floating Navigation & Pager Bar for PDF
                    AnimatedVisibility(
                        visible = showControls,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = PrimaryNavy.copy(alpha = 0.92f),
                            shadowElevation = 6.dp,
                            tonalElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (currentPdfPage > 0) {
                                            scope.launch {
                                                if (isHorizontalMode) {
                                                    pdfPagerState.animateScrollToPage(currentPdfPage - 1)
                                                } else {
                                                    pdfListState.animateScrollToItem(currentPdfPage - 1)
                                                }
                                            }
                                        }
                                    },
                                    enabled = currentPdfPage > 0
                                ) {
                                    Icon(
                                        Icons.Default.ChevronLeft,
                                        contentDescription = "Previous Page",
                                        tint = if (currentPdfPage > 0) BrightTeal else Color.Gray
                                    )
                                }

                                TextButton(onClick = { showJumpDialog = true }) {
                                    Text(
                                        text = "${currentPdfPage + 1} / $pdfPageCount",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (currentPdfPage < pdfPageCount - 1) {
                                            scope.launch {
                                                if (isHorizontalMode) {
                                                    pdfPagerState.animateScrollToPage(currentPdfPage + 1)
                                                } else {
                                                    pdfListState.animateScrollToItem(currentPdfPage + 1)
                                                }
                                            }
                                        }
                                    },
                                    enabled = currentPdfPage < pdfPageCount - 1
                                ) {
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = "Next Page",
                                        tint = if (currentPdfPage < pdfPageCount - 1) BrightTeal else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                DocType.WORD -> {
                    if (isWordLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = BrightTeal)
                        }
                    } else {
                        WordDocumentView(
                            paragraphs = wordParagraphs,
                            isNightMode = isNightMode,
                            fontSizeSp = fontSizeSp,
                            onSingleTap = { showControls = !showControls }
                        )
                    }
                }

                DocType.PPT -> {
                    if (isPptLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = BrightTeal)
                        }
                    } else {
                        PptPresentationView(
                            slides = pptSlides,
                            isNightMode = isNightMode,
                            onSingleTap = { showControls = !showControls }
                        )
                    }
                }

                DocType.TXT, DocType.MARKDOWN -> {
                    if (isTextLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = BrightTeal)
                        }
                    } else {
                        TextOrMarkdownView(
                            content = textContent,
                            isMarkdown = (docType == DocType.MARKDOWN),
                            isNightMode = isNightMode,
                            fontSizeSp = fontSizeSp,
                            onSingleTap = { showControls = !showControls }
                        )
                    }
                }

                DocType.IMAGE -> {
                    ZoomableImageView(
                        uri = uri,
                        documentContentParser = documentContentParser,
                        isNightMode = isNightMode,
                        onSingleTap = { showControls = !showControls }
                    )
                }

                DocType.OTHER -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.HelpOutline, null, tint = Color.Gray, modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Format berkas belum didukung sepenuhnya.")
                        }
                    }
                }
            }
        }
    }

    // OCR Result Bottom Sheet
    if (ocrResultText != null) {
        ModalBottomSheet(onDismissRequest = { ocrResultText = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hasil OCR Teks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("OCR Text", ocrResultText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Teks berhasil disalin ke clipboard", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrightTeal)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, tint = PrimaryNavy, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salin", color = PrimaryNavy, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 340.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    LazyColumn(modifier = Modifier.padding(14.dp)) {
                        item {
                            Text(
                                text = ocrResultText ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 24.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Bookmarks Sheet
    if (showBookmarksSheet) {
        ModalBottomSheet(onDismissRequest = { showBookmarksSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Penanda Buku (${bookmarks.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (bookmarks.isEmpty()) {
                    Text(
                        text = "Belum ada penanda di dokumen ini. Ketuk ikon penanda di bilah atas untuk menandai halaman saat ini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(bookmarks.size) { idx ->
                            val bookmark = bookmarks[idx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Bookmark, null, tint = StatusWarning)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Halaman ${bookmark.pageIndex + 1}",
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (bookmark.title.isNotBlank()) {
                                            Text(
                                                text = bookmark.title,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Row {
                                    TextButton(onClick = {
                                        showBookmarksSheet = false
                                        scope.launch {
                                            if (isHorizontalMode) {
                                                pdfPagerState.scrollToPage(bookmark.pageIndex)
                                            } else {
                                                pdfListState.scrollToItem(bookmark.pageIndex)
                                            }
                                        }
                                    }) {
                                        Text("Buka")
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            documentRepository.toggleBookmark(activeDocument.id, bookmark.pageIndex)
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, "Delete", tint = StatusError)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    // Jump to Page Dialog
    if (showJumpDialog) {
        var pageInput by remember { mutableStateOf((currentPdfPage + 1).toString()) }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Lompat ke Halaman") },
            text = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it.filter { char -> char.isDigit() } },
                    label = { Text("Nomor Halaman (1 - $pdfPageCount)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = (pageInput.toIntOrNull() ?: 1) - 1
                        val clamped = target.coerceIn(0, pdfPageCount - 1)
                        scope.launch {
                            if (isHorizontalMode) {
                                pdfPagerState.animateScrollToPage(clamped)
                            } else {
                                pdfListState.animateScrollToItem(clamped)
                            }
                        }
                        showJumpDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
                ) {
                    Text("Lompat", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

private fun getDocTypeIcon(docType: DocType): Pair<ImageVector, Color> {
    return when (docType) {
        DocType.PDF -> Pair(Icons.Default.PictureAsPdf, RoyalBlue)
        DocType.WORD -> Pair(Icons.Default.Description, RoyalBlue)
        DocType.PPT -> Pair(Icons.Default.CoPresent, Color(0xFFF97316))
        DocType.TXT -> Pair(Icons.Default.Notes, DarkTeal)
        DocType.MARKDOWN -> Pair(Icons.Default.Article, DarkTeal)
        DocType.IMAGE -> Pair(Icons.Default.Image, Color(0xFF10B981))
        DocType.OTHER -> Pair(Icons.Default.Article, Color.Gray)
    }
}

@Composable
fun WordDocumentView(
    paragraphs: List<String>,
    isNightMode: Boolean,
    fontSizeSp: Float,
    onSingleTap: () -> Unit
) {
    val totalWords = remember(paragraphs) {
        paragraphs.sumOf { it.split("\\s+".toRegex()).count { w -> w.isNotBlank() } }
    }
    val readingTimeMin = (totalWords / 200).coerceAtLeast(1)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onSingleTap() })
            },
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp)
    ) {
        item {
            // Stats Badge
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isNightMode) NavySurface else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, null, tint = RoyalBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${paragraphs.size} Paragraf • $totalWords Kata",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isNightMode) Color.White else PrimaryNavy
                        )
                    }
                    Text(
                        text = "~$readingTimeMin mnt baca",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(paragraphs) { p ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isNightMode) DarkSurface else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    text = p,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.55f).sp,
                    color = if (isNightMode) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                    modifier = Modifier.padding(18.dp)
                )
            }
        }
    }
}

@Composable
fun PptPresentationView(
    slides: List<SlideContent>,
    isNightMode: Boolean,
    onSingleTap: () -> Unit
) {
    if (slides.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Tidak ada slide yang terdeteksi dalam berkas presentasi.")
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            pageSpacing = 16.dp
        ) { page ->
            val slide = slides[page]
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isNightMode) DarkSurface else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onSingleTap() })
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Slide header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF97316).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "SLIDE ${slide.slideNumber} / ${slides.size}",
                                color = Color(0xFFF97316),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = slide.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isNightMode) Color.White else PrimaryNavy
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Bullets list
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(slide.bullets) { bullet ->
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(BrightTeal)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = bullet,
                                    style = MaterialTheme.typography.bodyLarge,
                                    lineHeight = 26.sp,
                                    color = if (isNightMode) Color(0xFFCBD5E1) else Color(0xFF334155)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Slide presentation navigator
        Surface(
            color = if (isNightMode) NavySurface else MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                    },
                    enabled = pagerState.currentPage > 0
                ) {
                    Icon(Icons.Default.ChevronLeft, "Previous Slide")
                }

                Text(
                    text = "Slide ${pagerState.currentPage + 1} dari ${slides.size}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )

                IconButton(
                    onClick = {
                        if (pagerState.currentPage < slides.size - 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    enabled = pagerState.currentPage < slides.size - 1
                ) {
                    Icon(Icons.Default.ChevronRight, "Next Slide")
                }
            }
        }
    }
}

@Composable
fun TextOrMarkdownView(
    content: String,
    isMarkdown: Boolean,
    isNightMode: Boolean,
    fontSizeSp: Float,
    onSingleTap: () -> Unit
) {
    val lines = remember(content) { content.lines() }
    val totalWords = remember(content) {
        content.split("\\s+".toRegex()).count { it.isNotBlank() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onSingleTap() })
            },
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isNightMode) NavySurface else MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isMarkdown) "Markdown" else "Plain Text",
                        fontWeight = FontWeight.Bold,
                        color = BrightTeal
                    )
                    Text(
                        text = "${lines.size} Baris • $totalWords Kata",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(lines) { line ->
            if (isMarkdown && line.startsWith("#")) {
                val headerLevel = line.takeWhile { it == '#' }.length
                val headerText = line.drop(headerLevel).trim()
                val headerSize = when (headerLevel) {
                    1 -> (fontSizeSp * 1.5f).sp
                    2 -> (fontSizeSp * 1.3f).sp
                    else -> (fontSizeSp * 1.15f).sp
                }
                Text(
                    text = headerText,
                    fontSize = headerSize,
                    fontWeight = FontWeight.Bold,
                    color = if (isNightMode) Color(0xFF38BDF8) else PrimaryNavy,
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                )
            } else if (isMarkdown && (line.startsWith("- ") || line.startsWith("* "))) {
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text("•", fontWeight = FontWeight.Bold, color = BrightTeal, fontSize = fontSizeSp.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = line.drop(2),
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.5f).sp,
                        color = if (isNightMode) Color(0xFFE2E8F0) else Color(0xFF1E293B)
                    )
                }
            } else if (line.startsWith("```")) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isNightMode) Color(0xFF020617) else Color(0xFFF1F5F9),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (fontSizeSp * 0.9f).sp,
                        color = if (isNightMode) Color(0xFF38BDF8) else Color(0xFF0F172A),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Text(
                    text = line,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.55f).sp,
                    color = if (isNightMode) Color(0xFFE2E8F0) else Color(0xFF1E293B),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ZoomableImageView(
    uri: Uri,
    documentContentParser: DocumentContentParser,
    isNightMode: Boolean,
    onSingleTap: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(uri) {
        bitmap = documentContentParser.decodeImageBitmap(uri)
    }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = newScale
        if (newScale > 1.05f) {
            val maxOffsetX = (newScale - 1f) * 500f
            val maxOffsetY = (newScale - 1f) * 700f
            offset = Offset(
                x = (offset.x + offsetChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                y = (offset.y + offsetChange.y).coerceIn(-maxOffsetY, maxOffsetY)
            )
        } else {
            offset = Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                    onTap = { onSingleTap() }
                )
            }
            .transformable(state = transformState),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Image View",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
            if (isNightMode) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
            }
        } else {
            CircularProgressIndicator(color = BrightTeal)
        }
    }
}

@Composable
fun ZoomablePdfPage(
    uri: Uri,
    pageIndex: Int,
    pdfRendererManager: PdfRendererManager,
    isNightMode: Boolean,
    onSingleTap: () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val isZoomed = scale > 1.05f

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = newScale
        if (newScale > 1.05f) {
            val maxOffsetX = (newScale - 1f) * 400f
            val maxOffsetY = (newScale - 1f) * 600f
            offset = Offset(
                x = (offset.x + offsetChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                y = (offset.y + offsetChange.y).coerceIn(-maxOffsetY, maxOffsetY)
            )
        } else {
            offset = Offset.Zero
        }
    }

    LaunchedEffect(uri, pageIndex) {
        bitmap = pdfRendererManager.renderPage(uri, pageIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.4f
                            }
                        },
                        onTap = { onSingleTap() }
                    )
                }
                .transformable(
                    state = transformState,
                    enabled = isZoomed
                ),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page ${pageIndex + 1}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                    if (isNightMode) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), color = BrightTeal)
                    }
                }
            }
        }
    }
}

fun printPdf(context: Context, uri: Uri, documentTitle: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
    if (printManager == null) {
        Toast.makeText(context, "Layanan pencetakan tidak tersedia", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val printAdapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = android.print.PrintDocumentInfo.Builder(documentTitle)
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: android.os.ParcelFileDescriptor?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val outputStream = FileOutputStream(destination?.fileDescriptor)
                    val buffer = ByteArray(1024 * 8)
                    var bytesRead: Int
                    while (inputStream?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    inputStream?.close()
                    outputStream.close()
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }
        printManager.print("PDF_Studio_$documentTitle", printAdapter, PrintAttributes.Builder().build())
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal memulai cetak: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
