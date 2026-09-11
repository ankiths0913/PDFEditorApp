package com.ankit.pdfeditor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.ankit.pdfeditor.ui.theme.PDFEdittorAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

private const val PREFS_NAME = "pdf_studio_prefs"
private const val RECENT_FILES_KEY = "recent_files"
private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
private const val MAX_RECENT_FILES = 20
private const val MAX_RENDER_DIMENSION = 2048

private enum class EditorMode { VIEW, DRAW, HIGHLIGHT, TEXT, ERASE }
private enum class AppScreen { HOME, EDITOR, SETTINGS }
private enum class SortOption { DATE, NAME }

private data class PdfStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<Offset>, // Normalized page coordinates: 0..1
    val color: Color = Color.Red,
    val widthFraction: Float = 0.008f
)

private data class PdfText(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val position: Offset, // Normalized page coordinates: 0..1
    val color: Color = Color.Black,
    val sizeFraction: Float = 0.025f
)

private data class RecentFileItem(
    val uri: String,
    val fileName: String,
    val openedAt: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)

private sealed interface AnnotationAction {
    data class StrokeAdded(val page: Int, val strokeId: String) : AnnotationAction
    data class TextAdded(val page: Int, val textId: String) : AnnotationAction
}

private data class PageRenderInfo(
    val bitmap: Bitmap
)

private fun sharePdf(context: Context, uri: Uri) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share PDF via"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun fileProviderUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, file)

private fun appDocumentsDir(context: Context): File =
    File(context.filesDir, "pdf_documents").apply { mkdirs() }

private fun safeFileName(name: String): String {
    val cleaned = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return cleaned.ifBlank { "document.pdf" }
}

private fun dateString(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())

private fun resolveFileName(context: Context, uri: Uri): String {
    if (uri.scheme == "file") {
        return File(uri.path.orEmpty()).name.ifBlank { "PDF Document" }
    }
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) cursor.getString(index) else "PDF Document"
            } else {
                "PDF Document"
            }
        } ?: "PDF Document"
    } catch (_: Exception) {
        "PDF Document"
    }
}

