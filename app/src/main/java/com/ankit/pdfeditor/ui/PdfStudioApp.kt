package com.ankit.pdfeditor.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ankit.pdfeditor.model.*
import com.ankit.pdfeditor.ui.editor.EditorScreen
import com.ankit.pdfeditor.ui.files.FilesScreen
import com.ankit.pdfeditor.ui.home.HomeScreen
import com.ankit.pdfeditor.ui.nav.BottomNavBar
import com.ankit.pdfeditor.ui.settings.SettingsScreen
import com.ankit.pdfeditor.ui.theme.PDFEditorAppTheme
import com.ankit.pdfeditor.ui.tools.ToolsScreen
import com.ankit.pdfeditor.util.*
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.math.hypot
import kotlin.time.Duration.Companion.milliseconds

private suspend fun writeAnnotatedPdf(
    context: Context,
    sourceUri: Uri,
    strokesByPage: Map<Int, List<PdfStroke>>,
    textsByPage: Map<Int, List<PdfText>>,
    shapesByPage: Map<Int, List<PdfShape>>,
    signaturesByPage: Map<Int, List<PdfSignature>>,
    outputStream: OutputStream
): Int = withContext(Dispatchers.IO) {
    var skippedAnnotations = 0
    val input = context.contentResolver.openInputStream(sourceUri)
        ?: throw IOException("Unable to read the source PDF")
    input.use { source ->
        PDDocument.load(source, MemoryUsageSetting.setupTempFileOnly()).use { document ->
            val latinFont = context.assets.open("NotoSans-Regular.ttf").use {
                PDType0Font.load(document, it, true)
            }
            val devanagariFont = context.assets.open("NotoSansDevanagari-Regular.ttf").use {
                PDType0Font.load(document, it, true)
            }
            document.pages.forEachIndexed { pageIndex, page ->
                val crop = page.cropBox ?: page.mediaBox
                val geometry = PdfPageGeometry(
                    width = crop.width,
                    height = crop.height,
                    cropLeft = crop.lowerLeftX,
                    cropBottom = crop.lowerLeftY,
                    rotation = page.rotation
                )
                val pageMin = displayedMinDimension(geometry)
                PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { content ->
                    strokesByPage[pageIndex].orEmpty().forEach { stroke ->
                        try {
                            if (stroke.points.isNotEmpty()) {
                                val color = stroke.color.toAndroidColor()
                                val graphicsState = PDExtendedGraphicsState().apply {
                                    strokingAlphaConstant = stroke.color.alpha
                                    if (stroke.color.alpha < 1f) blendMode = BlendMode.MULTIPLY
                                }
                                content.saveGraphicsState()
                                try {
                                    content.setGraphicsStateParameters(graphicsState)
                                    content.setStrokingColor(
                                        android.graphics.Color.red(color),
                                        android.graphics.Color.green(color),
                                        android.graphics.Color.blue(color)
                                    )
                                    content.setLineCapStyle(1)
                                    content.setLineJoinStyle(1)
                                    content.setLineWidth(stroke.widthFraction * pageMin)
                                    val first = mapNormalizedPointToPdf(stroke.points.first(), geometry)
                                    content.moveTo(first.x, first.y)
                                    stroke.points.drop(1).forEach { point ->
                                        val mapped = mapNormalizedPointToPdf(point, geometry)
                                        content.lineTo(mapped.x, mapped.y)
                                    }
                                    content.stroke()
                                } finally {
                                    content.restoreGraphicsState()
                                }
                            }
                        } catch (_: Exception) {
                            skippedAnnotations++
                        }
                    }

                    shapesByPage[pageIndex].orEmpty().forEach { shape ->
                        try {
                            val color = shape.color.toAndroidColor()
                            val startMapped = mapNormalizedPointToPdf(shape.start, geometry)
                            val endMapped = mapNormalizedPointToPdf(shape.end, geometry)
                            content.saveGraphicsState()
                            try {
                                content.setStrokingColor(
                                    android.graphics.Color.red(color),
                                    android.graphics.Color.green(color),
                                    android.graphics.Color.blue(color)
                                )
                                content.setLineWidth(shape.widthFraction * pageMin)
                                when (shape.type) {
                                    ShapeType.RECTANGLE -> {
                                        val left = minOf(startMapped.x, endMapped.x)
                                        val bottom = minOf(startMapped.y, endMapped.y)
                                        val w = kotlin.math.abs(endMapped.x - startMapped.x)
                                        val h = kotlin.math.abs(endMapped.y - startMapped.y)
                                        content.addRect(left, bottom, w, h)
                                        content.stroke()
                                    }
                                    else -> {
                                        content.moveTo(startMapped.x, startMapped.y)
                                        content.lineTo(endMapped.x, endMapped.y)
                                        content.stroke()
                                    }
                                }
                            } finally {
                                content.restoreGraphicsState()
                            }
                        } catch (_: Exception) {
                            skippedAnnotations++
                        }
                    }

                    signaturesByPage[pageIndex].orEmpty().forEach { sig ->
                        try {
                            val color = sig.color.toAndroidColor()
                            val originMapped = mapNormalizedPointToPdf(sig.position, geometry)
                            val sigWidthPdf = sig.size.x * geometry.width
                            val sigHeightPdf = sig.size.y * geometry.height
                            content.saveGraphicsState()
                            try {
                                content.setStrokingColor(
                                    android.graphics.Color.red(color),
                                    android.graphics.Color.green(color),
                                    android.graphics.Color.blue(color)
                                )
                                content.setLineWidth(2f)
                                sig.strokes.forEach { strokePts ->
                                    if (strokePts.isNotEmpty()) {
                                        val p0 = strokePts.first()
                                        content.moveTo(originMapped.x + p0.x * sigWidthPdf, originMapped.y - p0.y * sigHeightPdf)
                                        strokePts.drop(1).forEach { pt ->
                                            content.lineTo(originMapped.x + pt.x * sigWidthPdf, originMapped.y - pt.y * sigHeightPdf)
                                        }
                                        content.stroke()
                                    }
                                }
                            } finally {
                                content.restoreGraphicsState()
                            }
                        } catch (_: Exception) {
                            skippedAnnotations++
                        }
                    }

                    textsByPage[pageIndex].orEmpty().forEach { text ->
                        try {
                            val origin = mapNormalizedPointToPdf(text.position, geometry)
                            val fontSize = text.sizeFraction * pageMin
                            val color = text.color.toAndroidColor()

                            val lines = text.text.split('\n')
                            val lineRuns = lines.map { line ->
                                splitLineToExportRuns(line, latinFont, devanagariFont)
                            }

                            lineRuns.forEach { runs ->
                                runs.forEach { run ->
                                    run.font.encode(run.text)
                                }
                            }

                            content.saveGraphicsState()
                            try {
                                val graphicsState = PDExtendedGraphicsState().apply {
                                    nonStrokingAlphaConstant = text.color.alpha
                                }
                                content.setGraphicsStateParameters(graphicsState)
                                content.setNonStrokingColor(
                                    android.graphics.Color.red(color),
                                    android.graphics.Color.green(color),
                                    android.graphics.Color.blue(color)
                                )

                                val radians = Math.toRadians((geometry.rotation % 360).toDouble())

                                lineRuns.forEachIndexed { lineIndex, runs ->
                                    content.beginText()
                                    try {
                                        val lineOffsetY = -lineIndex * fontSize * 1.2f
                                        val dx = (lineOffsetY * -kotlin.math.sin(radians)).toFloat()
                                        val dy = (lineOffsetY * kotlin.math.cos(radians)).toFloat()
                                        val textMatrix = Matrix.getRotateInstance(radians, origin.x + dx, origin.y + dy)
                                        content.setTextMatrix(textMatrix)

                                        runs.forEach { run ->
                                            content.setFont(run.font, fontSize)
                                            content.showText(run.text)
                                        }
                                    } finally {
                                        content.endText()
                                    }
                                }
                            } finally {
                                content.restoreGraphicsState()
                            }
                        } catch (_: Exception) {
                            skippedAnnotations++
                        }
                    }
                }
            }
            document.save(outputStream)
        }
    }
    skippedAnnotations
}

