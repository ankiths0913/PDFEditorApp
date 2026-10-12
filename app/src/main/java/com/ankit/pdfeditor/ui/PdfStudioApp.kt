package com.ankit.pdfeditor.ui

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.ankit.pdfeditor.model.*
import com.ankit.pdfeditor.ui.files.FilesScreen
import com.ankit.pdfeditor.ui.home.HomeScreen
import com.ankit.pdfeditor.ui.editor.EditorScreen
import com.ankit.pdfeditor.ui.nav.BottomNavBar
import com.ankit.pdfeditor.ui.settings.SettingsScreen
import com.ankit.pdfeditor.ui.theme.PDFEdittorAppTheme
import com.ankit.pdfeditor.util.*
import com.ankit.pdfeditor.ui.tools.ToolsScreen
import com.ankit.pdfeditor.ui.theme.StudioAmber400
import com.ankit.pdfeditor.ui.theme.StudioBlue50
import com.ankit.pdfeditor.ui.theme.StudioBlue500
import com.ankit.pdfeditor.ui.theme.StudioBlue600
import com.ankit.pdfeditor.ui.theme.StudioEmerald500
import com.ankit.pdfeditor.ui.theme.StudioIndigo50
import com.ankit.pdfeditor.ui.theme.StudioIndigo600
import com.ankit.pdfeditor.ui.theme.StudioRed100
import com.ankit.pdfeditor.ui.theme.StudioRed50
import com.ankit.pdfeditor.ui.theme.StudioRed500
import com.ankit.pdfeditor.ui.theme.StudioSky50
import com.ankit.pdfeditor.ui.theme.StudioSky600
import com.ankit.pdfeditor.ui.theme.StudioSlate100
import com.ankit.pdfeditor.ui.theme.StudioSlate200
import com.ankit.pdfeditor.ui.theme.StudioSlate400
import com.ankit.pdfeditor.ui.theme.StudioSlate50
import com.ankit.pdfeditor.ui.theme.StudioSlate500
import com.ankit.pdfeditor.ui.theme.StudioSlate600
import com.ankit.pdfeditor.ui.theme.StudioSlate700
import com.ankit.pdfeditor.ui.theme.StudioSlate800
import com.ankit.pdfeditor.ui.theme.StudioSlate900
import com.ankit.pdfeditor.ui.theme.StudioViolet50
import com.ankit.pdfeditor.ui.theme.StudioViolet600
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.time.Duration.Companion.milliseconds

private const val DARK_THEME_KEY = "dark_theme"
private const val MAX_UNDO_HISTORY = 50
private const val MAX_DISPLAY_RENDER_DIMENSION = 1600
private const val DEFAULT_HIGHLIGHT_ALPHA = 0.7f

// Brand primary color used throughout the UI (matches the indigo accent used
// by the theme module). Declared locally so this file doesn't need an extra
// import just for a single accent color.
private val PdfPrimary = Color(0xFF6366F1)

private sealed interface AnnotationAction {
    data class StrokeAdded(val page: Int, val stroke: PdfStroke) : AnnotationAction
    data class StrokeRemoved(val page: Int, val stroke: PdfStroke) : AnnotationAction
    data class TextAdded(val page: Int, val text: PdfText) : AnnotationAction
    data class TextRemoved(val page: Int, val text: PdfText) : AnnotationAction
}

private suspend fun writeFlattenedPdf(
    renderer: PdfRenderer,
    strokesByPage: Map<Int, List<PdfStroke>>,
    textsByPage: Map<Int, List<PdfText>>,
    outputStream: OutputStream,
    rendererMutex: Mutex
) = withContext(Dispatchers.IO) {
    rendererMutex.withLock {
        val pdfDocument = PdfDocument()
        try {
            for (pageIndex in 0 until renderer.pageCount) {
                val page = renderer.openPage(pageIndex)
                try {
                    val sourceWidth = page.width
                    val sourceHeight = page.height
                    val scale = minOf(
                        1f,
                        MAX_EXPORT_RENDER_DIMENSION.toFloat() / maxOf(sourceWidth, sourceHeight)
                    )
                    val width = (sourceWidth * scale).toInt().coerceAtLeast(1)
                    val height = (sourceHeight * scale).toInt().coerceAtLeast(1)
                    var bitmap: Bitmap? = null
                    try {
                        bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        bakeAnnotations(
                            canvas = android.graphics.Canvas(bitmap),
                            bitmap = bitmap,
                            strokes = strokesByPage[pageIndex].orEmpty(),
                            texts = textsByPage[pageIndex].orEmpty()
                        )
                        val pageInfo = PdfDocument.PageInfo.Builder(width, height, pageIndex + 1).create()
                        val outputPage = pdfDocument.startPage(pageInfo)
                        outputPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        pdfDocument.finishPage(outputPage)
                    } finally {
                        bitmap?.let { if (!it.isRecycled) it.recycle() }
                    }
                } finally {
                    page.close()
                }
            }
            pdfDocument.writeTo(outputStream)
        } finally {
            pdfDocument.close()
        }
    }
}