private fun persistRecentFiles(context: Context, items: List<RecentFileItem>) {
    val array = JSONArray()
    items.take(MAX_RECENT_FILES).forEach { item ->
        array.put(
            JSONObject().apply {
                put("uri", item.uri)
                put("fileName", item.fileName)
                put("openedAt", item.openedAt)
                put("timestamp", item.timestamp)
                put("isFavorite", item.isFavorite)
            }
        )
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(RECENT_FILES_KEY, array.toString())
        .apply()
}

private fun loadRecentFiles(context: Context): List<RecentFileItem> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(RECENT_FILES_KEY, null)
        ?: return emptyList()

    return try {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    RecentFileItem(
                        uri = obj.getString("uri"),
                        fileName = obj.optString("fileName", "PDF Document"),
                        openedAt = obj.optString("openedAt", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isFavorite = obj.optBoolean("isFavorite", false)
                    )
                )
            }
        }.sortedByDescending { it.timestamp }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun convertImageToPdfOnDevice(context: Context, imageUri: Uri): File? {
    return try {
        val bitmap = context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null

        val maxDimension = MAX_RENDER_DIMENSION
        val scaledBitmap = if (maxOf(bitmap.width, bitmap.height) > maxDimension) {
            val scale = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
            bitmap.scale(
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1)
            ).also { bitmap.recycle() }
        } else {
            bitmap
        }

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(
            scaledBitmap.width,
            scaledBitmap.height,
            1
        ).create()
        val page = pdfDocument.startPage(pageInfo)
        page.canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
        pdfDocument.finishPage(page)

        val outputFile = File(
            appDocumentsDir(context),
            "Converted_${System.currentTimeMillis()}.pdf"
        )

        FileOutputStream(outputFile).use { output -> pdfDocument.writeTo(output) }
        pdfDocument.close()
        scaledBitmap.recycle()
        outputFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun distancePointToSegment(point: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    if (abs(dx) < 0.000001f && abs(dy) < 0.000001f) {
        return hypot(point.x - a.x, point.y - a.y)
    }
    val t = (((point.x - a.x) * dx) + ((point.y - a.y) * dy)) / (dx * dx + dy * dy)
    val clamped = t.coerceIn(0f, 1f)
    val closestX = a.x + clamped * dx
    val closestY = a.y + clamped * dy
    return hypot(point.x - closestX, point.y - closestY)
}

private fun strokeDistance(point: Offset, stroke: PdfStroke): Float {
    if (stroke.points.isEmpty()) return Float.MAX_VALUE
    if (stroke.points.size == 1) {
        return hypot(point.x - stroke.points[0].x, point.y - stroke.points[0].y)
    }
    return stroke.points.zipWithNext().minOf { (a, b) -> distancePointToSegment(point, a, b) }
}

private fun buildComposePath(points: List<Offset>): Path {
    return Path().apply {
        if (points.isNotEmpty()) {
            moveTo(points.first().x, points.first().y)
            for (point in points.drop(1)) lineTo(point.x, point.y)
        }
    }
}

private fun Color.toAndroidColor(): Int =
    android.graphics.Color.argb(
        (alpha.coerceIn(0f, 1f) * 255).toInt(),
        (red.coerceIn(0f, 1f) * 255).toInt(),
        (green.coerceIn(0f, 1f) * 255).toInt(),
        (blue.coerceIn(0f, 1f) * 255).toInt()
    )

private fun DrawScope.drawNormalizedStroke(stroke: PdfStroke) {
    if (stroke.points.isEmpty()) return
    val pageMin = min(size.width, size.height)
    val screenPoints = stroke.points.map { point ->
        Offset(point.x * size.width, point.y * size.height)
    }
    val path = buildComposePath(screenPoints)
    drawPath(
        path = path,
        color = stroke.color,
        style = Stroke(width = stroke.widthFraction * pageMin, cap = StrokeCap.Round)
    )
}

private fun bakeAnnotations(
    canvas: android.graphics.Canvas,
    bitmap: Bitmap,
    strokes: List<PdfStroke>,
    texts: List<PdfText>
) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }

    val pageMin = min(bitmap.width, bitmap.height).toFloat()
    strokes.forEach { stroke ->
        if (stroke.points.isEmpty()) return@forEach
        paint.color = stroke.color.toAndroidColor()
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = stroke.widthFraction * pageMin
        val path = android.graphics.Path()
        val first = stroke.points.first()
        path.moveTo(first.x * bitmap.width, first.y * bitmap.height)
        stroke.points.drop(1).forEach { point ->
            path.lineTo(point.x * bitmap.width, point.y * bitmap.height)
        }
        canvas.drawPath(path, paint)
    }

    texts.forEach { text ->
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = text.color.toAndroidColor()
        paint.textSize = text.sizeFraction * bitmap.width
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        canvas.drawText(
            text.text,
            text.position.x * bitmap.width,
            text.position.y * bitmap.height,
            paint
        )
    }
}

private suspend fun flattenPdfWithAnnotations(
    renderer: PdfRenderer,
    strokesByPage: Map<Int, List<PdfStroke>>,
    textsByPage: Map<Int, List<PdfText>>,
    outputFile: File,
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
                    val scale = min(
                        1f,
                        MAX_RENDER_DIMENSION.toFloat() / maxOf(sourceWidth, sourceHeight)
                    )
                    val width = (sourceWidth * scale).toInt().coerceAtLeast(1)
                    val height = (sourceHeight * scale).toInt().coerceAtLeast(1)
                    val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val canvas = android.graphics.Canvas(bitmap)
                    bakeAnnotations(
                        canvas = canvas,
                        bitmap = bitmap,
                        strokes = strokesByPage[pageIndex].orEmpty(),
                        texts = textsByPage[pageIndex].orEmpty()
                    )

                    val pageInfo = PdfDocument.PageInfo.Builder(width, height, pageIndex + 1).create()
                    val outputPage = pdfDocument.startPage(pageInfo)
                    outputPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(outputPage)
                    bitmap.recycle()
                } finally {
                    page.close()
                }
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { output -> pdfDocument.writeTo(output) }
        } finally {
            pdfDocument.close()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PdfStudioApp() }
    }
}