private data class ExportTextRun(val text: String, val font: PDType0Font)

private fun splitLineToExportRuns(
    line: String,
    latinFont: PDType0Font,
    devanagariFont: PDType0Font
): List<ExportTextRun> {
    if (line.isEmpty()) return emptyList()
    val runs = mutableListOf<ExportTextRun>()
    var currentFont: PDType0Font? = null
    val currentSb = StringBuilder()

    var i = 0
    while (i < line.length) {
        val codePoint = line.codePointAt(i)
        val charCount = Character.charCount(codePoint)
        val cpString = line.substring(i, i + charCount)

        val font = if (canEncode(latinFont, cpString)) {
            latinFont
        } else if (canEncode(devanagariFont, cpString)) {
            devanagariFont
        } else {
            latinFont
        }

        if (currentFont == null) {
            currentFont = font
            currentSb.append(cpString)
        } else if (font == currentFont) {
            currentSb.append(cpString)
        } else {
            runs.add(ExportTextRun(currentSb.toString(), currentFont))
            currentFont = font
            currentSb.clear()
            currentSb.append(cpString)
        }

        i += charCount
    }
    if (currentFont != null && currentSb.isNotEmpty()) {
        runs.add(ExportTextRun(currentSb.toString(), currentFont))
    }
    return runs
}