@Composable
internal fun PdfStudioApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rendererMutex = remember { Mutex() }
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var isDarkTheme by remember { mutableStateOf(prefs.getBoolean(DARK_THEME_KEY, false)) }
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    var editorMode by remember { mutableStateOf(EditorMode.VIEW) }
    var currentColor by remember { mutableStateOf(Color.Red) }
    var strokeWidth by remember { mutableFloatStateOf(8f) }

    var recentFiles by remember { mutableStateOf(loadRecentFiles(context)) }
    val pageStrokes = remember { mutableStateMapOf<Int, List<PdfStroke>>() }
    val pageTexts = remember { mutableStateMapOf<Int, List<PdfText>>() }
    val annotationHistory = remember { mutableStateListOf<AnnotationAction>() }

    fun clearAnnotations() {
        pageStrokes.clear()
        pageTexts.clear()
        annotationHistory.clear()
        hasUnsavedChanges = false
    }

    fun setChanged() {
        hasUnsavedChanges =
            pageStrokes.values.any { it.isNotEmpty() } ||
                    pageTexts.values.any { it.isNotEmpty() }
    }

    fun pushHistory(action: AnnotationAction) {
        annotationHistory.add(action)
        if (annotationHistory.size > MAX_UNDO_HISTORY) {
            annotationHistory.removeAt(0)
        }
    }

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
            isFavorite = recentFiles.firstOrNull { it.uri == uri.toString() }?.isFavorite == true
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
                currentPage = 0

                withContext(Dispatchers.IO) {
                    try {
                        oldRenderer?.close()
                    } catch (_: IllegalStateException) {
                    }
                }
            }

            clearAnnotations()

            renderer = newRenderer
            pageCount = newPageCount
            currentPage = 0
            zoomLevel = 1f
            editorMode = EditorMode.VIEW
            pdfUri = uri
            hasUnsavedChanges = false
            installed = true

            addRecent(
                uri,
                fileNameOverride ?: withContext(Dispatchers.IO) {
                    resolveFileName(context, uri)
                }
            )

            currentScreen = AppScreen.EDITOR
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
                errorMessage = e.message ?: "Could not open PDF"
            } finally {
                isBusy = false
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
        }
        openPdf(uri)
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            errorMessage = null
            try {
                val generated = withContext(Dispatchers.IO) {
                    convertImageToPdfOnDevice(context, uri)
                } ?: throw IOException("Could not convert the selected image to PDF")
                loadPdf(fileProviderUri(context, generated), generated.name)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not import image"
            } finally {
                isBusy = false
            }
        }
    }

    // "Scan Doc" / Camera import: capture a photo, then reuse the same
    // image -> PDF conversion path as the Photos picker above.
    var pendingScanFile by remember { mutableStateOf<File?>(null) }
    var pendingScanUri by remember { mutableStateOf<Uri?>(null) }

    fun handleScanCaptureResult(success: Boolean) {
        val capturedFile = pendingScanFile
        val capturedUri = pendingScanUri
        pendingScanFile = null
        pendingScanUri = null
        if (!success || capturedFile == null || capturedUri == null) {
            if (capturedFile?.exists() == true) capturedFile.delete()
            return
        }
        scope.launch {
            isBusy = true
            errorMessage = null
            try {
                val generated = withContext(Dispatchers.IO) {
                    convertImageToPdfOnDevice(context, capturedUri)
                } ?: throw IOException("Could not convert the scanned photo to PDF")
                withContext(Dispatchers.IO) { capturedFile.delete() }
                loadPdf(fileProviderUri(context, generated), generated.name)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not process the scanned document"
            } finally {
                isBusy = false
            }
        }
    }

    val scanCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> handleScanCaptureResult(success) }

    fun startScanCapture() {
        val file = File(appDocumentsDir(context), "Scan_${System.currentTimeMillis()}.jpg")
        val uri = fileProviderUri(context, file)
        pendingScanFile = file
        pendingScanUri = uri
        scanCameraLauncher.launch(uri)
    }

    val scanCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startScanCapture()
        } else {
            errorMessage = "Camera permission is required to scan documents."
        }
    }

    fun launchScanDoc() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            startScanCapture()
        } else {
            scanCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val activeRenderer = renderer ?: return@rememberLauncherForActivityResult
        val strokesSnapshot = pageStrokes.mapValues { it.value.toList() }
        val textsSnapshot = pageTexts.mapValues { it.value.toList() }
        scope.launch {
            isBusy = true
            errorMessage = null
            try {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IOException("Unable to create the destination PDF")
                output.use {
                    writeFlattenedPdf(
                        renderer = activeRenderer,
                        strokesByPage = strokesSnapshot,
                        textsByPage = textsSnapshot,
                        outputStream = it,
                        rendererMutex = rendererMutex
                    )
                }
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                loadPdf(uri, resolveFileName(context, uri))
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not save PDF"
            } finally {
                isBusy = false
            }
        }
    }

    fun requestBack() {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            currentScreen = AppScreen.HOME
        }
    }

    BackHandler(enabled = currentScreen != AppScreen.HOME) {
        if (currentScreen == AppScreen.EDITOR) {
            requestBack()
        } else {
            currentScreen = AppScreen.HOME
        }
    }

    val latestRenderer by rememberUpdatedState(renderer)
    DisposableEffect(Unit) {
        onDispose {
            try {
                latestRenderer?.close()
            } catch (_: IllegalStateException) {
            }
        }
    }

    PDFEdittorAppTheme(darkTheme = isDarkTheme) {
        val onOpenPdf: () -> Unit = { filePicker.launch(arrayOf("application/pdf")) }
        val onPhotoClick: () -> Unit = {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        val onScanDoc: () -> Unit = { launchScanDoc() }
        val onFileTap: (Uri) -> Unit = { selectedUri -> openPdf(selectedUri) }
        val onToggleFavorite: (RecentFileItem) -> Unit = { fileItem ->
            recentFiles = recentFiles.map {
                if (it.uri == fileItem.uri) it.copy(isFavorite = !it.isFavorite) else it
            }
            persistRecentFiles(context, recentFiles)
        }
        val onRemoveRecent: (RecentFileItem) -> Unit = { fileItem ->
            recentFiles = recentFiles.filterNot { it.uri == fileItem.uri }
            persistRecentFiles(context, recentFiles)
        }
        val onShareRecent: (RecentFileItem) -> Unit = { fileItem ->
            try {
                sharePdf(context, Uri.parse(fileItem.uri))
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not share PDF"
            }
        }

        when (currentScreen) {
            AppScreen.HOME -> HomeScreen(
                recentFiles = recentFiles,
                onOpenPdf = onOpenPdf,
                onPhotoClick = onPhotoClick,
                onScanDoc = onScanDoc,
                onFileTap = onFileTap,
                onSettingsTap = { currentScreen = AppScreen.SETTINGS },
                onToggleFavorite = onToggleFavorite,
                onRemove = onRemoveRecent,
                onShare = onShareRecent
            )

            AppScreen.FILES -> FilesScreen(
                recentFiles = recentFiles,
                onFileTap = onFileTap,
                onToggleFavorite = onToggleFavorite,
                onRemove = onRemoveRecent,
                onShare = onShareRecent
            )

            AppScreen.TOOLS -> ToolsScreen()

            AppScreen.EDITOR -> {
                val activeRenderer = renderer
                val activeUri = pdfUri
                if (activeRenderer != null && activeUri != null && pageCount > 0) {
                    // The editor is always dark, independent of the app-wide theme
                    // toggle in Settings — an immersive dark canvas is the intended
                    // design for annotating, similar to most PDF/photo editors.
                    PDFEdittorAppTheme(darkTheme = true) {
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
                            rendererMutex = rendererMutex,
                            isBusy = isBusy,
                            hasUnsavedChanges = hasUnsavedChanges,
                            hasUndoHistory = annotationHistory.isNotEmpty(),
                            onPageChange = { currentPage = it.coerceIn(0, pageCount - 1) },
                            onZoomChange = { zoomLevel = it.coerceIn(1f, 3f) },
                            onModeChange = { editorMode = it },
                            onColorChange = { currentColor = it },
                            onWidthChange = { strokeWidth = it },
                            onStrokeAdded = { pageIdx, stroke ->
                                pageStrokes[pageIdx] = pageStrokes[pageIdx].orEmpty() + stroke
                                pushHistory(AnnotationAction.StrokeAdded(pageIdx, stroke))
                                setChanged()
                            },
                            onTextAdded = { pageIdx, text ->
                                pageTexts[pageIdx] = pageTexts[pageIdx].orEmpty() + text
                                pushHistory(AnnotationAction.TextAdded(pageIdx, text))
                                setChanged()
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
                                        pageStrokes[pageIdx] = strokes.filterNot { it.id == nearestStroke.id }
                                        pushHistory(AnnotationAction.StrokeRemoved(pageIdx, nearestStroke))
                                        setChanged()
                                    }
                                    nearestText != null && nearestTextDistance <= 0.09f -> {
                                        pageTexts[pageIdx] = texts.filterNot { it.id == nearestText.id }
                                        pushHistory(AnnotationAction.TextRemoved(pageIdx, nearestText))
                                        setChanged()
                                    }
                                }
                            },
                            onUndo = {
                                when (val action = annotationHistory.removeLastOrNull()) {
                                    is AnnotationAction.StrokeAdded -> {
                                        pageStrokes[action.page] = pageStrokes[action.page].orEmpty()
                                            .filterNot { it.id == action.stroke.id }
                                    }
                                    is AnnotationAction.StrokeRemoved -> {
                                        pageStrokes[action.page] = pageStrokes[action.page].orEmpty() + action.stroke
                                    }
                                    is AnnotationAction.TextAdded -> {
                                        pageTexts[action.page] = pageTexts[action.page].orEmpty()
                                            .filterNot { it.id == action.text.id }
                                    }
                                    is AnnotationAction.TextRemoved -> {
                                        pageTexts[action.page] = pageTexts[action.page].orEmpty() + action.text
                                    }
                                    null -> Unit
                                }
                                setChanged()
                            },
                            onSaveClick = {
                                val baseName = safeFileName(resolveFileName(context, activeUri))
                                    .removeSuffix(".pdf")
                                    .ifBlank { "document" }
                                saveLauncher.launch("Edited_${baseName}.pdf")
                            },
                            onShareClick = { uri ->
                                if (hasUnsavedChanges) {
                                    errorMessage = "Save your changes before sharing the PDF."
                                } else {
                                    try {
                                        sharePdf(context, uri)
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Could not share PDF"
                                    }
                                }
                            },
                            onBackClick = ::requestBack
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (isBusy) CircularProgressIndicator() else Text("No PDF is currently open")
                    }
                }
            }

            AppScreen.SETTINGS -> SettingsScreen(
                isDarkTheme = isDarkTheme,
                onThemeToggle = { value ->
                    isDarkTheme = value
                    prefs.edit { putBoolean(DARK_THEME_KEY, value) }
                },
                onBackClick = { currentScreen = AppScreen.HOME }
            )
        }

        if (currentScreen != AppScreen.EDITOR) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                BottomNavBar(
                    currentScreen = currentScreen,
                    onTabSelected = { currentScreen = it },
                    onScanClick = onScanDoc
                )
            }
        }

        if (isBusy) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
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

        if (showUnsavedDialog) {
            AlertDialog(
                onDismissRequest = { showUnsavedDialog = false },
                title = { Text("Unsaved changes") },
                text = { Text("You have unsaved annotations. Save them before leaving the editor?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showUnsavedDialog = false
                            val activeUri = pdfUri ?: return@TextButton
                            val baseName = safeFileName(resolveFileName(context, activeUri))
                                .removeSuffix(".pdf")
                                .ifBlank { "document" }
                            saveLauncher.launch("Edited_${baseName}.pdf")
                        }
                    ) { Text("Save") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            showUnsavedDialog = false
                            clearAnnotations()
                            currentScreen = AppScreen.HOME
                        }) { Text("Discard") }
                        TextButton(onClick = { showUnsavedDialog = false }) { Text("Cancel") }
                    }
                }
            )
        }
    }
}