@Composable
private fun PdfStudioApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rendererMutex = remember { Mutex() }

    var isDarkTheme by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var editorMode by remember { mutableStateOf(EditorMode.VIEW) }
    var currentColor by remember { mutableStateOf(Color.Red) }
    var strokeWidth by remember { mutableFloatStateOf(8f) }

    var recentFiles by remember { mutableStateOf(loadRecentFiles(context)) }
    val pageStrokes = remember { mutableStateMapOf<Int, List<PdfStroke>>() }
    val pageTexts = remember { mutableStateMapOf<Int, List<PdfText>>() }
    val annotationHistory = remember { mutableStateListOf<AnnotationAction>() }

    fun closeRenderer() {
        renderer?.close()
        renderer = null
        pageCount = 0
        currentPage = 0
    }

    fun clearAnnotations() {
        pageStrokes.clear()
        pageTexts.clear()
        annotationHistory.clear()
    }

    fun addRecent(uri: Uri, fileName: String) {
        val old = recentFiles.firstOrNull { it.uri == uri.toString() }
        val updated = RecentFileItem(
            uri = uri.toString(),
            fileName = fileName,
            openedAt = dateString(),
            timestamp = System.currentTimeMillis(),
            isFavorite = old?.isFavorite == true
        )
        recentFiles = listOf(updated) + recentFiles.filterNot { it.uri == updated.uri }
        recentFiles = recentFiles.sortedByDescending { it.timestamp }.take(MAX_RECENT_FILES)
        persistRecentFiles(context, recentFiles)
    }

    fun openPdf(uri: Uri, fileNameOverride: String? = null) {
        scope.launch {
            isBusy = true
            errorMessage = null
            try {
                val result = withContext(Dispatchers.IO) {
                    val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                        ?: error("Unable to open the selected PDF")
                    try {
                        PdfRenderer(descriptor) to resolveFileName(context, uri)
                    } catch (e: Exception) {
                        descriptor.close()
                        throw e
                    }
                }

                rendererMutex.withLock {
                    closeRenderer()
                    clearAnnotations()
                    renderer = result.first
                    pageCount = result.first.pageCount
                    currentPage = 0
                    zoomLevel = 1f
                    editorMode = EditorMode.VIEW
                    pdfUri = uri
                    addRecent(uri, fileNameOverride ?: result.second)
                    currentScreen = AppScreen.EDITOR
                }
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
                // Some providers don't offer persistable grants; the current session can still work.
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
                val generatedPdfFile = withContext(Dispatchers.IO) {
                    convertImageToPdfOnDevice(context, uri)
                } ?: error("Could not convert the selected image to PDF")
                openPdf(fileProviderUri(context, generatedPdfFile), generatedPdfFile.name)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not import image"
                isBusy = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            renderer?.close()
        }
    }

    PDFEdittorAppTheme(darkTheme = isDarkTheme) {
        when (currentScreen) {
            AppScreen.HOME -> {
                HomeScreen(
                    recentFiles = recentFiles,
                    onOpenPdf = { filePicker.launch(arrayOf("application/pdf")) },
                    onPhotoClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onFileTap = { selectedUri -> openPdf(selectedUri) },
                    onSettingsTap = { currentScreen = AppScreen.SETTINGS },
                    onToggleFavorite = { fileItem ->
                        recentFiles = recentFiles.map {
                            if (it.uri == fileItem.uri) it.copy(isFavorite = !it.isFavorite) else it
                        }
                        persistRecentFiles(context, recentFiles)
                    }
                )
            }

            AppScreen.EDITOR -> {
                val safeRenderer = renderer
                if (safeRenderer != null && pdfUri != null) {
                    EditorScreen(
                        renderer = safeRenderer,
                        pdfUri = pdfUri,
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
                        onPageChange = { currentPage = it.coerceIn(0, (pageCount - 1).coerceAtLeast(0)) },
                        onZoomChange = { zoomLevel = it.coerceIn(1f, 3f) },
                        onModeChange = { editorMode = it },
                        onColorChange = { currentColor = it },
                        onWidthChange = { strokeWidth = it },
                        onStrokeAdded = { pageIdx, stroke ->
                            pageStrokes[pageIdx] = pageStrokes[pageIdx].orEmpty() + stroke
                            annotationHistory.add(AnnotationAction.StrokeAdded(pageIdx, stroke.id))
                        },
                        onTextAdded = { pageIdx, text ->
                            pageTexts[pageIdx] = pageTexts[pageIdx].orEmpty() + text
                            annotationHistory.add(AnnotationAction.TextAdded(pageIdx, text.id))
                        },
                        onEraseAt = { pageIdx, point ->
                            val strokes = pageStrokes[pageIdx].orEmpty()
                            val texts = pageTexts[pageIdx].orEmpty()
                            val nearestStroke = strokes.minByOrNull { strokeDistance(point, it) }
                            val strokeDistanceValue = nearestStroke?.let { strokeDistance(point, it) } ?: Float.MAX_VALUE
                            val nearestText = texts.minByOrNull {
                                hypot(point.x - it.position.x, point.y - it.position.y)
                            }
                            val textDistanceValue = nearestText?.let {
                                hypot(point.x - it.position.x, point.y - it.position.y)
                            } ?: Float.MAX_VALUE

                            when {
                                strokeDistanceValue <= 0.045f && nearestStroke != null -> {
                                    pageStrokes[pageIdx] = strokes.filterNot { it.id == nearestStroke.id }
                                }
                                textDistanceValue <= 0.09f && nearestText != null -> {
                                    pageTexts[pageIdx] = texts.filterNot { it.id == nearestText.id }
                                }
                            }
                        },
                        onUndo = {
                            val action = annotationHistory.removeLastOrNull()
                            when (action) {
                                is AnnotationAction.StrokeAdded -> {
                                    pageStrokes[action.page] = pageStrokes[action.page].orEmpty()
                                        .filterNot { it.id == action.strokeId }
                                }
                                is AnnotationAction.TextAdded -> {
                                    pageTexts[action.page] = pageTexts[action.page].orEmpty()
                                        .filterNot { it.id == action.textId }
                                }
                                null -> Unit
                            }
                        },
                        onSaveClick = {
                            val sourceName = pdfUri?.let { resolveFileName(context, it) } ?: "document.pdf"
                            val output = File(
                                appDocumentsDir(context),
                                "Edited_${System.currentTimeMillis()}_${safeFileName(sourceName)}"
                            )
                            scope.launch {
                                isBusy = true
                                errorMessage = null
                                try {
                                    flattenPdfWithAnnotations(
                                        renderer = safeRenderer,
                                        strokesByPage = pageStrokes.toMap(),
                                        textsByPage = pageTexts.toMap(),
                                        outputFile = output,
                                        rendererMutex = rendererMutex
                                    )
                                    val newUri = fileProviderUri(context, output)
                                    openPdf(newUri, output.name)
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Could not save PDF"
                                    isBusy = false
                                }
                            }
                        },
                        onShareClick = { currentPdfUri -> sharePdf(context, currentPdfUri) },
                        onBackClick = { currentScreen = AppScreen.HOME }
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (isBusy) CircularProgressIndicator()
                        else Text("No PDF is currently open")
                    }
                }
            }

            AppScreen.SETTINGS -> {
                SettingsScreen(
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = { isDarkTheme = it },
                    onBackClick = { currentScreen = AppScreen.HOME }
                )
            }
        }

        if (isBusy) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
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
                kotlinx.coroutines.delay(3500)
                errorMessage = null
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
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
    }
}

