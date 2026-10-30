package com.example.ui.screens.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.repository.DocumentRepository
import com.example.data.scanner.ScannerManager
import com.example.ui.components.TextInputDialog
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

data class ScannedPage(
    val id: Long = System.currentTimeMillis() + (0..1000).random(),
    var originalBitmap: Bitmap,
    var filteredBitmap: Bitmap,
    var filter: ScannerManager.ScanFilter = ScannerManager.ScanFilter.MAGIC,
    var rotation: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    scannerManager: ScannerManager,
    documentRepository: DocumentRepository,
    onBack: () -> Unit,
    onDocumentCreated: (Long) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val pages = remember { mutableStateListOf<ScannedPage>() }
    var selectedPageIndex by remember { mutableStateOf(0) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var isSaving by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(flashMode)
            .build()
    }

    // Photo picker from gallery
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                for (uri in uris) {
                    try {
                        val input = context.contentResolver.openInputStream(uri) ?: continue
                        val bmp = BitmapFactory.decodeStream(input)
                        input.close()
                        if (bmp != null) {
                            val filtered = applyFilterLocally(bmp, ScannerManager.ScanFilter.MAGIC)
                            withContext(Dispatchers.Main) {
                                pages.add(
                                    ScannedPage(
                                        originalBitmap = bmp,
                                        filteredBitmap = filtered,
                                        filter = ScannerManager.ScanFilter.MAGIC
                                    )
                                )
                                selectedPageIndex = pages.lastIndex
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.scanner_title),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Flash Toggle
                    IconButton(onClick = {
                        flashMode = if (flashMode == ImageCapture.FLASH_MODE_OFF) {
                            ImageCapture.FLASH_MODE_ON
                        } else {
                            ImageCapture.FLASH_MODE_OFF
                        }
                        imageCapture.flashMode = flashMode
                    }) {
                        Icon(
                            imageVector = if (flashMode == ImageCapture.FLASH_MODE_ON) Icons.Filled.FlashOn else Icons.Outlined.FlashOff,
                            contentDescription = "Flash",
                            tint = if (flashMode == ImageCapture.FLASH_MODE_ON) StatusWarning else Color.White
                        )
                    }

                    // Save / Finish PDF Button
                    if (pages.isNotEmpty()) {
                        Button(
                            onClick = { showSaveDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = BrightTeal),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Selesai (${pages.size})", color = PrimaryNavy, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryNavy)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            if (!hasCameraPermission) {
                // Permission Request State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(NavySurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = BrightTeal,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        stringResource(R.string.camera_permission_required),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.camera_permission_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrightTeal),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.grant_permission), color = PrimaryNavy, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Camera Preview & Scanning view
                Column(modifier = Modifier.fillMaxSize()) {
                    // Preview box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (pages.isNotEmpty() && selectedPageIndex in pages.indices) {
                            // Showing Current Page Preview / Filter editor
                            val currentPage = pages[selectedPageIndex]
                            Image(
                                bitmap = currentPage.filteredBitmap.asImageBitmap(),
                                contentDescription = "Scanned Page",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            )
                        } else {
                            // Camera Live Stream
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        try {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                CameraSelector.DEFAULT_BACK_CAMERA,
                                                preview,
                                                imageCapture
                                            )
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))
                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Document Guide Frame Overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp)
                                    .border(2.dp, BrightTeal.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            )
                        }
                    }

                    // Scanned Pages Tray & Filter Controls
                    if (pages.isNotEmpty()) {
                        Surface(
                            color = NavySurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Filter chips
                                val current = pages.getOrNull(selectedPageIndex)
                                if (current != null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ScannerManager.ScanFilter.values().forEach { filter ->
                                            val isSelected = current.filter == filter
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    scope.launch(Dispatchers.Default) {
                                                        val filtered = applyFilterLocally(current.originalBitmap, filter)
                                                        withContext(Dispatchers.Main) {
                                                            current.filter = filter
                                                            current.filteredBitmap = filtered
                                                        }
                                                    }
                                                },
                                                label = {
                                                    Text(
                                                        when (filter) {
                                                            ScannerManager.ScanFilter.ORIGINAL -> "Asli"
                                                            ScannerManager.ScanFilter.MAGIC -> "Magic Color"
                                                            ScannerManager.ScanFilter.BW -> "B&W"
                                                            ScannerManager.ScanFilter.GRAYSCALE -> "Grayscale"
                                                        }
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = BrightTeal,
                                                    selectedLabelColor = PrimaryNavy,
                                                    containerColor = PrimaryNavy,
                                                    labelColor = Color.White
                                                )
                                            )
                                        }

                                        // Rotate 90 Button
                                        IconButton(onClick = {
                                            scope.launch(Dispatchers.Default) {
                                                val matrix = Matrix().apply { postRotate(90f) }
                                                val rotatedOrig = Bitmap.createBitmap(
                                                    current.originalBitmap, 0, 0,
                                                    current.originalBitmap.width, current.originalBitmap.height,
                                                    matrix, true
                                                )
                                                val filtered = applyFilterLocally(rotatedOrig, current.filter)
                                                withContext(Dispatchers.Main) {
                                                    current.originalBitmap = rotatedOrig
                                                    current.filteredBitmap = filtered
                                                }
                                            }
                                        }) {
                                            Icon(Icons.Default.RotateRight, "Rotate", tint = Color.White)
                                        }

                                        // Delete Page Button
                                        IconButton(onClick = {
                                            pages.removeAt(selectedPageIndex)
                                            if (selectedPageIndex >= pages.size) {
                                                selectedPageIndex = (pages.size - 1).coerceAtLeast(0)
                                            }
                                        }) {
                                            Icon(Icons.Default.Delete, "Delete Page", tint = StatusError)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Pages Carousel
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pages.forEachIndexed { index, scannedPage ->
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp, 80.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(
                                                    width = if (selectedPageIndex == index) 2.dp else 1.dp,
                                                    color = if (selectedPageIndex == index) BrightTeal else Color.Gray,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { selectedPageIndex = index }
                                        ) {
                                            Image(
                                                bitmap = scannedPage.filteredBitmap.asImageBitmap(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .background(Color.Black.copy(alpha = 0.6f))
                                                    .padding(horizontal = 4.dp)
                                            ) {
                                                Text(
                                                    "${index + 1}",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Control Bar (Capture, Gallery, New Page)
                    Surface(
                        color = PrimaryNavy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Gallery Import Button
                            IconButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.PhotoLibrary,
                                    contentDescription = "Gallery",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // Capture Shutter Button
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable {
                                        val executor = ContextCompat.getMainExecutor(context)
                                        imageCapture.takePicture(
                                            executor,
                                            object : ImageCapture.OnImageCapturedCallback() {
                                                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                                    val bitmap = imageProxyToBitmap(imageProxy)
                                                    imageProxy.close()
                                                    if (bitmap != null) {
                                                        scope.launch(Dispatchers.Default) {
                                                            val filtered = applyFilterLocally(bitmap, ScannerManager.ScanFilter.MAGIC)
                                                            withContext(Dispatchers.Main) {
                                                                pages.add(
                                                                    ScannedPage(
                                                                        originalBitmap = bitmap,
                                                                        filteredBitmap = filtered,
                                                                        filter = ScannerManager.ScanFilter.MAGIC
                                                                    )
                                                                )
                                                                selectedPageIndex = pages.lastIndex
                                                            }
                                                        }
                                                    }
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    Toast.makeText(context, "Gagal mengambil gambar: ${exception.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .border(3.dp, PrimaryNavy, CircleShape)
                                        .background(BrightTeal)
                                )
                            }

                            // Mode indicator or resume camera
                            IconButton(onClick = {
                                // Add blank / switch back to live camera
                                selectedPageIndex = pages.size
                            }) {
                                Icon(
                                    Icons.Default.AddAPhoto,
                                    contentDescription = "Add Page",
                                    tint = if (selectedPageIndex >= pages.size) BrightTeal else Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (isSaving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = BrightTeal)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Membuat Dokumen PDF...", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        TextInputDialog(
            title = stringResource(R.string.scanner_save_pdf),
            initialValue = "Scan_${System.currentTimeMillis()}",
            label = "Nama Berkas PDF",
            onConfirm = { fileName ->
                showSaveDialog = false
                isSaving = true
                scope.launch(Dispatchers.IO) {
                    val scansDir = File(context.filesDir, "documents").apply { mkdirs() }
                    val safeName = if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"
                    val targetPdf = File(scansDir, safeName)

                    val bitmapsToExport = pages.map { it.filteredBitmap }
                    val success = scannerManager.exportScannedPagesToPdf(bitmapsToExport, targetPdf)
                    if (success) {
                        val docId = documentRepository.registerLocalPdf(targetPdf, safeName, isScanned = true)
                        withContext(Dispatchers.Main) {
                            isSaving = false
                            Toast.makeText(context, "Hasil pindai berhasil disimpan!", Toast.LENGTH_SHORT).show()
                            onDocumentCreated(docId)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isSaving = false
                            Toast.makeText(context, "Gagal membuat berkas PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDismiss = { showSaveDialog = false }
        )
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val planeProxy = image.planes[0]
    val buffer = planeProxy.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

    val rotation = image.imageInfo.rotationDegrees
    return if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
}

private fun applyFilterLocally(source: Bitmap, filter: ScannerManager.ScanFilter): Bitmap {
    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    val paint = android.graphics.Paint()

    when (filter) {
        ScannerManager.ScanFilter.ORIGINAL -> {
            canvas.drawBitmap(source, 0f, 0f, null)
        }
        ScannerManager.ScanFilter.GRAYSCALE -> {
            val colorMatrix = android.graphics.ColorMatrix().apply { setSaturation(0f) }
            paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(source, 0f, 0f, paint)
        }
        ScannerManager.ScanFilter.MAGIC -> {
            val contrast = 1.35f
            val brightness = -10f
            val cm = android.graphics.ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val sat = android.graphics.ColorMatrix().apply { setSaturation(1.25f) }
            cm.postConcat(sat)
            paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
            canvas.drawBitmap(source, 0f, 0f, paint)
        }
        ScannerManager.ScanFilter.BW -> {
            val pixels = IntArray(source.width * source.height)
            source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            var sumLum = 0L
            for (p in pixels) {
                sumLum += (0.299 * android.graphics.Color.red(p) + 0.587 * android.graphics.Color.green(p) + 0.114 * android.graphics.Color.blue(p)).toLong()
            }
            val threshold = (sumLum / pixels.size).toInt().coerceIn(100, 160)
            for (i in pixels.indices) {
                val p = pixels[i]
                val lum = (0.299 * android.graphics.Color.red(p) + 0.587 * android.graphics.Color.green(p) + 0.114 * android.graphics.Color.blue(p)).toInt()
                pixels[i] = if (lum > threshold) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            }
            output.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        }
    }
    return output
}
