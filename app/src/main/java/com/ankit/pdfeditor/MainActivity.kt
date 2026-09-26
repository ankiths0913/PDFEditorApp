package com.ankit.pdfeditor

import android.content.ClipData
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.ankit.pdfeditor.ui.theme.PDFEdittorAppTheme
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
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.time.Duration.Companion.milliseconds

private const val PREFS_NAME = "pdf_studio_prefs"
private const val RECENT_FILES_KEY = "recent_files"
private const val DARK_THEME_KEY = "dark_theme"
private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
private const val MAX_RECENT_FILES = 20
private const val MAX_UNDO_HISTORY = 50
private const val MAX_DISPLAY_RENDER_DIMENSION = 1600
private const val MAX_EXPORT_RENDER_DIMENSION = 2048
private const val DEFAULT_HIGHLIGHT_ALPHA = 0.7f

private enum class EditorMode { VIEW, DRAW, HIGHLIGHT, TEXT, ERASE }
private enum class AppScreen { HOME, EDITOR, SETTINGS }
private enum class SortOption { DATE, NAME }

private data class PdfStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<Offset>,
    val color: Color = Color.Red,
    val widthFraction: Float = 0.008f
)

private data class PdfText(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val position: Offset,
    val color: Color = Color.Black,
    val sizeFraction: Float = 0.025f
)

private data class RecentFileItem(
    val uri: String,
    val fileName: String,
    val openedAt: String,
    val fileSizeBytes: Long = -1L,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)

private sealed interface AnnotationAction {
    data class StrokeAdded(val page: Int, val stroke: PdfStroke) : AnnotationAction
    data class StrokeRemoved(val page: Int, val stroke: PdfStroke) : AnnotationAction
    data class TextAdded(val page: Int, val text: PdfText) : AnnotationAction
    data class TextRemoved(val page: Int, val text: PdfText) : AnnotationAction
}

private data class PageRenderInfo(val bitmap: Bitmap)

private fun appDocumentsDir(context: Context): File =
    File(context.filesDir, "pdf_documents").apply { mkdirs() }

private fun fileProviderUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, file)

private fun safeFileName(name: String): String {
    val normalized = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return normalized.ifBlank { "document.pdf" }
}

private fun dateString(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())

private fun resolveFileName(context: Context, uri: Uri): String {
    if (uri.scheme == "file") return File(uri.path.orEmpty()).name.ifBlank { "PDF Document" }
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else "PDF Document"
            } else {
                "PDF Document"
            }
        } ?: "PDF Document"
    } catch (_: Exception) {
        "PDF Document"
    }
}

private fun resolveFileSize(context: Context, uri: Uri): Long {
    if (uri.scheme == "file") return File(uri.path.orEmpty()).length().takeIf { it > 0L } ?: -1L
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else -1L
            } else {
                -1L
            }
        } ?: -1L
    } catch (_: Exception) {
        -1L
    }
}

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes < 0L) return "Size unknown"
    if (sizeBytes < 1024L) return "$sizeBytes B"
    val kb = sizeBytes / 1024.0
    if (kb < 1024.0) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

private fun persistRecentFiles(context: Context, items: List<RecentFileItem>) {
    val array = JSONArray()
    items.take(MAX_RECENT_FILES).forEach { item ->
        array.put(
            JSONObject().apply {
                put("uri", item.uri)
                put("fileName", item.fileName)
                put("openedAt", item.openedAt)
                put("fileSizeBytes", item.fileSizeBytes)
                put("timestamp", item.timestamp)
                put("isFavorite", item.isFavorite)
            }
        )
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit {
            putString(RECENT_FILES_KEY, array.toString())
        }
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
                        fileSizeBytes = obj.optLong("fileSizeBytes", -1L),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isFavorite = obj.optBoolean("isFavorite", false)
                    )
                )
            }
        }.sortedByDescending { it.timestamp }.take(MAX_RECENT_FILES)
    } catch (_: Exception) {
        emptyList()
    }
}