@Composable
private fun HomeScreen(
    recentFiles: List<RecentFileItem>,
    onOpenPdf: () -> Unit,
    onPhotoClick: () -> Unit,
    onFileTap: (Uri) -> Unit,
    onSettingsTap: () -> Unit,
    onToggleFavorite: (RecentFileItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var currentSort by remember { mutableStateOf(SortOption.DATE) }

    val filteredFiles = recentFiles
        .filter { it.fileName.contains(searchQuery, ignoreCase = true) }
        .sortedWith(
            when (currentSort) {
                SortOption.DATE -> compareByDescending { it.timestamp }
                SortOption.NAME -> compareBy { it.fileName.lowercase() }
            }
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBarHome(onSettingsTap = onSettingsTap)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { WelcomeSection(onOpenPdf = onOpenPdf) }

            item {
                ImportSourcesSection(
                    onOpenPdf = onOpenPdf,
                    onPhotoClick = onPhotoClick
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search recent PDFs…") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recent Files (${filteredFiles.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = currentSort == SortOption.DATE,
                                onClick = { currentSort = SortOption.DATE },
                                label = { Text("Date") }
                            )
                            FilterChip(
                                selected = currentSort == SortOption.NAME,
                                onClick = { currentSort = SortOption.NAME },
                                label = { Text("Name") }
                            )
                        }
                    }
                }
            }

            if (filteredFiles.isNotEmpty()) {
                items(filteredFiles, key = { it.uri }) { file ->
                    RecentFileListItem(
                        file = file,
                        onTap = { onFileTap(Uri.parse(file.uri)) },
                        onFavoriteToggle = { onToggleFavorite(file) }
                    )
                }
            } else {
                item {
                    Text(
                        if (recentFiles.isEmpty()) {
                            "No recent PDF files found. Open a file to get started!"
                        } else {
                            "No recent PDFs match your search."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    )
                }
            }

            item { QuickActionsSection(onOpenPdf = onOpenPdf) }
        }
    }
}

