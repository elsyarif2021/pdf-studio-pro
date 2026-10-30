package com.example.ui.screens.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.DocumentEntity
import com.example.ui.components.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recentDocuments: List<DocumentEntity>,
    onOpenDocument: (DocumentEntity) -> Unit,
    onImportPdfs: (List<Uri>) -> Unit,
    onNavigateToScan: () -> Unit,
    onNavigateToOcr: () -> Unit,
    onNavigateToTool: (String) -> Unit,
    onToggleFavorite: (DocumentEntity) -> Unit,
    onRenameDocument: (DocumentEntity, String) -> Unit,
    onDeleteDocument: (DocumentEntity) -> Unit,
    onShareDocument: (DocumentEntity) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onImportPdfs(uris)
        }
    }

    var documentToRename by remember { mutableStateOf<DocumentEntity?>(null) }
    var documentToDelete by remember { mutableStateOf<DocumentEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryNavy),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = BrightTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "PDF Studio Pro",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Hero Banner Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = PrimaryNavy)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Background Hero Graphic
                        Image(
                            painter = painterResource(id = R.drawable.pdf_hero_banner_1793321055590),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                        )
                        // Gradient Overlay for Readability
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            PrimaryNavy.copy(alpha = 0.5f),
                                            PrimaryNavy.copy(alpha = 0.95f)
                                        )
                                    )
                                )
                        )
                        // Card Content
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.hero_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.hero_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TealSoft.copy(alpha = 0.9f),
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = onNavigateToScan,
                                    colors = ButtonDefaults.buttonColors(containerColor = BrightTeal),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("hero_scan_btn")
                                ) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        contentDescription = null,
                                        tint = PrimaryNavy,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.action_scan_now),
                                        color = PrimaryNavy,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                OutlinedButton(
                                    onClick = { openPdfLauncher.launch(com.example.data.reader.DocumentContentParser.ALL_SUPPORTED_MIME_TYPES) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.6f)))
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("hero_open_pdf_btn")
                                ) {
                                    Icon(
                                        Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.action_open_pdf),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Actions Section
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.quick_actions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        QuickActionButton(
                            icon = Icons.Default.DocumentScanner,
                            label = "Pindai",
                            containerColor = TealSoft,
                            contentColor = PremiumTeal,
                            onClick = onNavigateToScan
                        )
                    }
                    item {
                        QuickActionButton(
                            icon = Icons.Default.TextFields,
                            label = "OCR Teks",
                            containerColor = Color(0xFFEFF6FF),
                            contentColor = RoyalBlue,
                            onClick = onNavigateToOcr
                        )
                    }
                    item {
                        QuickActionButton(
                            icon = Icons.Default.CallMerge,
                            label = "Gabung",
                            containerColor = Color(0xFFF3E8FF),
                            contentColor = Color(0xFF9333EA),
                            onClick = { onNavigateToTool("merge") }
                        )
                    }
                    item {
                        QuickActionButton(
                            icon = Icons.Default.CallSplit,
                            label = "Pisah",
                            containerColor = Color(0xFFFEF3C7),
                            contentColor = Color(0xFFD97706),
                            onClick = { onNavigateToTool("split") }
                        )
                    }
                    item {
                        QuickActionButton(
                            icon = Icons.Default.Compress,
                            label = "Kompres",
                            containerColor = Color(0xFFDCFCE7),
                            contentColor = StatusSuccess,
                            onClick = { onNavigateToTool("compress") }
                        )
                    }
                    item {
                        QuickActionButton(
                            icon = Icons.Default.Image,
                            label = "Foto ke PDF",
                            containerColor = Color(0xFFFFEDD5),
                            contentColor = Color(0xFFEA580C),
                            onClick = { onNavigateToTool("image_to_pdf") }
                        )
                    }
                }
            }

            // Recent Documents Section
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.recent_documents),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (recentDocuments.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Outlined.Description,
                        title = stringResource(R.string.no_recent_docs),
                        description = stringResource(R.string.no_recent_docs_desc),
                        actionLabel = stringResource(R.string.action_open_pdf),
                        onActionClick = { openPdfLauncher.launch(com.example.data.reader.DocumentContentParser.ALL_SUPPORTED_MIME_TYPES) }
                    )
                }
            } else {
                items(recentDocuments, key = { it.id }) { doc ->
                    DocumentCard(
                        document = doc,
                        onClick = { onOpenDocument(doc) },
                        onFavoriteToggle = { onToggleFavorite(doc) },
                        onShare = { onShareDocument(doc) },
                        onRename = { documentToRename = doc },
                        onDelete = { documentToDelete = doc },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
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
            message = "Apakah Anda yakin ingin menghapus '${doc.displayName}' dari daftar riwayat dokumen?",
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                onDeleteDocument(doc)
                documentToDelete = null
            },
            onDismiss = { documentToDelete = null }
        )
    }
}
