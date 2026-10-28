package com.ankit.pdfeditor.ui.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankit.pdfeditor.model.*
import com.ankit.pdfeditor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class ToolCardItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

internal data class PageThumbnailItem(
    val originalPageIndex: Int,
    val bitmap: Bitmap,
    val rotationDelta: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolsScreen(
    onOpenPdf: (Uri) -> Unit = {},
    onRecentFilesUpdated: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("Processing...") }
    var resultDialogFile by remember { mutableStateOf<File?>(null) }
    var resultDialogImages by remember { mutableStateOf<List<File>>(emptyList()) }
    var resultDialogTitle by remember { mutableStateOf("") }
    var resultDialogMessage by remember { mutableStateOf("") }
    var resultNameText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }
    var pendingSaveImages by remember { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(resultDialogFile) {
        resultDialogFile?.let { resultNameText = it.nameWithoutExtension }
    }

    fun applyResultName(file: File): File {
        val renamed = renameLocalFile(file, resultNameText)
        resultDialogFile = renamed
        return renamed
    }

    // Tool Dialog States
    var splitPdfUri by remember { mutableStateOf<Uri?>(null) }
    var splitPageCount by remember { mutableIntStateOf(0) }
    var splitPageRangeText by remember { mutableStateOf("") }

    var reorderPdfUri by remember { mutableStateOf<Uri?>(null) }
    var reorderPages by remember { mutableStateOf<List<PageThumbnailItem>>(emptyList()) }

    var compressPdfUri by remember { mutableStateOf<Uri?>(null) }

    var protectPdfUri by remember { mutableStateOf<Uri?>(null) }
    var userPasswordText by remember { mutableStateOf("") }
    var ownerPasswordText by remember { mutableStateOf("") }

    var unlockPdfUri by remember { mutableStateOf<Uri?>(null) }
    var unlockPasswordText by remember { mutableStateOf("") }

    var pdfToImagesUri by remember { mutableStateOf<Uri?>(null) }

    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    var watermarkPdfUri by remember { mutableStateOf<Uri?>(null) }
    var watermarkType by remember { mutableStateOf(WatermarkType.PAGE_NUMBERS) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var numberPosition by remember { mutableStateOf(NumberPosition.BOTTOM_CENTER) }

    // SAF Launchers
    val mergeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.size >= 2) {
            scope.launch {
                isProcessing = true
                processingMessage = "Merging ${uris.size} PDFs..."
                val result = mergePdfs(context, uris)
                isProcessing = false
                if (result != null) {
                    addRecentFile(context, result)
                    onRecentFilesUpdated()
                    resultDialogFile = result
                    resultDialogTitle = "PDFs Merged Successfully"
                    resultDialogMessage = "Created merged document with ${uris.size} files."
                } else {
                    errorMessage = "Failed to merge selected PDFs."
                }
            }
        } else if (uris.isNotEmpty()) {
            errorMessage = "Please select at least 2 PDF files to merge."
        }
    }

    val splitLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            splitPdfUri = uri
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            splitPageCount = renderer.pageCount
                            splitPageRangeText = "1-$splitPageCount"
                        }
                    }
                }
            }
        }
    }

    val reorderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            reorderPdfUri = uri
            scope.launch(Dispatchers.IO) {
                isProcessing = true
                processingMessage = "Loading page thumbnails..."
                val thumbnails = loadPageThumbnails(context, uri)
                reorderPages = thumbnails
                isProcessing = false
            }
        }
    }

    val compressLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            compressPdfUri = uri
        }
    }

    val protectLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            protectPdfUri = uri
            userPasswordText = ""
            ownerPasswordText = ""
        }
    }

    val unlockLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            unlockPdfUri = uri
            unlockPasswordText = ""
        }
    }

    val pdfToImagesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pdfToImagesUri = uri
        }
    }

    val imagesToPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            selectedImageUris = uris
        }
    }

    val watermarkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            watermarkPdfUri = uri
        }
    }

    val ocrLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isProcessing = true
                processingMessage = "Performing OCR & creating searchable PDF..."
                val result = generateSearchablePdfFromImages(context, uris) { current, total ->
                    processingMessage = "Performing OCR on page $current of $total..."
                }
                isProcessing = false
                if (result != null) {
                    addRecentFile(context, result)
                    onRecentFilesUpdated()
                    resultDialogFile = result
                    resultDialogTitle = "Searchable PDF Created"
                    resultDialogMessage = "Processed ${uris.size} image(s) with ML Kit OCR."
                } else {
                    errorMessage = "Failed to create searchable PDF from images."
                }
            }

        }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val file = pendingSaveFile
        if (uri != null && file != null) {
            scope.launch {
                val ok = copyFileToUri(context, file, uri)
                Toast.makeText(
                    context,
                    if (ok) "PDF saved" else "Could not save PDF",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val saveImagesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        if (tree != null && pendingSaveImages.isNotEmpty()) {
            scope.launch {
                val count = copyFilesToFolder(context, pendingSaveImages, tree)
                Toast.makeText(context, "Saved $count image(s)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val toolCards = remember {
        listOf(
            ToolCardItem("Merge PDFs", "Combine multiple PDF files into one", Icons.Default.CallMerge, Color(0xFF4F46E5)) {
                mergeLauncher.launch("application/pdf")
            },
            ToolCardItem("Split Pages", "Extract specific pages or page ranges", Icons.Default.CallSplit, Color(0xFF0284C7)) {
                splitLauncher.launch("application/pdf")
            },
            ToolCardItem("Reorder & Rotate", "Rotate, delete, or rearrange pages", Icons.Default.GridView, Color(0xFFD97706)) {
                reorderLauncher.launch("application/pdf")
            },
            ToolCardItem("OCR / Searchable PDF", "Run OCR to make images into a searchable PDF", Icons.Default.Scanner, Color(0xFF059669)) {
                ocrLauncher.launch("image/*")
            },
            ToolCardItem("Compress PDF", "Reduce PDF file size", Icons.Default.Compress, Color(0xFF0284C7)) {
                compressLauncher.launch("application/pdf")
            },
            ToolCardItem("Protect PDF", "Encrypt PDF with a password", Icons.Default.Lock, Color(0xFFDC2626)) {
                protectLauncher.launch("application/pdf")
            },
            ToolCardItem("Unlock PDF", "Remove password protection from PDF", Icons.Default.LockOpen, Color(0xFF7C3AED)) {
                unlockLauncher.launch("application/pdf")
            },
            ToolCardItem("PDF to Images", "Export PDF pages as PNG image files", Icons.Default.Image, Color(0xFF2563EB)) {
                pdfToImagesLauncher.launch("application/pdf")
            },
            ToolCardItem("Images to PDF", "Convert multiple images into a PDF", Icons.Default.PictureAsPdf, Color(0xFF0891B2)) {
                imagesToPdfLauncher.launch("image/*")
            },
            ToolCardItem("Watermark & Numbers", "Add page numbers or watermark text", Icons.Default.FormatListNumbered, Color(0xFF9333EA)) {
                watermarkLauncher.launch("application/pdf")
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PDF Tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(toolCards) { tool ->
                Card(
                    onClick = tool.onClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = tool.color.copy(alpha = 0.12f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(tool.icon, contentDescription = null, tint = tool.color, modifier = Modifier.size(24.dp))
                            }
                        }
                        Column {
                            Text(tool.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                tool.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    // Processing Dialog
    if (isProcessing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Processing") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Text(processingMessage)
                }
            },
            confirmButton = {}
        )
    }

    // Error Dialog
    errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("OK") }
            }
        )
    }

    // Result Dialog
    if (resultDialogFile != null || resultDialogImages.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {
                resultDialogFile = null
                resultDialogImages = emptyList()
            },
            title = { Text(resultDialogTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(resultDialogMessage)
                    if (resultDialogFile != null) {
                        OutlinedTextField(
                            value = resultNameText,
                            onValueChange = { resultNameText = it },
                            singleLine = true,
                            label = { Text("File name") },
                            suffix = { Text(".pdf") }
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    resultDialogFile?.let { file ->
                        TextButton(onClick = {
                            val renamed = applyResultName(file)
                            val uri = fileProviderUri(context, renamed)
                            resultDialogFile = null
                            onOpenPdf(uri)
                        }) { Text("Open PDF") }

                        TextButton(onClick = {
                            val renamed = applyResultName(file)
                            pendingSaveFile = renamed
                            savePdfLauncher.launch(renamed.name)
                        }) { Text("Save PDF") }
                    }
                    if (resultDialogImages.isNotEmpty()) {
                        TextButton(onClick = {
                            pendingSaveImages = resultDialogImages
                            saveImagesLauncher.launch(null)
                        }) { Text("Save Images") }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    resultDialogFile = null
                    resultDialogImages = emptyList()
                }) { Text("Done") }
            }
        )
    }

    // Split PDF Dialog
    splitPdfUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { splitPdfUri = null },
            title = { Text("Split / Extract Pages") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Total pages in PDF: $splitPageCount")
                    Text("Enter page numbers or ranges to keep (e.g. '1-3, 5, 8'):")
                    OutlinedTextField(
                        value = splitPageRangeText,
                        onValueChange = { splitPageRangeText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("1-$splitPageCount") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pagesToKeep = parsePageRange(splitPageRangeText, splitPageCount)
                    splitPdfUri = null
                    if (pagesToKeep.isNotEmpty()) {
                        scope.launch {
                            isProcessing = true
                            processingMessage = "Extracting ${pagesToKeep.size} pages..."
                            val result = splitPdf(context, uri, pagesToKeep)
                            isProcessing = false
                            if (result != null) {
                                addRecentFile(context, result)
                                onRecentFilesUpdated()
                                resultDialogFile = result
                                resultDialogTitle = "PDF Split Successfully"
                                resultDialogMessage = "Extracted ${pagesToKeep.size} pages into a new document."
                            } else {
                                errorMessage = "Failed to split PDF document."
                            }
                        }
                    } else {
                        errorMessage = "Invalid page range specified."
                    }
                }) { Text("Extract") }
            },
            dismissButton = {
                TextButton(onClick = { splitPdfUri = null }) { Text("Cancel") }
            }
        )
    }

    // Reorder / Rotate Pages Dialog
    reorderPdfUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { reorderPdfUri = null },
            title = { Text("Reorder / Rotate / Delete Pages") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                ) {
                    Text("Total pages: ${reorderPages.size}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        itemsIndexed(reorderPages) { index, item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = item.bitmap.asImageBitmap(),
                                            contentDescription = "Page ${index + 1}",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Text("Page ${item.originalPageIndex + 1} (${item.rotationDelta}°)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        IconButton(
                                            onClick = {
                                                reorderPages = reorderPages.toMutableList().apply {
                                                    this[index] = item.copy(rotationDelta = (item.rotationDelta + 90) % 360)
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                if (index > 0) {
                                                    reorderPages = reorderPages.toMutableList().apply {
                                                        val temp = this[index]
                                                        this[index] = this[index - 1]
                                                        this[index - 1] = temp
                                                    }
                                                }
                                            },
                                            enabled = index > 0,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move Left", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                if (index < reorderPages.size - 1) {
                                                    reorderPages = reorderPages.toMutableList().apply {
                                                        val temp = this[index]
                                                        this[index] = this[index + 1]
                                                        this[index + 1] = temp
                                                    }
                                                }
                                            },
                                            enabled = index < reorderPages.size - 1,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move Right", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                reorderPages = reorderPages.toMutableList().apply { removeAt(index) }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pageSpecs = reorderPages.map { PageSpec(it.originalPageIndex, it.rotationDelta) }
                        reorderPdfUri = null
                        if (pageSpecs.isNotEmpty()) {
                            scope.launch {
                                isProcessing = true
                                processingMessage = "Saving page modifications..."
                                val result = reorderRotateDeletePdf(context, uri, pageSpecs)
                                isProcessing = false
                                if (result != null) {
                                    addRecentFile(context, result)
                                    onRecentFilesUpdated()
                                    resultDialogFile = result
                                    resultDialogTitle = "PDF Saved Successfully"
                                    resultDialogMessage = "Rearranged, rotated, or removed pages."
                                } else {
                                    errorMessage = "Failed to save modified PDF."
                                }
                            }
                        }
                    },
                    enabled = reorderPages.isNotEmpty()
                ) { Text("Save PDF") }
            },
            dismissButton = {
                TextButton(onClick = { reorderPdfUri = null }) { Text("Cancel") }
            }
        )
    }

    // Compress PDF Dialog
    compressPdfUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { compressPdfUri = null },
            title = { Text("Compress PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select compression quality level:")
                    CompressQuality.entries.forEach { quality ->
                        Card(
                            onClick = {
                                compressPdfUri = null
                                scope.launch {
                                    isProcessing = true
                                    processingMessage = "Compressing PDF (${quality.label})..."
                                    val result = compressPdf(context, uri, quality.qualityRatio, quality.scaleFactor)
                                    isProcessing = false
                                    if (result != null) {
                                        addRecentFile(context, result)
                                        onRecentFilesUpdated()
                                        val newSize = formatFileSize(result.length())
                                        resultDialogFile = result
                                        resultDialogTitle = "PDF Compressed"
                                        resultDialogMessage = "New file size: $newSize"
                                    } else {
                                        errorMessage = "Failed to compress PDF."
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(
                                quality.label,
                                modifier = Modifier.padding(12.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { compressPdfUri = null }) { Text("Cancel") }
            }
        )
    }

    // Protect PDF Dialog
    protectPdfUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { protectPdfUri = null },
            title = { Text("Protect PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set password to lock the document:")
                    OutlinedTextField(
                        value = userPasswordText,
                        onValueChange = { userPasswordText = it },
                        label = { Text("User Password (required)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ownerPasswordText,
                        onValueChange = { ownerPasswordText = it },
                        label = { Text("Owner Password (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pass = userPasswordText.trim()
                        protectPdfUri = null
                        if (pass.isNotEmpty()) {
                            scope.launch {
                                isProcessing = true
                                processingMessage = "Encrypting PDF document..."
                                val result = protectPdf(context, uri, pass, ownerPasswordText.trim().ifBlank { pass })
                                isProcessing = false
                                if (result != null) {
                                    addRecentFile(context, result)
                                    onRecentFilesUpdated()
                                    resultDialogFile = result
                                    resultDialogTitle = "PDF Protected"
                                    resultDialogMessage = "Document encrypted with password."
                                } else {
                                    errorMessage = "Failed to protect PDF document."
                                }
                            }
                        } else {
                            errorMessage = "Password cannot be empty."
                        }
                    },
                    enabled = userPasswordText.trim().isNotEmpty()
                ) { Text("Protect") }
            },
            dismissButton = {
                TextButton(onClick = { protectPdfUri = null }) { Text("Cancel") }
            }
        )
    }

    // Unlock PDF Dialog
    unlockPdfUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { unlockPdfUri = null },
            title = { Text("Unlock PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter current password to remove protection:")
                    OutlinedTextField(
                        value = unlockPasswordText,
                        onValueChange = { unlockPasswordText = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pass = unlockPasswordText.trim()
                        unlockPdfUri = null
                        if (pass.isNotEmpty()) {
                            scope.launch {
                                isProcessing = true
                                processingMessage = "Unlocking PDF document..."
                                val result = unlockPdf(context, uri, pass)
                                isProcessing = false
                                if (result != null) {
                                    addRecentFile(context, result)
                                    onRecentFilesUpdated()
                                    resultDialogFile = result
                                    resultDialogTitle = "PDF Unlocked"
                                    resultDialogMessage = "Security protection removed from PDF."
                                } else {
                                    errorMessage = "Incorrect password or unable to unlock PDF."
                                }
                            }
                        }
                    },
                    enabled = unlockPasswordText.trim().isNotEmpty()
                ) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(onClick = { unlockPdfUri = null }) { Text("Cancel") }
            }
        )
    }

    // PDF to Images Dialog
    pdfToImagesUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pdfToImagesUri = null },
            title = { Text("Export PDF to Images") },
            text = { Text("Export all pages as PNG images?") },
            confirmButton = {
                TextButton(onClick = {
                    pdfToImagesUri = null
                    scope.launch {
                        isProcessing = true
                        processingMessage = "Rendering PDF pages as images..."
                        val images = convertPdfToImages(context, uri, Bitmap.CompressFormat.PNG)
                        isProcessing = false
                        if (images.isNotEmpty()) {
                            resultDialogImages = images
                            resultDialogTitle = "Export Complete"
                            resultDialogMessage = "Converted ${images.size} pages to PNG images."
                        } else {
                            errorMessage = "Failed to export PDF pages as images."
                        }
                    }
                }) { Text("Export PNG") }
            },
            dismissButton = {
                TextButton(onClick = { pdfToImagesUri = null }) { Text("Cancel") }
            }
        )
    }

    // Multi-Image to PDF Dialog
    if (selectedImageUris.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { selectedImageUris = emptyList() },
            title = { Text("Images to PDF (${selectedImageUris.size} selected)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Convert selected ${selectedImageUris.size} images to a multi-page PDF document?")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uris = selectedImageUris
                    selectedImageUris = emptyList()
                    scope.launch {
                        isProcessing = true
                        processingMessage = "Creating PDF from images..."
                        val result = convertImagesToPdfOnDevice(context, uris)
                        isProcessing = false
                        if (result != null) {
                            addRecentFile(context, result)
                            onRecentFilesUpdated()
                            resultDialogFile = result
                            resultDialogTitle = "PDF Created"
                            resultDialogMessage = "Converted ${uris.size} images into a single PDF."
                        } else {
                            errorMessage = "Failed to create PDF from images."
                        }
                    }
                }) { Text("Convert to PDF") }
            },
            dismissButton = {
                TextButton(onClick = { selectedImageUris = emptyList() }) { Text("Cancel") }
            }
        )
    }

    // Watermark / Page Numbers Dialog
    watermarkPdfUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { watermarkPdfUri = null },
            title = { Text("Add Page Numbers / Watermark") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = watermarkType == WatermarkType.PAGE_NUMBERS,
                            onClick = { watermarkType = WatermarkType.PAGE_NUMBERS },
                            label = { Text("Page Numbers") }
                        )
                        FilterChip(
                            selected = watermarkType == WatermarkType.WATERMARK_TEXT,
                            onClick = { watermarkType = WatermarkType.WATERMARK_TEXT },
                            label = { Text("Watermark Text") }
                        )
                    }

                    if (watermarkType == WatermarkType.WATERMARK_TEXT) {
                        OutlinedTextField(
                            value = watermarkText,
                            onValueChange = { watermarkText = it },
                            label = { Text("Watermark Text") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        Text("Position:", fontWeight = FontWeight.SemiBold)
                        Column {
                            NumberPosition.entries.forEach { pos ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { numberPosition = pos }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = numberPosition == pos,
                                        onClick = { numberPosition = pos }
                                    )
                                    Text(
                                        when (pos) {
                                            NumberPosition.BOTTOM_CENTER -> "Bottom Center"
                                            NumberPosition.BOTTOM_RIGHT -> "Bottom Right"
                                            NumberPosition.TOP_RIGHT -> "Top Right"
                                            NumberPosition.TOP_CENTER -> "Top Center"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    watermarkPdfUri = null
                    val config = WatermarkConfig(
                        type = watermarkType,
                        text = watermarkText.trim().ifBlank { "CONFIDENTIAL" },
                        position = numberPosition,
                        opacity = if (watermarkType == WatermarkType.WATERMARK_TEXT) 0.3f else 0.8f
                    )
                    scope.launch {
                        isProcessing = true
                        processingMessage = "Applying ${if (watermarkType == WatermarkType.WATERMARK_TEXT) "watermark" else "page numbers"}..."
                        val result = addWatermarkOrPageNumbers(context, uri, config)
                        isProcessing = false
                        if (result != null) {
                            addRecentFile(context, result)
                            onRecentFilesUpdated()
                            resultDialogFile = result
                            resultDialogTitle = "Operation Complete"
                            resultDialogMessage = "Saved document with ${if (watermarkType == WatermarkType.WATERMARK_TEXT) "watermark" else "page numbers"}."
                        } else {
                            errorMessage = "Failed to add watermark or page numbers."
                        }
                    }
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { watermarkPdfUri = null }) { Text("Cancel") }
            }
        )
    }
}

private suspend fun loadPageThumbnails(context: Context, uri: Uri): List<PageThumbnailItem> = withContext(Dispatchers.IO) {
    val list = mutableListOf<PageThumbnailItem>()
    runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (pageIdx in 0 until renderer.pageCount) {
                    renderer.openPage(pageIdx).use { page ->
                        val scale = minOf(1f, 300f / maxOf(page.width, page.height).toFloat())
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        list.add(PageThumbnailItem(pageIdx, bitmap))
                    }
                }
            }
        }
    }
    list
}