@Composable
private fun TopBarHome(onSettingsTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "PDF Studio",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onSettingsTap) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF6366F1))
        }
    }
}

@Composable
private fun WelcomeSection(onOpenPdf: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF6366F1)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "PDF Reader & Editor",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Read, annotate, highlight, add text and organize your PDFs.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onOpenPdf,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text("Open PDF", color = Color(0xFF6366F1), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RecentFileListItem(
    file: RecentFileItem,
    onTap: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onTap,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = Color(0xFFEF4444)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        file.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        file.openedAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Favorite",
                    tint = if (file.isFavorite) Color(0xFFFFB800) else Color.LightGray
                )
            }
        }
    }
}

@Composable
private fun QuickActionsSection(onOpenPdf: () -> Unit) {
    Column {
        Text(
            "Quick Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                icon = Icons.Default.Add,
                title = "Open PDF",
                modifier = Modifier.weight(1f),
                onClick = onOpenPdf
            )
            QuickActionCard(
                icon = Icons.Default.Edit,
                title = "Edit PDF",
                modifier = Modifier.weight(1f),
                onClick = onOpenPdf
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color(0xFF6366F1))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EditorScreen(
    renderer: PdfRenderer,
    pdfUri: Uri?,
    currentPage: Int,
    pageCount: Int,
    zoomLevel: Float,
    editorMode: EditorMode,
    currentColor: Color,
    strokeWidth: Float,
    pageStrokes: Map<Int, List<PdfStroke>>,
    pageTexts: Map<Int, List<PdfText>>,
    rendererMutex: Mutex,
    isBusy: Boolean,
    onPageChange: (Int) -> Unit,
    onZoomChange: (Float) -> Unit,
    onModeChange: (EditorMode) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onStrokeAdded: (Int, PdfStroke) -> Unit,
    onTextAdded: (Int, PdfText) -> Unit,
    onEraseAt: (Int, Offset) -> Unit,
    onUndo: () -> Unit,
    onSaveClick: () -> Unit,
    onShareClick: (Uri) -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            EditorTopBar(
                fileName = pdfUri?.let { uri ->
                    val context = LocalContext.current
                    resolveFileName(context, uri)
                } ?: "PDF Document",
                onShareClick = { pdfUri?.let(onShareClick) },
                onSaveClick = onSaveClick,
                onBackClick = onBackClick,
                saveEnabled = !isBusy
            )
        },
        bottomBar = {
            EditorBottomBar(
                editorMode = editorMode,
                onModeChange = onModeChange,
                currentColor = currentColor,
                onColorChange = onColorChange,
                strokeWidth = strokeWidth,
                onWidthChange = onWidthChange,
                zoomLevel = zoomLevel,
                onZoomChange = onZoomChange,
                canUndo = pageStrokes.values.any { it.isNotEmpty() } || pageTexts.values.any { it.isNotEmpty() },
                onUndo = onUndo
            )
        }
    ) { innerPadding ->
        PdfViewerWithSwipe(
            renderer = renderer,
            currentPage = currentPage,
            pageCount = pageCount,
            zoomLevel = zoomLevel,
            editorMode = editorMode,
            currentColor = currentColor,
            strokeWidth = strokeWidth,
            strokes = pageStrokes[currentPage].orEmpty(),
            texts = pageTexts[currentPage].orEmpty(),
            rendererMutex = rendererMutex,
            modifier = Modifier.padding(innerPadding),
            onPageChange = onPageChange,
            onStrokeAdded = { stroke -> onStrokeAdded(currentPage, stroke) },
            onTextAdded = { text -> onTextAdded(currentPage, text) },
            onEraseAt = { point -> onEraseAt(currentPage, point) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    fileName: String,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
    saveEnabled: Boolean
) {
    TopAppBar(
        title = { Text(fileName, fontWeight = FontWeight.Bold, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.Close, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onSaveClick, enabled = saveEnabled) {
                Icon(Icons.Default.Save, contentDescription = "Save PDF")
            }
            IconButton(onClick = onShareClick) {
                Icon(Icons.Default.Share, contentDescription = "Share PDF")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF6366F1),
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorBottomBar(
    editorMode: EditorMode,
    onModeChange: (EditorMode) -> Unit,
    currentColor: Color,
    onColorChange: (Color) -> Unit,
    strokeWidth: Float,
    onWidthChange: (Float) -> Unit,
    zoomLevel: Float,
    onZoomChange: (Float) -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit
) {
    Column {
        if (editorMode == EditorMode.DRAW || editorMode == EditorMode.HIGHLIGHT) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val colors = if (editorMode == EditorMode.HIGHLIGHT) {
                        listOf(
                            Color.Yellow.copy(alpha = 0.35f),
                            Color.Green.copy(alpha = 0.35f),
                            Color.Cyan.copy(alpha = 0.35f),
                            Color.Magenta.copy(alpha = 0.35f)
                        )
                    } else {
                        listOf(Color.Red, Color.Blue, Color.Green, Color.Black, Color.Yellow)
                    }
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(color, shape = RoundedCornerShape(50))
                                .pointerInput(color) { detectTapGestures { onColorChange(color) } }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(4f to "S", 12f to "M", 28f to "L").forEach { (width, label) ->
                        FilterChip(
                            selected = strokeWidth == width,
                            onClick = { onWidthChange(width) },
                            label = { Text(label, fontSize = 10.sp) }
                        )
                    }
                }
            }
        }

        BottomAppBar(containerColor = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    EditorModeButton(
                        icon = Icons.Default.Image,
                        label = "View",
                        isSelected = editorMode == EditorMode.VIEW,
                        onClick = { onModeChange(EditorMode.VIEW) }
                    )
                    EditorModeButton(
                        icon = Icons.Default.Edit,
                        label = "Draw",
                        isSelected = editorMode == EditorMode.DRAW,
                        onClick = {
                            onModeChange(EditorMode.DRAW)
                            onColorChange(Color.Red)
                            onWidthChange(8f)
                        }
                    )
                    EditorModeButton(
                        icon = Icons.Default.Edit,
                        label = "Highlight",
                        isSelected = editorMode == EditorMode.HIGHLIGHT,
                        onClick = {
                            onModeChange(EditorMode.HIGHLIGHT)
                            onColorChange(Color.Yellow.copy(alpha = 0.35f))
                            onWidthChange(28f)
                        }
                    )
                    EditorModeButton(
                        icon = Icons.Default.Description,
                        label = "Text",
                        isSelected = editorMode == EditorMode.TEXT,
                        onClick = { onModeChange(EditorMode.TEXT) }
                    )
                    EditorModeButton(
                        icon = Icons.Default.Close,
                        label = "Erase",
                        isSelected = editorMode == EditorMode.ERASE,
                        onClick = { onModeChange(EditorMode.ERASE) }
                    )
                    IconButton(onClick = onUndo, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(18.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onZoomChange(zoomLevel - 0.2f) }) {
                        Text("−", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Text(
                        "${(zoomLevel * 100).toInt()}%",
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    IconButton(onClick = { onZoomChange(zoomLevel + 0.2f) }) {
                        Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorModeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (isSelected) Color(0xFF6366F1).copy(alpha = 0.2f) else Color.Transparent
        )
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PdfViewerWithSwipe(
    renderer: PdfRenderer,
    currentPage: Int,
    pageCount: Int,
    zoomLevel: Float,
    editorMode: EditorMode,
    currentColor: Color,
    strokeWidth: Float,
    strokes: List<PdfStroke>,
    texts: List<PdfText>,
    rendererMutex: Mutex,
    modifier: Modifier = Modifier,
    onPageChange: (Int) -> Unit,
    onStrokeAdded: (PdfStroke) -> Unit,
    onTextAdded: (PdfText) -> Unit,
    onEraseAt: (Offset) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(editorMode, zoomLevel, currentPage, pageCount) {
                if (editorMode == EditorMode.VIEW && zoomLevel == 1f) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (dragAmount.y < -50 && currentPage < pageCount - 1) {
                            onPageChange(currentPage + 1)
                        } else if (dragAmount.y > 50 && currentPage > 0) {
                            onPageChange(currentPage - 1)
                        }
                    }
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF3F4F6))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Page ${currentPage + 1} / $pageCount",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                PdfPage(
                    renderer = renderer,
                    pageIndex = currentPage,
                    zoomLevel = zoomLevel,
                    editorMode = editorMode,
                    currentColor = currentColor,
                    strokeWidth = strokeWidth,
                    strokes = strokes,
                    texts = texts,
                    rendererMutex = rendererMutex,
                    onStrokeAdded = onStrokeAdded,
                    onTextAdded = onTextAdded,
                    onEraseAt = onEraseAt
                )
            }
        }
    }
}

@Composable
private fun PdfPage(
    renderer: PdfRenderer,
    pageIndex: Int,
    zoomLevel: Float,
    editorMode: EditorMode,
    currentColor: Color,
    strokeWidth: Float,
    strokes: List<PdfStroke>,
    texts: List<PdfText>,
    rendererMutex: Mutex,
    onStrokeAdded: (PdfStroke) -> Unit,
    onTextAdded: (PdfText) -> Unit,
    onEraseAt: (Offset) -> Unit
) {
    var renderInfo by remember(pageIndex, renderer) { mutableStateOf<PageRenderInfo?>(null) }
    var isLoading by remember(pageIndex, renderer) { mutableStateOf(true) }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var showTextDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var textPosition by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pageIndex, renderer) {
        isLoading = true
        renderInfo = try {
            rendererMutex.withLock {
                withContext(Dispatchers.IO) {
                    val page = renderer.openPage(pageIndex)
                    try {
                        val sourceWidth = page.width
                        val sourceHeight = page.height
                        val scale = min(
                            1f,
                            MAX_RENDER_DIMENSION.toFloat() / maxOf(sourceWidth, sourceHeight)
                        )
                        val width = (sourceWidth * scale).toInt().coerceAtLeast(1)
                        val height = (sourceHeight * scale).toInt().coerceAtLeast(1)
                        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        PageRenderInfo(bitmap)
                    } finally {
                        page.close()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        isLoading = false
    }

    DisposableEffect(renderInfo?.bitmap) {
        onDispose {
            renderInfo?.bitmap?.let { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        }
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = {
                showTextDialog = false
                textInput = ""
            },
            title = { Text("Add text") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    placeholder = { Text("Enter text") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = textInput.trim()
                        if (trimmed.isNotEmpty()) {
                            onTextAdded(
                                PdfText(
                                    text = trimmed,
                                    position = textPosition,
                                    color = currentColor
                                )
                            )
                        }
                        showTextDialog = false
                        textInput = ""
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTextDialog = false
                        textInput = ""
                    }
                ) { Text("Cancel") }
            }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(scaleX = zoomLevel, scaleY = zoomLevel),
        contentAlignment = Alignment.Center
    ) {
        val info = renderInfo
        if (isLoading) {
            CircularProgressIndicator()
        } else if (info == null) {
            Text("Error loading page", color = Color.Red)
        } else {
            val viewportWidth = constraints.maxWidth.toFloat()
            val viewportHeight = constraints.maxHeight.toFloat()
            val fitScale = min(viewportWidth / info.bitmap.width, viewportHeight / info.bitmap.height)
            val pageDisplayWidth = info.bitmap.width * fitScale
            val pageDisplayHeight = info.bitmap.height * fitScale
            val left = (viewportWidth - pageDisplayWidth) / 2f
            val top = (viewportHeight - pageDisplayHeight) / 2f

            val density = LocalDensity.current
            Image(
                bitmap = info.bitmap.asImageBitmap(),
                contentDescription = "Page $pageIndex",
                modifier = Modifier
                    .width(with(density) { pageDisplayWidth.toDp() })
                    .height(with(density) { pageDisplayHeight.toDp() }),
                contentScale = ContentScale.FillBounds
            )

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(editorMode, currentColor, strokeWidth, pageDisplayWidth, pageDisplayHeight) {
                        fun toNormalized(raw: Offset): Offset? {
                            val x = (raw.x - left) / pageDisplayWidth
                            val y = (raw.y - top) / pageDisplayHeight
                            return if (x in 0f..1f && y in 0f..1f) Offset(x, y) else null
                        }

                        when (editorMode) {
                            EditorMode.DRAW, EditorMode.HIGHLIGHT -> {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPoints = toNormalized(offset)?.let { listOf(it) }.orEmpty()
                                    },
                                    onDragEnd = {
                                        if (currentPoints.isNotEmpty()) {
                                            val widthFraction = strokeWidth / min(pageDisplayWidth, pageDisplayHeight)
                                            onStrokeAdded(
                                                PdfStroke(
                                                    points = currentPoints,
                                                    color = currentColor,
                                                    widthFraction = widthFraction.coerceAtLeast(0.001f)
                                                )
                                            )
                                        }
                                        currentPoints = emptyList()
                                    },
                                    onDragCancel = { currentPoints = emptyList() }
                                ) { change, _ ->
                                    change.consume()
                                    toNormalized(change.position)?.let { point ->
                                        currentPoints = currentPoints + point
                                    }
                                }
                            }
                            EditorMode.ERASE -> {
                                detectDragGestures(
                                    onDragStart = { offset -> toNormalized(offset)?.let(onEraseAt) },
                                    onDragEnd = {},
                                    onDragCancel = {}
                                ) { change, _ ->
                                    change.consume()
                                    toNormalized(change.position)?.let(onEraseAt)
                                }
                            }
                            EditorMode.TEXT -> {
                                detectTapGestures { offset ->
                                    toNormalized(offset)?.let { normalized ->
                                        textPosition = normalized
                                        showTextDialog = true
                                    }
                                }
                            }
                            EditorMode.VIEW -> Unit
                        }
                    }
            ) {
                strokes.forEach { drawNormalizedStroke(it) }
                if (currentPoints.isNotEmpty()) {
                    val preview = PdfStroke(
                        points = currentPoints,
                        color = currentColor,
                        widthFraction = (strokeWidth / min(size.width, size.height)).coerceAtLeast(0.001f)
                    )
                    drawNormalizedStroke(preview)
                }
                texts.forEach { text ->
                    drawContext.canvas.nativeCanvas.drawText(
                        text.text,
                        text.position.x * size.width,
                        text.position.y * size.height,
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = text.color.toAndroidColor()
                            textSize = text.sizeFraction * size.width
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Settings", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.Close, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF6366F1),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )

        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dark Mode", fontWeight = FontWeight.Bold)
                        Switch(checked = isDarkTheme, onCheckedChange = onThemeToggle)
                    }
                }
            }
            item { SettingItem(title = "Version", value = "1.1") }
            item { SettingItem(title = "About", value = "PDF Studio") }
            item {
                Text(
                    "Edited PDFs are exported as flattened copies so annotations remain visible after reopening or sharing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun SettingItem(title: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(value, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ImportSourceChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color(0xFF6366F1),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ImportSourcesSection(
    onOpenPdf: () -> Unit,
    onPhotoClick: () -> Unit
) {
    Column {
        Text(
            text = "Import From",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                ImportSourceChip(
                    icon = Icons.Default.PictureAsPdf,
                    label = "Storage",
                    onClick = onOpenPdf
                )
            }
            item {
                ImportSourceChip(
                    icon = Icons.Default.Image,
                    label = "Photos",
                    onClick = onPhotoClick
                )
            }
            item {
                ImportSourceChip(
                    icon = Icons.Default.Description,
                    label = "Files",
                    onClick = onOpenPdf
                )
            }
        }
    }
}