private fun convertImageToPdfOnDevice(context: Context, imageUri: Uri): File? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (largest / sample > MAX_EXPORT_RENDER_DIMENSION * 2) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null

        val finalBitmap = if (maxOf(bitmap.width, bitmap.height) > MAX_EXPORT_RENDER_DIMENSION) {
            val ratio = MAX_EXPORT_RENDER_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
            val targetWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
            val targetHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)

            if (targetWidth == bitmap.width && targetHeight == bitmap.height) {
                bitmap
            } else {
                val scaledBitmap = bitmap.scale(targetWidth, targetHeight)
                if (scaledBitmap !== bitmap && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
                scaledBitmap
            }
        } else {
            bitmap
        }

        val outputFile = File(
            appDocumentsDir(context),
            "Converted_${System.currentTimeMillis()}.pdf"
        )
        val pdfDocument = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(
                finalBitmap.width,
                finalBitmap.height,
                1
            ).create()
            val page = pdfDocument.startPage(pageInfo)
            page.canvas.drawBitmap(finalBitmap, 0f, 0f, null)
            pdfDocument.finishPage(page)
            File(outputFile.parentFile, outputFile.name).outputStream().use { pdfDocument.writeTo(it) }
        } finally {
            pdfDocument.close()
            if (!finalBitmap.isRecycled) finalBitmap.recycle()
        }
        outputFile
    } catch (_: Exception) {
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
    if (stroke.points.size == 1) return hypot(point.x - stroke.points[0].x, point.y - stroke.points[0].y)
    return stroke.points.zipWithNext().minOf { (a, b) -> distancePointToSegment(point, a, b) }
}

private fun Color.toAndroidColor(): Int = android.graphics.Color.argb(
    (alpha.coerceIn(0f, 1f) * 255).toInt(),
    (red.coerceIn(0f, 1f) * 255).toInt(),
    (green.coerceIn(0f, 1f) * 255).toInt(),
    (blue.coerceIn(0f, 1f) * 255).toInt()
)

private fun buildComposePath(points: List<Offset>): Path = Path().apply {
    if (points.isNotEmpty()) {
        moveTo(points.first().x, points.first().y)
        for (point in points.drop(1)) lineTo(point.x, point.y)
    }
}

private fun DrawScope.drawNormalizedStroke(stroke: PdfStroke) {
    if (stroke.points.isEmpty()) return
    val minDimension = minOf(size.width, size.height)
    val screenPoints = stroke.points.map { Offset(it.x * size.width, it.y * size.height) }
    drawPath(
        path = buildComposePath(screenPoints),
        color = stroke.color,
        style = Stroke(width = stroke.widthFraction * minDimension, cap = StrokeCap.Round)
    )
}

