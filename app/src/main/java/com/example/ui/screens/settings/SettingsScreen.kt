package com.example.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.pdf.PdfRendererManager
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.formatFileSize
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    pdfRendererManager: PdfRendererManager,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    var cacheSize by remember { mutableStateOf(calculateCacheSize(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Offline security badge
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TealSoft.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(PremiumTeal),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Security, null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_offline_notice),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryNavy
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Dokumen Anda tidak pernah diunggah ke server cloud mana pun.",
                                style = MaterialTheme.typography.bodySmall,
                                color = PrimaryNavy.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Section: Appearance
            item {
                Text(
                    text = stringResource(R.string.settings_appearance),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.DarkMode, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("Tema Gelap (Dark Mode)", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Beralih antara tema gelap dan terang",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { onThemeToggle(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = BrightTeal, checkedTrackColor = PrimaryNavy)
                        )
                    }
                }
            }

            // Section: Storage
            item {
                Text(
                    text = stringResource(R.string.settings_storage),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        SettingsClickableRow(
                            icon = Icons.Outlined.CleaningServices,
                            title = stringResource(R.string.settings_clear_cache),
                            subtitle = "Ukuran cache saat ini: ${formatFileSize(cacheSize)}",
                            onClick = { showClearCacheDialog = true }
                        )
                    }
                }
            }

            // Section: Info
            item {
                Text(
                    text = "Informasi & Privasi",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        SettingsClickableRow(
                            icon = Icons.Outlined.PrivacyTip,
                            title = stringResource(R.string.settings_privacy),
                            subtitle = "Informasi perlindungan data dan privasi pengguna",
                            onClick = { showPrivacyDialog = true }
                        )
                        Divider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsClickableRow(
                            icon = Icons.Outlined.Info,
                            title = stringResource(R.string.settings_about),
                            subtitle = stringResource(R.string.settings_version),
                            onClick = { showAboutDialog = true }
                        )
                    }
                }
            }
        }
    }

    // Clear Cache Dialog
    if (showClearCacheDialog) {
        ConfirmDialog(
            title = "Bersihkan Cache?",
            message = "Ini akan menghapus bitmap cache render sementara. Dokumen PDF asli Anda tetap aman.",
            confirmLabel = "Bersihkan",
            isDestructive = false,
            onConfirm = {
                pdfRendererManager.clearCache()
                scope.launch(Dispatchers.IO) {
                    clearAppCache(context)
                    val updated = calculateCacheSize(context)
                    withContext(Dispatchers.Main) {
                        cacheSize = updated
                        showClearCacheDialog = false
                        Toast.makeText(context, "Cache berhasil dibersihkan", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showClearCacheDialog = false }
        )
    }

    // Privacy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Kebijakan Privasi") },
            text = {
                Text(
                    "PDF Studio Pro dirancang dengan prinsip Privacy First.\n\n" +
                    "• Semua proses pembacaan PDF, pemindaian dokumen, pengenalan teks OCR, dan manipulasi berkas (merge, split, watermark, compress) dilakukan 100% secara offline di perangkat Anda.\n\n" +
                    "• Tidak ada pelacak dokumen, tidak ada iklan, dan tidak ada pengiriman data ke server luar."
                )
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
                ) {
                    Text("Tutup", color = Color.White)
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Tentang PDF Studio Pro") },
            text = {
                Column {
                    Text("PDF Studio Pro", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Versi 1.0.0 (Build 1)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Aplikasi produktivitas dokumen Android Native lengkap yang menggabungkan Penampil PDF, Pemindai Dokumen Cerdas, OCR, dan Alat PDF profesional dengan arsitektur Clean + MVVM dan Jetpack Compose."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNavy)
                ) {
                    Text("OK", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

fun calculateCacheSize(context: Context): Long {
    var size = 0L
    context.cacheDir.walkTopDown().forEach { file ->
        if (file.isFile) size += file.length()
    }
    return size
}

fun clearAppCache(context: Context) {
    context.cacheDir.deleteRecursively()
}
