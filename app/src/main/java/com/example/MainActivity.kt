package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.local.entity.DocumentEntity
import com.example.ui.screens.documents.DocumentsScreen
import com.example.ui.screens.editor.MultiDocumentEditorScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.ocr.OcrScreen
import com.example.ui.screens.reader.ReaderScreen
import com.example.ui.screens.scanner.ScannerScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.tools.ToolsScreen
import com.example.ui.theme.BrightTeal
import com.example.ui.theme.PDFStudioProTheme
import com.example.ui.theme.PrimaryNavy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Documents : Screen("documents")
    object Scan : Screen("scan")
    object Tools : Screen("tools")
    object Settings : Screen("settings")
    object Reader : Screen("reader")
    object Ocr : Screen("ocr")
    object MultiDocEditor : Screen("multi_doc_editor")
}

data class NavItem(
    val screen: Screen,
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as PdfStudioApplication
        val repository = app.documentRepository
        val pdfRendererManager = app.pdfRendererManager
        val pdfProcessor = app.pdfProcessor
        val scannerManager = app.scannerManager
        val ocrManager = app.ocrManager

        // Handle external ACTION_VIEW intent if user opened a PDF file from another app
        var incomingDocUri: Uri? = null
        if (intent?.action == Intent.ACTION_VIEW && intent?.data != null) {
            incomingDocUri = intent.data
        }

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkTheme by remember { mutableStateOf(systemDark) }

            PDFStudioProTheme(darkTheme = isDarkTheme) {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

                // Multi-file reader session state
                val readerDocuments = remember { mutableStateListOf<DocumentEntity>() }
                var readerInitialIndex by remember { mutableStateOf(0) }

                // Multi-file editor session state
                val editorDocuments = remember { mutableStateListOf<DocumentEntity>() }

                val coroutineScope = rememberCoroutineScope()
                val context = LocalContext.current

                // Collect documents state
                val allDocuments by repository.allDocuments.collectAsState(initial = emptyList())
                val recentDocuments by repository.recentDocuments.collectAsState(initial = emptyList())
                val favoriteDocuments by repository.favoriteDocuments.collectAsState(initial = emptyList())
                val scannedDocuments by repository.scannedDocuments.collectAsState(initial = emptyList())

                fun openInReader(doc: DocumentEntity) {
                    val existingIdx = readerDocuments.indexOfFirst { it.id == doc.id }
                    if (existingIdx != -1) {
                        readerInitialIndex = existingIdx
                    } else {
                        readerDocuments.add(doc)
                        readerInitialIndex = readerDocuments.lastIndex
                    }
                    currentScreen = Screen.Reader
                }

                fun openMultipleInReader(docs: List<DocumentEntity>) {
                    if (docs.isEmpty()) return
                    docs.forEach { doc ->
                        if (readerDocuments.none { it.id == doc.id }) {
                            readerDocuments.add(doc)
                        }
                    }
                    readerInitialIndex = readerDocuments.indexOfFirst { it.id == docs.first().id }.coerceAtLeast(0)
                    currentScreen = Screen.Reader
                }

                fun importAndOpenPdfs(uris: List<Uri>) {
                    coroutineScope.launch {
                        val importedDocs = mutableListOf<DocumentEntity>()
                        for (uri in uris) {
                            val id = repository.importDocumentFromUri(uri)
                            repository.getDocumentById(id)?.let { doc ->
                                importedDocs.add(doc)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            if (importedDocs.isNotEmpty()) {
                                openMultipleInReader(importedDocs)
                            }
                        }
                    }
                }

                // Process external intent if present
                LaunchedEffect(incomingDocUri) {
                    incomingDocUri?.let { uri ->
                        coroutineScope.launch {
                            val docId = repository.importDocumentFromUri(uri)
                            repository.getDocumentById(docId)?.let { doc ->
                                openInReader(doc)
                            }
                        }
                    }
                }

                val navItems = listOf(
                    NavItem(Screen.Home, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
                    NavItem(Screen.Documents, R.string.nav_documents, Icons.Filled.Folder, Icons.Outlined.Folder),
                    NavItem(Screen.Scan, R.string.nav_scan, Icons.Filled.DocumentScanner, Icons.Outlined.DocumentScanner),
                    NavItem(Screen.Tools, R.string.nav_tools, Icons.Filled.AutoFixHigh, Icons.Outlined.AutoFixHigh),
                    NavItem(Screen.Settings, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
                )

                // Share document helper
                fun shareDocument(doc: DocumentEntity) {
                    try {
                        val uri = Uri.parse(doc.uri)
                        val shareUri = if (uri.scheme == "file") {
                            val file = File(uri.path ?: return)
                            FileProvider.getUriForFile(context, "${packageName}.fileprovider", file)
                        } else {
                            uri
                        }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, shareUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Bagikan Dokumen"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Gagal membagikan: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                // Handle system back button for secondary screens
                if (currentScreen != Screen.Home) {
                    BackHandler {
                        currentScreen = Screen.Home
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        // Only show bottom navigation for main tabs
                        if (currentScreen in listOf(Screen.Home, Screen.Documents, Screen.Tools, Screen.Settings)) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.navigationBarsPadding()
                            ) {
                                navItems.forEach { item ->
                                    val isSelected = currentScreen == item.screen
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            currentScreen = item.screen
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = stringResource(item.titleRes)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = stringResource(item.titleRes),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = PrimaryNavy,
                                            indicatorColor = BrightTeal
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Crossfade(
                        targetState = currentScreen,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                bottom = if (currentScreen in listOf(Screen.Home, Screen.Documents, Screen.Tools, Screen.Settings)) {
                                    innerPadding.calculateBottomPadding()
                                } else 0.dp
                            )
                    ) { screen ->
                        when (screen) {
                            Screen.Home -> HomeScreen(
                                recentDocuments = recentDocuments,
                                onOpenDocument = { doc -> openInReader(doc) },
                                onImportPdfs = { uris -> importAndOpenPdfs(uris) },
                                onNavigateToScan = { currentScreen = Screen.Scan },
                                onNavigateToOcr = { currentScreen = Screen.Ocr },
                                onNavigateToTool = { _ -> currentScreen = Screen.Tools },
                                onToggleFavorite = { doc ->
                                    coroutineScope.launch { repository.toggleFavorite(doc.id, doc.isFavorite) }
                                },
                                onRenameDocument = { doc, newName ->
                                    coroutineScope.launch { repository.renameDocument(doc.id, newName) }
                                },
                                onDeleteDocument = { doc ->
                                    coroutineScope.launch {
                                        repository.deleteDocument(doc.id)
                                        readerDocuments.removeAll { it.id == doc.id }
                                    }
                                },
                                onShareDocument = { doc -> shareDocument(doc) },
                                onSearchClick = { currentScreen = Screen.Documents },
                                onSettingsClick = { currentScreen = Screen.Settings }
                            )

                            Screen.Documents -> DocumentsScreen(
                                allDocuments = allDocuments,
                                favoriteDocuments = favoriteDocuments,
                                scannedDocuments = scannedDocuments,
                                onOpenDocument = { doc -> openInReader(doc) },
                                onOpenMultipleDocuments = { docs -> openMultipleInReader(docs) },
                                onEditMultipleDocuments = { docs ->
                                    editorDocuments.clear()
                                    editorDocuments.addAll(docs)
                                    currentScreen = Screen.MultiDocEditor
                                },
                                onImportPdfs = { uris -> importAndOpenPdfs(uris) },
                                onToggleFavorite = { doc ->
                                    coroutineScope.launch { repository.toggleFavorite(doc.id, doc.isFavorite) }
                                },
                                onRenameDocument = { doc, newName ->
                                    coroutineScope.launch { repository.renameDocument(doc.id, newName) }
                                },
                                onDeleteDocument = { doc ->
                                    coroutineScope.launch {
                                        repository.deleteDocument(doc.id)
                                        readerDocuments.removeAll { it.id == doc.id }
                                        editorDocuments.removeAll { it.id == doc.id }
                                    }
                                },
                                onShareDocument = { doc -> shareDocument(doc) }
                            )

                            Screen.Scan -> ScannerScreen(
                                scannerManager = scannerManager,
                                documentRepository = repository,
                                onBack = { currentScreen = Screen.Home },
                                onDocumentCreated = { docId ->
                                    coroutineScope.launch {
                                        repository.getDocumentById(docId)?.let { doc ->
                                            openInReader(doc)
                                        } ?: run {
                                            currentScreen = Screen.Documents
                                        }
                                    }
                                }
                            )

                            Screen.Tools -> ToolsScreen(
                                pdfProcessor = pdfProcessor,
                                pdfRendererManager = pdfRendererManager,
                                documentRepository = repository,
                                onDocumentCreated = { docId ->
                                    coroutineScope.launch {
                                        repository.getDocumentById(docId)?.let { doc ->
                                            openInReader(doc)
                                        }
                                    }
                                },
                                onOpenMultiDocEditor = { uris ->
                                    coroutineScope.launch {
                                        val docs = mutableListOf<DocumentEntity>()
                                        for (u in uris) {
                                            val id = repository.importDocumentFromUri(u)
                                            repository.getDocumentById(id)?.let { docs.add(it) }
                                        }
                                        withContext(Dispatchers.Main) {
                                            editorDocuments.clear()
                                            editorDocuments.addAll(docs)
                                            currentScreen = Screen.MultiDocEditor
                                        }
                                    }
                                }
                            )

                            Screen.Ocr -> OcrScreen(
                                ocrManager = ocrManager,
                                onBack = { currentScreen = Screen.Home }
                            )

                            Screen.Settings -> SettingsScreen(
                                pdfRendererManager = pdfRendererManager,
                                isDarkTheme = isDarkTheme,
                                onThemeToggle = { isDarkTheme = it }
                            )

                            Screen.Reader -> {
                                if (readerDocuments.isNotEmpty()) {
                                    ReaderScreen(
                                        documents = readerDocuments,
                                        initialDocumentIndex = readerInitialIndex,
                                        pdfRendererManager = pdfRendererManager,
                                        documentRepository = repository,
                                        documentContentParser = app.documentContentParser,
                                        pdfProcessor = pdfProcessor,
                                        ocrManager = ocrManager,
                                        onBack = { currentScreen = Screen.Home },
                                        onAddDocuments = { uris -> importAndOpenPdfs(uris) },
                                        onCloseDocument = { docToClose ->
                                            readerDocuments.remove(docToClose)
                                            if (readerDocuments.isEmpty()) {
                                                currentScreen = Screen.Home
                                            }
                                        },
                                        onOpenMultiDocEditor = {
                                            editorDocuments.clear()
                                            editorDocuments.addAll(readerDocuments)
                                            currentScreen = Screen.MultiDocEditor
                                        },
                                        onDocumentConvertedToPdf = { newDocId ->
                                            coroutineScope.launch {
                                                repository.getDocumentById(newDocId)?.let { newDoc ->
                                                    openInReader(newDoc)
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    currentScreen = Screen.Home
                                }
                            }

                            Screen.MultiDocEditor -> {
                                MultiDocumentEditorScreen(
                                    initialDocuments = editorDocuments,
                                    pdfProcessor = pdfProcessor,
                                    pdfRendererManager = pdfRendererManager,
                                    documentRepository = repository,
                                    onBack = { currentScreen = Screen.Home },
                                    onDocumentSaved = { newDocId ->
                                        coroutineScope.launch {
                                            repository.getDocumentById(newDocId)?.let { newDoc ->
                                                openInReader(newDoc)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
