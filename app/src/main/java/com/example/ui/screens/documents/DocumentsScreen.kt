package com.example.ui.screens.documents

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.DocumentEntity
import com.example.ui.components.*
import com.example.ui.theme.*

enum class DocumentTab {
    ALL,
    FAVORITES,
    SCANNED
}

enum class SortOption {
    DATE_DESC,
    NAME_ASC,
    SIZE_DESC
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentsScreen(
    allDocuments: List<DocumentEntity>,
    favoriteDocuments: List<DocumentEntity>,
    scannedDocuments: List<DocumentEntity>,
    onOpenDocument: (DocumentEntity) -> Unit,
    onOpenMultipleDocuments: (List<DocumentEntity>) -> Unit,
    onEditMultipleDocuments: (List<DocumentEntity>) -> Unit,
    onImportPdfs: (List<Uri>) -> Unit,
    onToggleFavorite: (DocumentEntity) -> Unit,
    onRenameDocument: (DocumentEntity, String) -> Unit,
    onDeleteDocument: (DocumentEntity) -> Unit,
    onShareDocument: (DocumentEntity) -> Unit
) {
    var selectedTab by remember { mutableStateOf(DocumentTab.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var isSearchActive by remember { mutableStateOf(false) }

    val selectedDocIds = remember { mutableStateListOf<Long>() }
    val isMultiSelectMode = selectedDocIds.isNotEmpty()

    var documentToRename by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToDelete by remember { mutableStateOf<DocumentEntity?>(null) }

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onImportPdfs(uris)
        }
    }

    val sourceList = when (selectedTab) {
        DocumentTab.ALL -> allDocuments
        DocumentTab.FAVORITES -> favoriteDocuments
        DocumentTab.SCANNED -> scannedDocuments
    }

    val filteredList = remember(sourceList, searchQuery, sortOption) {
        val filtered = if (searchQuery.isBlank()) {
            sourceList
        } else {
            sourceList.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
        }

        when (sortOption) {
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.modifiedAt }
            SortOption.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
            SortOption.SIZE_DESC -> filtered.sortedByDescending { it.size }
        }
    }

    Scaffold(
        topBar = {
            Column {
                if (isMultiSelectMode) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "${selectedDocIds.size} dipilih",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { selectedDocIds.clear() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                            }
                        },
                        actions = {
                            // "Edit Multi-Dokumen" button
                            IconButton(onClick = {
                                val docsToEdit = sourceList.filter { it.id in selectedDocIds }
                                selectedDocIds.clear()
                                if (docsToEdit.isNotEmpty()) {
                                    onEditMultipleDocuments(docsToEdit)
                                }
                            }) {
                                Icon(Icons.Default.DashboardCustomize, contentDescription = "Edit Multi", tint = BrightTeal)
                            }

                            // "Buka Bersama" (Open Multi-file)
                            Button(
                                onClick = {
                                    val docsToOpen = sourceList.filter { it.id in selectedDocIds }
                                    selectedDocIds.clear()
                                    if (docsToOpen.isNotEmpty()) {
                                        onOpenMultipleDocuments(docsToOpen)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrightTeal),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.MenuBook, null, tint = PrimaryNavy, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buka", color = PrimaryNavy, fontWeight = FontWeight.Bold)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryNavy, titleContentColor = Color.White)
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.nav_documents),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        actions = {
                            IconButton(onClick = { isSearchActive = !isSearchActive }) {
                                Icon(
                                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Outlined.Search,
                                    contentDescription = "Search"
                                )
                            }
                            // Multi-file import action
                            IconButton(onClick = { openPdfLauncher.launch(com.example.data.reader.DocumentContentParser.ALL_SUPPORTED_MIME_TYPES) }) {
                                Icon(Icons.Outlined.FileOpen, contentDescription = "Import Files")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }

                AnimatedVisibility(visible = isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("search_text_field"),
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, "Clear")
                                }
                            }
                        }
                    )
                }