private fun canEncode(font: PDType0Font, text: String): Boolean {
    return try {
        font.encode(text)
        true
    } catch (_: Exception) {
        false
    }
}

@Composable
internal fun PdfStudioApp(initialUri: Uri? = null) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val studioViewModel: StudioViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val rendererMutex = remember { Mutex() }
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var themeMode by remember {
        mutableStateOf(
            runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
        )
    }
    var dynamicColor by remember { mutableStateOf(prefs.getBoolean("dynamic_color", false)) }
    var appLockEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock_enabled", false)) }
    var pageColorFilter by remember {
        mutableStateOf(
            runCatching { PageColorFilter.valueOf(prefs.getString("page_color_filter", "NORMAL") ?: "NORMAL") }.getOrDefault(PageColorFilter.NORMAL)
        )
    }

    var isUnlocked by remember { mutableStateOf(!appLockEnabled) }

    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showSaveOverwriteDialog by remember { mutableStateOf(false) }
    var passwordPromptUri by remember { mutableStateOf<Uri?>(null) }
    var passwordPromptText by remember { mutableStateOf("") }

    val recentFilesState = remember { mutableStateOf(loadRecentFiles(context)) }
    var recentFiles by recentFilesState

    suspend fun addRecent(uri: Uri, fileName: String) {
        val resolvedSize = withContext(Dispatchers.IO) {
            resolveFileSize(context, uri)
        }

        val updated = RecentFileItem(
            uri = uri.toString(),
            fileName = fileName,
            openedAt = dateString(),
            fileSizeBytes = resolvedSize,
            timestamp = System.currentTimeMillis(),
            isFavorite = recentFiles.firstOrNull { it.uri == uri.toString() }?.isFavorite == true,
            lastPage = studioViewModel.currentPage
        )

        recentFiles = (listOf(updated) + recentFiles.filterNot { it.uri == updated.uri })
            .sortedByDescending { it.timestamp }
            .take(MAX_RECENT_FILES)

        persistRecentFiles(context, recentFiles)
    }

    suspend fun loadPdf(uri: Uri, fileNameOverride: String? = null) {
        val newRenderer = withContext(Dispatchers.IO) {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IOException("Unable to open the selected PDF")

            try {
                PdfRenderer(descriptor)
            } catch (e: Exception) {
                try {
                    descriptor.close()
                } catch (_: Exception) {
                }
                throw e
            }
        }

        var installed = false
        try {
            val newPageCount = withContext(Dispatchers.IO) {
                newRenderer.pageCount
            }

            rendererMutex.withLock {
                val oldRenderer = renderer
                renderer = null
                pageCount = 0

                withContext(Dispatchers.IO) {
                    try {
                        oldRenderer?.close()
                    } catch (_: IllegalStateException) {
                    }
                }
            }

            renderer = newRenderer
            pageCount = newPageCount
            val previousUri = studioViewModel.pdfUri
            val sameDocument = previousUri == uri.toString()
            studioViewModel.pdfUri = uri.toString()
            studioViewModel.pdfUri?.let { _ -> }
            if (!sameDocument) {
                studioViewModel.clearAnnotations(previousUri, deletePersisted = false)
                val savedItem = recentFiles.firstOrNull { it.uri == uri.toString() }
                studioViewModel.currentPage = savedItem?.lastPage?.coerceIn(0, (newPageCount - 1).coerceAtLeast(0)) ?: 0
                studioViewModel.zoomLevel = 1f
                studioViewModel.editorMode = EditorMode.VIEW
                studioViewModel.hasUnsavedChanges = false
            }
            studioViewModel.restoreAnnotations(uri.toString())
            installed = true

            addRecent(
                uri,
                fileNameOverride ?: withContext(Dispatchers.IO) {
                    resolveFileName(context, uri)
                }
            )

            studioViewModel.currentScreen = AppScreen.EDITOR
        } finally {
            if (!installed) {
                withContext(Dispatchers.IO) {
                    try {
                        newRenderer.close()
                    } catch (_: IllegalStateException) {
                    }
                }
            }
        }
    }

    fun openPdf(uri: Uri, fileNameOverride: String? = null) {
        scope.launch {
            isBusy = true
            errorMessage = null
            try {
                loadPdf(uri, fileNameOverride)
            } catch (e: Exception) {
                if (e is SecurityException || e.message?.contains("encrypted", ignoreCase = true) == true || e.message?.contains("password", ignoreCase = true) == true) {
                    passwordPromptUri = uri
                    passwordPromptText = ""
                } else {
                    errorMessage = e.message ?: "Could not open PDF"
                }
            } finally {
                isBusy = false
            }
        }
    }

    LaunchedEffect(appLockEnabled) {
        if (appLockEnabled && !isUnlocked && activity != null) {
            showBiometricPrompt(
                activity = activity,
                onSuccess = { isUnlocked = true },
                onError = { /* handle error */ }
            )
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null && isReadableUri(context, initialUri)) {
            openPdf(initialUri)
        }
    }

    val currentScreen = studioViewModel.currentScreen
    val currentPage = studioViewModel.currentPage
    val zoomLevel = studioViewModel.zoomLevel
    val hasUnsavedChanges = studioViewModel.hasUnsavedChanges

    val editorMode = studioViewModel.editorMode
    val currentColor = studioViewModel.currentColor
    val strokeWidth = studioViewModel.strokeWidth

    LaunchedEffect(Unit) {
        val valid = withContext(Dispatchers.IO) {
            cleanupGeneratedFiles(context, recentFiles)
        }
        if (valid.size != recentFiles.size) {
            recentFiles = valid
            persistRecentFiles(context, valid)
        }
    }
    val pageStrokes = studioViewModel.pageStrokes
    val pageTexts = studioViewModel.pageTexts
    val pageShapes = studioViewModel.pageShapes
    val pageSignatures = studioViewModel.pageSignatures

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            openPdf(uri)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isBusy = true
                val converted = convertImageToPdfOnDevice(context, uri)
                isBusy = false
                if (converted != null) {
                    openPdf(fileProviderUri(context, converted))
                } else {
                    errorMessage = "Could not convert image to PDF"
                }
            }
        }
    }

    val documentScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = parseScannerResult(result.data)
            if (scanResult != null) {
                val pdf = scanResult.pdf
                if (pdf != null) {
                    openPdf(pdf.uri)
                } else {
                    val pages = scanResult.pages
                    val pageUris = pages?.mapNotNull { it.imageUri } ?: emptyList()
                    if (pageUris.isNotEmpty()) {
                        scope.launch {
                            isBusy = true
                            val ocrPdf = generateSearchablePdfFromImages(context, pageUris)
                            isBusy = false
                            if (ocrPdf != null) {
                                openPdf(fileProviderUri(context, ocrPdf))
                            } else {
                                errorMessage = "Failed to generate searchable PDF from scanned pages."
                            }
                        }
                    }
                }
            }
        }
    }

    fun launchDocumentScanner() {
        val activity = context as? ComponentActivity ?: return
        val client = getDocumentScannerClient(activity)
        client.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                documentScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                errorMessage = e.message ?: "Failed to start document scanner."
            }
    }

    fun onShareRecent(item: RecentFileItem) {
        runCatching {
            sharePdf(context, Uri.parse(item.uri))
        }.onFailure { e ->
            errorMessage = e.message ?: "Could not share PDF"
        }
    }

    fun onToggleFavorite(item: RecentFileItem) {
        recentFiles = recentFiles.map {
            if (it.uri == item.uri) it.copy(isFavorite = !it.isFavorite) else it
        }
        persistRecentFiles(context, recentFiles)
    }

    fun onRemoveRecent(item: RecentFileItem) {
        recentFiles = recentFiles.filterNot { it.uri == item.uri }
        persistRecentFiles(context, recentFiles)
        if (studioViewModel.pdfUri == item.uri) {
            studioViewModel.clearAnnotations(item.uri, deletePersisted = true)
        }
    }

    fun onFileTap(item: RecentFileItem) {
        val uri = Uri.parse(item.uri)
        if (isReadableUri(context, uri)) {
            openPdf(uri, item.fileName)
        } else {
            onRemoveRecent(item)
            errorMessage = "File no longer available"
        }
    }

    fun saveEditedPdf(overwrite: Boolean) {
        val currentUri = studioViewModel.pdfUri?.let { Uri.parse(it) } ?: return
        scope.launch {
            isBusy = true
            errorMessage = null
            try {
                val documentFile = if (overwrite && currentUri.scheme == "file") {
                    File(currentUri.path.orEmpty())
                } else {
                    File(
                        appDocumentsDir(context),
                        "Annotated_${safeFileName(resolveFileName(context, currentUri))}"
                    )
                }

                val skipped = withContext(Dispatchers.IO) {
                    documentFile.outputStream().use { stream ->
                        writeAnnotatedPdf(
                            context = context,
                            sourceUri = currentUri,
                            strokesByPage = pageStrokes,
                            textsByPage = pageTexts,
                            shapesByPage = pageShapes,
                            signaturesByPage = pageSignatures,
                            outputStream = stream
                        )
                    }
                }

                val savedUri = fileProviderUri(context, documentFile)
                loadPdf(savedUri, documentFile.name)
                studioViewModel.clearAnnotations(currentUri.toString(), deletePersisted = true)
                if (skipped > 0) {
                    errorMessage = "$skipped annotation(s) could not be exported."
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not save PDF"
            } finally {
                isBusy = false
            }
        }
    }

    fun confirmBackNavigation() {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            studioViewModel.currentScreen = AppScreen.HOME
        }
    }

    BackHandler(enabled = currentScreen == AppScreen.EDITOR) {
        confirmBackNavigation()
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Unsaved changes") },
            text = { Text("You have unsaved annotations. Save your changes before leaving?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    showSaveOverwriteDialog = true
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    studioViewModel.clearAnnotations(studioViewModel.pdfUri, deletePersisted = true)
                    studioViewModel.currentScreen = AppScreen.HOME
                }) { Text("Discard") }
            }
        )
    }

    if (showSaveOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showSaveOverwriteDialog = false },
            title = { Text("Save PDF") },
            text = { Text("Do you want to overwrite the original file or save as a new copy?") },
            confirmButton = {
                TextButton(onClick = {
                    showSaveOverwriteDialog = false
                    saveEditedPdf(overwrite = true)
                }) { Text("Overwrite") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSaveOverwriteDialog = false
                    saveEditedPdf(overwrite = false)
                }) { Text("Save as Copy") }
            }
        )
    }

    PDFEditorAppTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
        Scaffold(
            bottomBar = {
                if (currentScreen != AppScreen.EDITOR && currentScreen != AppScreen.SETTINGS) {
                    BottomNavBar(
                        currentScreen = currentScreen,
                        onTabSelected = { studioViewModel.currentScreen = it },
                        onScanClick = { launchDocumentScanner() }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (!isUnlocked) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("PDF Studio is locked", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Button(onClick = {
                                if (activity != null) {
                                    showBiometricPrompt(
                                        activity = activity,
                                        onSuccess = { isUnlocked = true },
                                        onError = { errorMessage = it }
                                    )
                                }
                            }) {
                                Text("Unlock with Biometrics")
                            }
                        }
                    }
                } else {
                    when (currentScreen) {
                        AppScreen.HOME -> HomeScreen(
                            recentFiles = recentFiles,
                            onOpenPdf = { openPdfLauncher.launch("application/pdf") },
                            onPhotoClick = { pickImageLauncher.launch("image/*") },
                            onScanDoc = { launchDocumentScanner() },
                            onFileTap = ::onFileTap,
                            onSettingsTap = { studioViewModel.currentScreen = AppScreen.SETTINGS },
                            onToggleFavorite = ::onToggleFavorite,
                            onRemove = ::onRemoveRecent,
                            onShare = ::onShareRecent
                        )

                        AppScreen.FILES -> FilesScreen(
                            recentFiles = recentFiles,
                            onFileTap = ::onFileTap,
                            onToggleFavorite = ::onToggleFavorite,
                            onRemove = ::onRemoveRecent,
                            onShare = ::onShareRecent,
                            onClearAll = {
                                recentFiles = emptyList()
                                persistRecentFiles(context, emptyList())
                            }
                        )

                        AppScreen.TOOLS -> ToolsScreen(
                            onOpenPdf = { uri -> openPdf(uri) },
                            onRecentFilesUpdated = { recentFiles = loadRecentFiles(context) }
                        )

                        AppScreen.EDITOR -> {
                            val activeRenderer = renderer
                            val activeUri = studioViewModel.pdfUri?.let { Uri.parse(it) }
                            if (activeRenderer != null && activeUri != null && pageCount > 0) {
                                PDFEditorAppTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                                    EditorScreen(
                                        renderer = activeRenderer,
                                        pdfUri = activeUri,
                                        currentPage = currentPage,
                                        pageCount = pageCount,
                                        zoomLevel = zoomLevel,
                                        editorMode = editorMode,
                                        currentColor = currentColor,
                                        strokeWidth = strokeWidth,
                                        pageStrokes = pageStrokes,
                                        pageTexts = pageTexts,
                                        pageShapes = pageShapes,
                                        pageSignatures = pageSignatures,
                                        savedSignatures = studioViewModel.savedSignatures,
                                        textIsBold = studioViewModel.textIsBold.value,
                                        textBgColor = studioViewModel.textBgColor.value,
                                        pageColorFilter = pageColorFilter,
                                        rendererMutex = rendererMutex,
                                        isBusy = isBusy,
                                        hasUnsavedChanges = hasUnsavedChanges,
                                        hasUndoHistory = studioViewModel.annotationHistory.isNotEmpty(),
                                        hasRedoHistory = studioViewModel.redoHistory.isNotEmpty(),
                                        onPageChange = { studioViewModel.currentPage = it.coerceIn(0, pageCount - 1) },
                                        onZoomChange = { studioViewModel.zoomLevel = it.coerceIn(1f, 3f) },
                                        onModeChange = { studioViewModel.editorMode = it },
                                        onColorChange = { studioViewModel.currentColor = it },
                                        onWidthChange = { studioViewModel.strokeWidth = it },
                                        onTextBoldToggle = { studioViewModel.textIsBold.value = !studioViewModel.textIsBold.value },
                                        onTextBgColorChange = { studioViewModel.textBgColor.value = it },
                                        onPageColorFilterChange = {
                                            pageColorFilter = it
                                            prefs.edit().putString("page_color_filter", it.name).apply()
                                        },
                                        onStrokeAdded = { pageIdx, stroke ->
                                            studioViewModel.pushAction(StudioViewModel.AnnotationAction.StrokeAdded(pageIdx, stroke))
                                        },
                                        onTextAdded = { pageIdx, text ->
                                            studioViewModel.pushAction(StudioViewModel.AnnotationAction.TextAdded(pageIdx, text))
                                        },
                                        onShapeAdded = { pageIdx, shape ->
                                            studioViewModel.pushAction(StudioViewModel.AnnotationAction.ShapeAdded(pageIdx, shape))
                                        },
                                        onSignatureAdded = { pageIdx, sig ->
                                            studioViewModel.pushAction(StudioViewModel.AnnotationAction.SignatureAdded(pageIdx, sig))
                                        },
                                        onSaveSignaturePreset = { strokes ->
                                            studioViewModel.saveSignaturePreset(strokes)
                                        },
                                        onEraseAt = { pageIdx, point ->
                                            val strokes = pageStrokes[pageIdx].orEmpty()
                                            val texts = pageTexts[pageIdx].orEmpty()
                                            val nearestStroke = strokes.minByOrNull { strokeDistance(point, it) }
                                            val nearestStrokeDistance = nearestStroke?.let { strokeDistance(point, it) } ?: Float.MAX_VALUE
                                            val nearestText = texts.minByOrNull { hypot(point.x - it.position.x, point.y - it.position.y) }
                                            val nearestTextDistance = nearestText?.let { hypot(point.x - it.position.x, point.y - it.position.y) } ?: Float.MAX_VALUE

                                            when {
                                                nearestStroke != null && nearestStrokeDistance <= 0.045f -> {
                                                    studioViewModel.pushAction(StudioViewModel.AnnotationAction.StrokeRemoved(pageIdx, nearestStroke))
                                                }
                                                nearestText != null && nearestTextDistance <= 0.09f -> {
                                                    studioViewModel.pushAction(StudioViewModel.AnnotationAction.TextRemoved(pageIdx, nearestText))
                                                }
                                            }
                                        },
                                        onUndo = { studioViewModel.undo() },
                                        onRedo = { studioViewModel.redo() },
                                        onSaveClick = { showSaveOverwriteDialog = true },
                                        onShareClick = { uri ->
                                            if (hasUnsavedChanges) {
                                                errorMessage = "Save your changes before sharing the PDF."
                                            } else {
                                                runCatching {
                                                    sharePdf(context, uri)
                                                }.onFailure { e ->
                                                    errorMessage = e.message ?: "Could not share PDF"
                                                }
                                            }
                                        },
                                        onBackClick = { confirmBackNavigation() }
                                    )
                                }
                            }
                        }

                        AppScreen.SETTINGS -> SettingsScreen(
                            themeMode = themeMode,
                            onThemeModeChange = {
                                themeMode = it
                                prefs.edit().putString("theme_mode", it.name).apply()
                            },
                            dynamicColor = dynamicColor,
                            onDynamicColorChange = {
                                dynamicColor = it
                                prefs.edit().putBoolean("dynamic_color", it).apply()
                            },
                            appLockEnabled = appLockEnabled,
                            onAppLockToggle = {
                                appLockEnabled = it
                                prefs.edit().putBoolean("app_lock_enabled", it).apply()
                            },
                            onBackClick = { studioViewModel.currentScreen = AppScreen.HOME }
                        )
                    }
                }

                if (isBusy) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text("Working…")
                            }
                        }
                    }
                }

                errorMessage?.let { message ->
                    LaunchedEffect(message) {
                        delay(3500.milliseconds)
                        if (errorMessage == message) errorMessage = null
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                message,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                passwordPromptUri?.let { uri ->
                    AlertDialog(
                        onDismissRequest = { passwordPromptUri = null },
                        title = { Text("Password Protected PDF") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("This PDF document is encrypted. Enter the password to open:")
                                OutlinedTextField(
                                    value = passwordPromptText,
                                    onValueChange = { passwordPromptText = it },
                                    label = { Text("Password") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val pass = passwordPromptText.trim()
                                val targetUri = uri
                                passwordPromptUri = null
                                if (pass.isNotEmpty()) {
                                    scope.launch {
                                        isBusy = true
                                        val unlockedFile = unlockPdf(context, targetUri, pass)
                                        isBusy = false
                                        if (unlockedFile != null) {
                                            openPdf(fileProviderUri(context, unlockedFile))
                                        } else {
                                            errorMessage = "Incorrect password or unable to decrypt PDF."
                                        }
                                    }
                                }
                            }) { Text("Open") }
                        },
                        dismissButton = {
                            TextButton(onClick = { passwordPromptUri = null }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}