private fun bakeAnnotations(
    canvas: android.graphics.Canvas,
    bitmap: Bitmap,
    strokes: List<PdfStroke>,
    texts: List<PdfText>
) {
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val pageMin = minOf(bitmap.width, bitmap.height).toFloat()

    strokes.forEach { stroke ->
        if (stroke.points.isEmpty()) return@forEach
        strokePaint.color = stroke.color.toAndroidColor()
        strokePaint.strokeWidth = stroke.widthFraction * pageMin
        val path = AndroidPath()
        val first = stroke.points.first()
        path.moveTo(first.x * bitmap.width, first.y * bitmap.height)
        stroke.points.drop(1).forEach { point ->
            path.lineTo(point.x * bitmap.width, point.y * bitmap.height)
        }
        canvas.drawPath(path, strokePaint)
    }

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    texts.forEach { text ->
        textPaint.color = text.color.toAndroidColor()
        textPaint.textSize = text.sizeFraction * pageMin
        canvas.drawText(
            text.text,
            text.position.x * bitmap.width,
            text.position.y * bitmap.height,
            textPaint
        )
    }
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

private fun sharePdf(context: Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("PDF", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF via"))
    } catch (_: Exception) {
        throw IOException("No compatible app is available to share this PDF")
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

    BackHandler(enabled = currentScreen == AppScreen.EDITOR) {
        requestBack()
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
        when (currentScreen) {
            AppScreen.HOME -> HomeScreen(
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

            AppScreen.EDITOR -> {
                val activeRenderer = renderer
                val activeUri = pdfUri
                if (activeRenderer != null && activeUri != null && pageCount > 0) {
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
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        TopBarHome(onSettingsTap)
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { WelcomeSection(onOpenPdf) }
            item { ImportSourcesSection(onOpenPdf, onPhotoClick) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search recent PDFs...") },
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
            } else if (recentFiles.isEmpty()) {
                item {
                    Text(
                        "No recent PDF files found. Open a file to get started!",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                }
            } else {
                item {
                    Text(
                        "No recent PDFs match your search.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                }
            }
            item { QuickActionsSection(onOpenPdf) }
        }
    }
}

@Composable
private fun TopBarHome(onSettingsTap: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("PDF Studio", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.White)
            Spacer(Modifier.height(16.dp))
            Text("PDF Reader & Editor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("Read, annotate, highlight & organize all your PDFs easily.", textAlign = TextAlign.Center, color = Color.White.copy(alpha = 0.9f))
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onOpenPdf,
                modifier = Modifier.fillMaxWidth().height(48.dp),
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
        onClick = onTap,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(36.dp), tint = Color(0xFFEF4444))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.fileName, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${file.openedAt} • ${formatFileSize(file.fileSizeBytes)}", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    Icons.Default.Star,
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
        Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionCard(Icons.Default.Add, "Open PDF", Modifier.weight(1f), onOpenPdf)
            QuickActionCard(Icons.Default.Edit, "Edit PDF", Modifier.weight(1f), onOpenPdf)
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
        onClick = onClick,
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color(0xFF6366F1))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EditorScreen(
    renderer: PdfRenderer,
    pdfUri: Uri,
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
    hasUnsavedChanges: Boolean,
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
                fileName = resolveFileName(LocalContext.current, pdfUri),
                onShareClick = { onShareClick(pdfUri) },
                onSaveClick = onSaveClick,
                onBackClick = onBackClick,
                saveEnabled = !isBusy,
                shareEnabled = !isBusy,
                hasUnsavedChanges = hasUnsavedChanges
            )
        },
        bottomBar = {
            EditorBottomBar(
                editorMode = editorMode,
                onModeChange = onModeChange,
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
            onStrokeAdded = { onStrokeAdded(currentPage, it) },
            onTextAdded = { onTextAdded(currentPage, it) },
            onEraseAt = { onEraseAt(currentPage, it) }
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
    saveEnabled: Boolean,
    shareEnabled: Boolean,
    hasUnsavedChanges: Boolean
) {
    TopAppBar(
        title = {
            Column {
                Text(fileName, fontWeight = FontWeight.Bold, maxLines = 1)
                if (hasUnsavedChanges) Text("Unsaved changes", style = MaterialTheme.typography.labelSmall)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) { Icon(Icons.Default.Close, contentDescription = "Back") }
        },
        actions = {
            IconButton(onClick = onSaveClick, enabled = saveEnabled) { Icon(Icons.Default.Save, contentDescription = "Save PDF") }
            IconButton(onClick = onShareClick, enabled = shareEnabled) { Icon(Icons.Default.Share, contentDescription = "Share PDF") }
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
    onColorChange: (Color) -> Unit,
    strokeWidth: Float,
    onWidthChange: (Float) -> Unit,
    zoomLevel: Float,
    onZoomChange: (Float) -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit
) {
    Column {
        if (editorMode == EditorMode.DRAW || editorMode == EditorMode.HIGHLIGHT || editorMode == EditorMode.TEXT) {
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val colors = if (editorMode == EditorMode.HIGHLIGHT) {
                        listOf(
                            Color.Yellow.copy(alpha = DEFAULT_HIGHLIGHT_ALPHA),
                            Color.Green.copy(alpha = DEFAULT_HIGHLIGHT_ALPHA),
                            Color.Cyan.copy(alpha = DEFAULT_HIGHLIGHT_ALPHA),
                            Color.Magenta.copy(alpha = DEFAULT_HIGHLIGHT_ALPHA)
                        )
                    } else {
                        listOf(Color.Red, Color.Blue, Color.Green, Color.Black, Color.Yellow)
                    }
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(color, RoundedCornerShape(50))
                                .pointerInput(color) { detectTapGestures { onColorChange(color) } }
                        )
                    }
                }
                if (editorMode != EditorMode.TEXT) {
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
        }

        BottomAppBar(containerColor = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    EditorModeButton(Icons.Default.Image, "View", editorMode == EditorMode.VIEW) { onModeChange(EditorMode.VIEW) }
                    EditorModeButton(Icons.Default.Edit, "Draw", editorMode == EditorMode.DRAW) {
                        onModeChange(EditorMode.DRAW); onColorChange(Color.Red); onWidthChange(8f)
                    }
                    EditorModeButton(Icons.Default.Edit, "Highlight", editorMode == EditorMode.HIGHLIGHT) {
                        onModeChange(EditorMode.HIGHLIGHT); onColorChange(Color.Yellow.copy(alpha = DEFAULT_HIGHLIGHT_ALPHA)); onWidthChange(28f)
                    }
                    EditorModeButton(Icons.Default.Description, "Text", editorMode == EditorMode.TEXT) { onModeChange(EditorMode.TEXT) }
                    EditorModeButton(Icons.Default.Delete, "Erase", editorMode == EditorMode.ERASE) { onModeChange(EditorMode.ERASE) }
                    IconButton(onClick = onUndo, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(18.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onZoomChange(zoomLevel - 0.2f) }, enabled = zoomLevel > 1f) { Text("−", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    Text("${(zoomLevel * 100).toInt()}%", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 2.dp))
                    IconButton(onClick = { onZoomChange(zoomLevel + 0.2f) }, enabled = zoomLevel < 3f) { Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
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
        modifier = modifier.fillMaxSize().background(Color.White)
            .pointerInput(editorMode, zoomLevel, currentPage, pageCount) {
                if (editorMode == EditorMode.VIEW && zoomLevel == 1f) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        when {
                            dragAmount < -80f && currentPage < pageCount - 1 -> onPageChange(currentPage + 1)
                            dragAmount > 80f && currentPage > 0 -> onPageChange(currentPage - 1)
                        }
                    }
                }
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxWidth().background(Color(0xFFF3F4F6)).padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Page ${currentPage + 1} / $pageCount", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = Color.Black)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
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
    var renderError by remember(pageIndex, renderer) { mutableStateOf<String?>(null) }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var showTextDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var textPosition by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pageIndex, renderer) {
        renderInfo = null
        renderError = null

        try {
            val result = rendererMutex.withLock {
                withContext(Dispatchers.IO) {
                    renderer.openPage(pageIndex).use { page ->
                        val sourceWidth = page.width
                        val sourceHeight = page.height
                        val scale = minOf(
                            1f,
                            MAX_DISPLAY_RENDER_DIMENSION.toFloat() /
                                    maxOf(sourceWidth, sourceHeight).toFloat()
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
                            bitmap.prepareToDraw()

                            val readyBitmap = bitmap
                            bitmap = null
                            PageRenderInfo(readyBitmap)
                        } finally {
                            bitmap?.let {
                                if (!it.isRecycled) {
                                    it.recycle()
                                }
                            }
                        }
                    }
                }
            }

            if (!isActive) {
                result.bitmap.let {
                    if (!it.isRecycled) {
                        it.recycle()
                    }
                }
                return@LaunchedEffect
            }

            renderInfo = result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            renderError = e.message ?: "Unable to render this page"
        }
    }



    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false; textInput = "" },
            title = { Text("Add text") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter text") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = textInput.trim()
                    if (text.isNotEmpty()) {
                        onTextAdded(PdfText(text = text, position = textPosition, color = currentColor))
                    }
                    showTextDialog = false
                    textInput = ""
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showTextDialog = false; textInput = "" }) { Text("Cancel") }
            }
        )
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color(0xFFE5E7EB)).graphicsLayer(scaleX = zoomLevel, scaleY = zoomLevel),
        contentAlignment = Alignment.Center
    ) {
        val info = renderInfo
        when {
            info == null && renderError == null -> CircularProgressIndicator()
            info == null -> Text(renderError ?: "Error loading page", color = Color.Red, modifier = Modifier.padding(24.dp), textAlign = TextAlign.Center)
            else -> {
                val viewportWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val viewportHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val fitScale = minOf(viewportWidth / info.bitmap.width, viewportHeight / info.bitmap.height)
                val pageWidthPx = (info.bitmap.width * fitScale)
                    .toInt()
                    .coerceAtLeast(1)
                val pageHeightPx = (info.bitmap.height * fitScale)
                    .toInt()
                    .coerceAtLeast(1)
                val density = LocalDensity.current
                val pageWidthDp = (pageWidthPx / density.density).dp
                val pageHeightDp = (pageHeightPx / density.density).dp

                Box(
                    modifier = Modifier
                        .width(pageWidthDp)
                        .height(pageHeightDp)
                ) {
                    Image(
                        bitmap = info.bitmap.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(editorMode, currentColor, strokeWidth) {
                                when (editorMode) {
                                    EditorMode.DRAW, EditorMode.HIGHLIGHT -> {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                currentPoints = listOf(
                                                    Offset(
                                                        (offset.x / size.width).coerceIn(0f, 1f),
                                                        (offset.y / size.height).coerceIn(0f, 1f)
                                                    )
                                                )
                                            },
                                            onDragEnd = {
                                                if (currentPoints.isNotEmpty()) {
                                                    val minDimension = minOf(size.width, size.height)
                                                        .coerceAtLeast(1)
                                                        .toFloat()

                                                    val widthFraction = strokeWidth / minDimension
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
                                            currentPoints = currentPoints + Offset(
                                                (change.position.x / size.width).coerceIn(0f, 1f),
                                                (change.position.y / size.height).coerceIn(0f, 1f)
                                            )
                                        }
                                    }
                                    EditorMode.ERASE -> {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                onEraseAt(
                                                    Offset(
                                                        (offset.x / size.width).coerceIn(0f, 1f),
                                                        (offset.y / size.height).coerceIn(0f, 1f)
                                                    )
                                                )
                                            },
                                            onDragEnd = {},
                                            onDragCancel = {}
                                        ) { change, _ ->
                                            change.consume()
                                            onEraseAt(
                                                Offset(
                                                    (change.position.x / size.width).coerceIn(0f, 1f),
                                                    (change.position.y / size.height).coerceIn(0f, 1f)
                                                )
                                            )
                                        }
                                    }
                                    EditorMode.TEXT -> {
                                        detectTapGestures { offset ->
                                            textPosition = Offset(
                                                (offset.x / size.width).coerceIn(0f, 1f),
                                                (offset.y / size.height).coerceIn(0f, 1f)
                                            )
                                            showTextDialog = true
                                        }
                                    }
                                    EditorMode.VIEW -> Unit
                                }
                            }
                    ) {
                        strokes.forEach { drawNormalizedStroke(it) }
                        if (currentPoints.isNotEmpty()) {
                            drawNormalizedStroke(
                                PdfStroke(
                                    points = currentPoints,
                                    color = currentColor,
                                    widthFraction = (strokeWidth / maxOf(1f, minOf(size.width, size.height)))
                                        .coerceAtLeast(0.001f)
                                )
                            )
                        }
                        texts.forEach { text ->
                            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = text.color.toAndroidColor()
                                textSize = text.sizeFraction * minOf(size.width, size.height)
                                typeface = Typeface.DEFAULT
                            }
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawText(
                                    text.text,
                                    text.position.x * size.width,
                                    text.position.y * size.height,
                                    paint
                                )
                            }
                        }
                    }
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
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Settings", fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.Close, contentDescription = "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF6366F1),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )
        LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dark Mode", fontWeight = FontWeight.Bold)
                        Switch(checked = isDarkTheme, onCheckedChange = onThemeToggle)
                    }
                }
            }
            item { SettingItem("Version", "1.2") }
            item { SettingItem("About", "PDF Studio") }
            item {
                Text(
                    "Edited PDFs are exported as flattened copies so annotations remain visible after reopening or sharing.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SettingItem(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 12.sp)
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
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ImportSourcesSection(onOpenPdf: () -> Unit, onPhotoClick: () -> Unit) {
    Column {
        Text("Import From", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ImportSourceChip(Icons.Default.PictureAsPdf, "Storage", onOpenPdf) }
            item { ImportSourceChip(Icons.Default.Image, "Photos", onPhotoClick) }
            item { ImportSourceChip(Icons.Default.Description, "Files", onOpenPdf) }
        }
    }
}