                // Filter Tabs
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = PrimaryNavy,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                            color = BrightTeal
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == DocumentTab.ALL,
                        onClick = { selectedTab = DocumentTab.ALL },
                        text = {
                            Text(
                                stringResource(R.string.all_tab) + " (${allDocuments.size})",
                                fontWeight = if (selectedTab == DocumentTab.ALL) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == DocumentTab.FAVORITES,
                        onClick = { selectedTab = DocumentTab.FAVORITES },
                        text = {
                            Text(
                                stringResource(R.string.favorites_tab) + " (${favoriteDocuments.size})",
                                fontWeight = if (selectedTab == DocumentTab.FAVORITES) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == DocumentTab.SCANNED,
                        onClick = { selectedTab = DocumentTab.SCANNED },
                        text = {
                            Text(
                                stringResource(R.string.scanned_tab) + " (${scannedDocuments.size})",
                                fontWeight = if (selectedTab == DocumentTab.SCANNED) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (!isMultiSelectMode) {
                FloatingActionButton(
                    onClick = { openPdfLauncher.launch(com.example.data.reader.DocumentContentParser.ALL_SUPPORTED_MIME_TYPES) },
                    containerColor = PrimaryNavy,
                    contentColor = BrightTeal,
                    modifier = Modifier
                        .padding(bottom = 80.dp)
                        .testTag("fab_import_pdf")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Import PDF")
                }
            }
        }
    ) { innerPadding ->
        if (filteredList.isEmpty()) {
            EmptyStateView(
                icon = when (selectedTab) {
                    DocumentTab.ALL -> Icons.Outlined.FolderOpen
                    DocumentTab.FAVORITES -> Icons.Outlined.StarBorder
                    DocumentTab.SCANNED -> Icons.Outlined.DocumentScanner
                },
                title = when (selectedTab) {
                    DocumentTab.ALL -> "Tidak ada dokumen"
                    DocumentTab.FAVORITES -> "Belum ada favorit"
                    DocumentTab.SCANNED -> "Belum ada dokumen hasil pindai"
                },
                description = "Buka file PDF dari perangkat atau gunakan pemindai kamera untuk menambahkan dokumen.",
                actionLabel = "Buka PDF",
                onActionClick = { openPdfLauncher.launch(com.example.data.reader.DocumentContentParser.ALL_SUPPORTED_MIME_TYPES) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)
            ) {
                items(filteredList, key = { it.id }) { doc ->
                    val isSelected = doc.id in selectedDocIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isMultiSelectMode) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) selectedDocIds.add(doc.id) else selectedDocIds.remove(doc.id)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = BrightTeal, checkmarkColor = PrimaryNavy)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        DocumentCard(
                            document = doc,
                            onClick = {
                                if (isMultiSelectMode) {
                                    if (isSelected) selectedDocIds.remove(doc.id) else selectedDocIds.add(doc.id)
                                } else {
                                    onOpenDocument(doc)
                                }
                            },
                            onFavoriteToggle = { onToggleFavorite(doc) },
                            onShare = { onShareDocument(doc) },
                            onRename = { documentToRename = doc },
                            onDelete = { documentToDelete = doc },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    // Rename Dialog
    documentToRename?.let { doc ->
        TextInputDialog(
            title = stringResource(R.string.rename),
            initialValue = doc.displayName,
            onConfirm = { newName ->
                onRenameDocument(doc, newName)
                documentToRename = null
            },
            onDismiss = { documentToRename = null }
        )
    }

    // Delete Dialog
    documentToDelete?.let { doc ->
        ConfirmDialog(
            title = stringResource(R.string.delete),
            message = "Apakah Anda yakin ingin menghapus '${doc.displayName}'?",
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                onDeleteDocument(doc)
                documentToDelete = null
            },
            onDismiss = { documentToDelete = null }
        )
    }
}