internal fun parsePageRange(text: String, pageCount: Int): List<Int> {
    if (pageCount <= 0) return emptyList()
    val pages = mutableSetOf<Int>()
    val parts = text.split(',')
    for (part in parts) {
        val trimmed = part.trim()
        if (trimmed.contains('-')) {
            val bounds = trimmed.split('-')
            if (bounds.size == 2) {
                val start = bounds[0].trim().toIntOrNull()
                val end = bounds[1].trim().toIntOrNull()
                if (start != null && end != null) {
                    val minP = minOf(start, end).coerceIn(1, pageCount)
                    val maxP = maxOf(start, end).coerceIn(1, pageCount)
                    for (p in minP..maxP) pages.add(p - 1)
                }
            }
        } else {
            val num = trimmed.toIntOrNull()
            if (num != null && num in 1..pageCount) {
                pages.add(num - 1)
            }
        }
    }
    return pages.toList().sorted()
}

private fun addRecentFile(context: Context, file: File) {
    val uriStr = fileProviderUri(context, file).toString()
    val item = RecentFileItem(
        uri = uriStr,
        fileName = file.name,
        openedAt = dateString(),
        fileSizeBytes = file.length()
    )
    val list = loadRecentFiles(context).filterNot { it.uri == uriStr }
    persistRecentFiles(context, listOf(item) + list)
}
