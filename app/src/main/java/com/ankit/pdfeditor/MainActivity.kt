package com.ankit.pdfeditor

// Android Core & OS
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast

// Activity & Contracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

// Compose Foundation & Layout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField

// Compose Material 3 & Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*

// Compose Runtime & State
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList

// Compose UI & Graphics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Coroutines & System Utilities
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

// Theme
import com.ankit.pdfeditor.ui.theme.PDFEdittorAppTheme

/**
 * PDF Viewer Main Activity
 * Implements PDF loading, paginated viewing, navigation, and zoom.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PDFEdittorAppTheme {
                PdfViewerApp()
            }
        }
    }
}

// --- DATA CLASSES & ENUMS ---
enum class EditorMode { VIEW, DRAW, TEXT }

data class PdfStroke(
    val path: Path,
    val color: Color = Color.Red,
    val strokeWidth: Float = 8f
)

data class PdfTextAnnotation(
    val id: String = java.util.UUID.randomUUID().toString(),
    var text: String,
    var x: Float,
    var y: Float,
    var color: Color = Color.Black,
    var fontSize: Float = 18f
)

data class RecentFileItem(val uri: String, val fileName: String, val openedAt: String)

// --- UTILITY FUNCTIONS ---
fun resolveFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = cursor.getString(index)
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) result = result?.substring(cut + 1)
    }
    return result ?: "Unknown"
}

suspend fun exportAndSavePdf(
    context: Context,
    sourceUri: Uri,
    targetUri: Uri,
    pageCount: Int,
    pageStrokes: Map<Int, List<PdfStroke>>,
    pageTexts: Map<Int, List<PdfTextAnnotation>>
) = withContext(Dispatchers.IO) {
    val pfd = context.contentResolver.openFileDescriptor(sourceUri, "r") ?: return@withContext
    val renderer = PdfRenderer(pfd)
    val pdfDocument = android.graphics.pdf.PdfDocument()

    try {
        for (i in 0 until pageCount) {
            val page = renderer.openPage(i)
            val pageWidth = page.width
            val pageHeight = page.height

            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, i + 1).create()
            val pdfPage = pdfDocument.startPage(pageInfo)
            val canvas = pdfPage.canvas

            val bmp = createBitmap(pageWidth * 2, pageHeight * 2, Bitmap.Config.ARGB_8888)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            val srcRect = android.graphics.Rect(0, 0, bmp.width, bmp.height)
            val destRect = android.graphics.Rect(0, 0, pageWidth, pageHeight)
            canvas.drawBitmap(bmp, srcRect, destRect, null)
            bmp.recycle()

            val strokes = pageStrokes[i]
            if (!strokes.isNullOrEmpty()) {
                strokes.forEach { stroke ->
                    val paint = android.graphics.Paint().apply {
                        color = stroke.color.toArgb()
                        strokeWidth = stroke.strokeWidth
                        style = android.graphics.Paint.Style.STROKE
                        isAntiAlias = true
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                    }
                    canvas.drawPath(stroke.path.asAndroidPath(), paint)
                }
            }

            val textAnnotations = pageTexts[i]
            if (!textAnnotations.isNullOrEmpty()) {
                textAnnotations.forEach { annotation ->
                    val textPaint = android.graphics.Paint().apply {
                        color = annotation.color.toArgb()
                        textSize = annotation.fontSize * 2f
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    canvas.drawText(annotation.text, annotation.x, annotation.y + annotation.fontSize, textPaint)
                }
            }

            pdfDocument.finishPage(pdfPage)
        }

        context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        pdfDocument.close()
        renderer.close()
        pfd.close()
    }
}

// --- COMPOSABLES ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    var editorMode by remember { mutableStateOf(EditorMode.VIEW) }
    var currentColor by remember { mutableStateOf(Color.Red) }
    var strokeWidth by remember { mutableFloatStateOf(8f) }

    val pageStrokes = remember { mutableStateMapOf<Int, SnapshotStateList<PdfStroke>>() }
    val pageTexts = remember { mutableStateMapOf<Int, SnapshotStateList<PdfTextAnnotation>>() }
    val recentFiles = remember { mutableStateListOf<RecentFileItem>() }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri: Uri? ->
        if (targetUri != null && pdfUri != null) {
            isSaving = true
            scope.launch {
                exportAndSavePdf(
                    context = context,
                    sourceUri = pdfUri!!,
                    targetUri = targetUri,
                    pageCount = pageCount,
                    pageStrokes = pageStrokes,
                    pageTexts = pageTexts
                )
                isSaving = false
                Toast.makeText(context, "PDF saved successfully!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = resolveFileName(context, it)
            val openedAt = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date())
            recentFiles.removeAll { item -> item.uri == it.toString() }
            recentFiles.add(0, RecentFileItem(uri = it.toString(), fileName = name, openedAt = openedAt))
            pdfUri = it
            errorMessage = null
            zoomLevel = 1f
            editorMode = EditorMode.VIEW
            pageStrokes.clear()
            pageTexts.clear()
        }
    }

    val renderer = remember(pdfUri) {
        pdfUri?.let { uri ->
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    PdfRenderer(pfd).also { pageCount = it.pageCount }
                } else {
                    errorMessage = "Could not open file."
                    null
                }
            } catch (e: Exception) {
                errorMessage = "Error: ${e.localizedMessage}"
                null
            }
        }
    }

    DisposableEffect(renderer) {
        onDispose { renderer?.close() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("PDF Studio", fontWeight = FontWeight.Bold) },
                actions = {
                    if (pdfUri != null) {
                        IconButton(
                            onClick = { savePdfLauncher.launch("edited_document.pdf") },
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, contentDescription = "Save PDF")
                            }
                        }
                    }
                    IconButton(onClick = { filePicker.launch("application/pdf") }) {
                        Icon(Icons.Default.Add, contentDescription = "Open PDF")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            if (renderer != null) {
                Column {
                    if (editorMode == EditorMode.DRAW || editorMode == EditorMode.TEXT) {
                        DrawingPalette(
                            currentColor = currentColor,
                            onColorSelected = { currentColor = it },
                            strokeWidth = strokeWidth,
                            onWidthSelected = { strokeWidth = it }
                        )
                    }

                    PdfBottomControls(
                        zoomLevel = zoomLevel,
                        onZoomChange = { zoomLevel = it },
                        editorMode = editorMode,
                        onModeChange = { editorMode = it }
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(contentAlignment = Alignment.Center) {
                when {
                    errorMessage != null -> ErrorDisplay(errorMessage!!)
                    pdfUri == null -> EmptyStateDisplay(recentFiles, onOpenClick = { filePicker.launch("application/pdf") })
                    renderer != null -> {
                        PdfPager(
                            renderer = renderer,
                            pageCount = pageCount,
                            zoomLevel = zoomLevel,
                            editorMode = editorMode,
                            currentColor = currentColor,
                            strokeWidth = strokeWidth,
                            pageStrokes = pageStrokes,
                            pageTexts = pageTexts
                        )
                    }
                    else -> CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun DrawingPalette(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    strokeWidth: Float,
    onWidthSelected: (Float) -> Unit
) {
    val colors = listOf(
        Color.Red,
        Color.Blue,
        Color.Green,
        Color.Black,
        Color(0xFFFFEB3B).copy(alpha = 0.5f)
    )
    val widths = listOf(4f to "Thin", 10f to "Medium", 24f to "Thick")

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(color, shape = RoundedCornerShape(50))
                            .border(
                                width = if (currentColor == color) 3.dp else 1.dp,
                                color = if (currentColor == color) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = RoundedCornerShape(50)
                            )
                            .pointerInput(Unit) {
                                detectTapGestures { onColorSelected(color) }
                            }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                widths.forEach { (width, label) ->
                    FilterChip(
                        selected = strokeWidth == width,
                        onClick = { onWidthSelected(width) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }
        }
    }
}

@Composable
fun PdfPager(
    renderer: PdfRenderer,
    pageCount: Int,
    zoomLevel: Float,
    editorMode: EditorMode,
    currentColor: Color,
    strokeWidth: Float,
    pageStrokes: MutableMap<Int, SnapshotStateList<PdfStroke>>,
    pageTexts: MutableMap<Int, SnapshotStateList<PdfTextAnnotation>>
) {
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Page ${listState.firstVisibleItemIndex + 1} of $pageCount",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxSize().clipToBounds(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                userScrollEnabled = editorMode == EditorMode.VIEW && zoomLevel <= 1.05f
            ) {
                items(count = pageCount, key = { it }) { pageIndex ->
                    val strokes = pageStrokes.getOrPut(pageIndex) { mutableStateListOf() }
                    val texts = pageTexts.getOrPut(pageIndex) { mutableStateListOf() }

                    PdfPage(
                        renderer = renderer,
                        pageIndex = pageIndex,
                        zoomLevel = zoomLevel,
                        editorMode = editorMode,
                        currentColor = currentColor,
                        strokeWidth = strokeWidth,
                        strokes = strokes,
                        textAnnotations = texts
                    )
                }
            }
        }

        if (pageCount > 1) {
            VerticalFastScroller(
                listState = listState,
                pageCount = pageCount,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp, top = 50.dp, bottom = 10.dp)
            )
        }
    }
}

@Composable
fun PdfPage(
    renderer: PdfRenderer,
    pageIndex: Int,
    zoomLevel: Float,
    editorMode: EditorMode,
    currentColor: Color,
    strokeWidth: Float,
    strokes: SnapshotStateList<PdfStroke>,
    textAnnotations: SnapshotStateList<PdfTextAnnotation>
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var gestureScale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var currentPath by remember { mutableStateOf<Path?>(null) }

    LaunchedEffect(zoomLevel) {
        if (zoomLevel == 1f) {
            gestureScale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }

    LaunchedEffect(pageIndex) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val page = renderer.openPage(pageIndex)
                val bmp = createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap = bmp
                page.close()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    val totalScale = (zoomLevel * gestureScale).coerceIn(0.8f, 5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(550.dp)
            .background(Color.White)
            .then(
                when (editorMode) {
                    EditorMode.DRAW -> Modifier.pointerInput(currentColor, strokeWidth) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPath = Path().apply { moveTo(offset.x, offset.y) }
                            },
                            onDragEnd = {
                                currentPath?.let { path ->
                                    strokes.add(PdfStroke(path = path, color = currentColor, strokeWidth = strokeWidth))
                                }
                                currentPath = null
                            },
                            onDragCancel = { currentPath = null }
                        ) { change, _ ->
                            change.consume()
                            currentPath?.lineTo(change.position.x, change.position.y)
                        }
                    }
                    EditorMode.TEXT -> Modifier.pointerInput(Unit) {
                        detectTapGestures { tapOffset ->
                            textAnnotations.add(
                                PdfTextAnnotation(
                                    text = "Tap to edit",
                                    x = tapOffset.x,
                                    y = tapOffset.y,
                                    color = currentColor
                                )
                            )
                        }
                    }
                    EditorMode.VIEW -> if (totalScale > 1.05f) {
                        Modifier.pointerInput(pageIndex) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                gestureScale = (gestureScale * zoom).coerceIn(0.8f, 4f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                    } else Modifier
                }
            )
            .graphicsLayer(
                scaleX = totalScale,
                scaleY = totalScale,
                translationX = offsetX,
                translationY = offsetY
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        } else {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Page $pageIndex",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } ?: Text("Error rendering page", color = Color.Red)

            Canvas(modifier = Modifier.fillMaxSize()) {
                strokes.forEach { stroke ->
                    drawPath(
                        path = stroke.path,
                        color = stroke.color,
                        style = DrawStroke(width = stroke.strokeWidth)
                    )
                }
                currentPath?.let { path ->
                    drawPath(
                        path = path,
                        color = currentColor,
                        style = DrawStroke(width = strokeWidth)
                    )
                }
            }

            textAnnotations.forEach { annotation ->
                var textValue by remember { mutableStateOf(annotation.text) }

                BasicTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        annotation.text = it
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = annotation.color,
                        fontSize = annotation.fontSize.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier
                        .offset { IntOffset(annotation.x.toInt(), annotation.y.toInt()) }
                        .background(
                            color = Color.White.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun VerticalFastScroller(
    listState: LazyListState,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var localDragY by remember { mutableFloatStateOf(-1f) }
    var lastTargetPage by remember { mutableIntStateOf(-1) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .width(48.dp)
            .fillMaxHeight()
    ) {
        val heightPx = constraints.maxHeight.toFloat()
        val thumbHeightPx = with(density) { 48.dp.toPx() }
        val maxTravelPx = (heightPx - thumbHeightPx).coerceAtLeast(1f)

        val currentFraction = if (pageCount > 1) {
            listState.firstVisibleItemIndex.toFloat() / (pageCount - 1)
        } else 0f

        val thumbOffsetY = if (isDragging && localDragY >= 0f) {
            localDragY.coerceIn(0f, maxTravelPx)
        } else {
            (maxTravelPx * currentFraction).coerceIn(0f, maxTravelPx)
        }

        val targetPage = if (maxTravelPx > 0f) {
            ((thumbOffsetY / maxTravelPx) * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1)
        } else 0

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(2.dp)
                )
        )

        if (isDragging) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 28.dp)
                    .offset { IntOffset(0, (thumbOffsetY + 4).toInt()) }
            ) {
                Text(
                    text = "Page ${targetPage + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(0, thumbOffsetY.toInt()) }
                .align(Alignment.TopEnd)
                .size(width = 16.dp, height = 48.dp)
                .background(
                    color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                )
                .pointerInput(pageCount, heightPx) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            localDragY = thumbOffsetY + offset.y
                        },
                        onDragEnd = {
                            isDragging = false
                            localDragY = -1f
                            lastTargetPage = -1
                        },
                        onDragCancel = {
                            isDragging = false
                            localDragY = -1f
                            lastTargetPage = -1
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        val newY = (localDragY + dragAmount.y).coerceIn(0f, maxTravelPx)
                        localDragY = newY

                        val newFraction = newY / maxTravelPx
                        val newTargetPage = (newFraction * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1)

                        if (newTargetPage != lastTargetPage) {
                            lastTargetPage = newTargetPage
                            scope.launch {
                                listState.scrollToItem(newTargetPage)
                            }
                        }
                    }
                }
        )
    }
}

@Composable
fun PdfBottomControls(
    zoomLevel: Float,
    onZoomChange: (Float) -> Unit,
    editorMode: EditorMode,
    onModeChange: (EditorMode) -> Unit
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { onModeChange(EditorMode.VIEW) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (editorMode == EditorMode.VIEW) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (editorMode == EditorMode.VIEW) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.Description, contentDescription = "View")
                }

                IconButton(
                    onClick = { onModeChange(EditorMode.DRAW) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (editorMode == EditorMode.DRAW) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (editorMode == EditorMode.DRAW) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Draw")
                }

                IconButton(
                    onClick = { onModeChange(EditorMode.TEXT) },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (editorMode == EditorMode.TEXT) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (editorMode == EditorMode.TEXT) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Text")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onZoomChange((zoomLevel - 0.25f).coerceAtLeast(1f)) }) {
                    Text("-", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Text("${(zoomLevel * 100).toInt()}%", modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = { onZoomChange((zoomLevel + 0.25f).coerceAtMost(3f)) }) {
                    Text("+", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
fun ErrorDisplay(errorMessage: String) {
    Text(text = errorMessage, color = Color.Red)
}

@Composable
fun EmptyStateDisplay(recentFiles: List<RecentFileItem>, onOpenClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No PDF loaded. Click + to open.", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenClick) {
            Text("Open PDF")
        }
    }
}