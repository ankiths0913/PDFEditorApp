package com.ankit.pdfeditor.ui.editor

import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.ankit.pdfeditor.model.*
import com.ankit.pdfeditor.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
internal fun PdfViewerWithSwipe(
    renderer: PdfRenderer,
    currentPage: Int,
    pageCount: Int,
    zoomLevel: Float,
    editorMode: EditorMode,
    currentColor: Color,
    strokeWidth: Float,
    pageStrokesMap: Map<Int, List<PdfStroke>>,
    pageTextsMap: Map<Int, List<PdfText>>,
    pageShapesMap: Map<Int, List<PdfShape>>,
    pageSignaturesMap: Map<Int, List<PdfSignature>>,
    activeSignaturePreset: List<List<Offset>>?,
    pageColorFilter: PageColorFilter = PageColorFilter.NORMAL,
    rendererMutex: Mutex,
    modifier: Modifier = Modifier,
    onPageChange: (Int) -> Unit,
    onStrokeAdded: (Int, PdfStroke) -> Unit,
    onTextAdded: (Int, PdfText) -> Unit,
    onShapeAdded: (Int, PdfShape) -> Unit,
    onSignatureAdded: (Int, PdfSignature) -> Unit,
    onEraseAt: (Int, Offset) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            if (index in 0 until pageCount && index != currentPage) {
                onPageChange(index)
            }
        }
    }

    LaunchedEffect(currentPage) {
        if (currentPage in 0 until pageCount && listState.firstVisibleItemIndex != currentPage) {
            listState.animateScrollToItem(currentPage)
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(zoomLevel) {
        scale = zoomLevel
        if (scale == 1f) panOffset = Offset.Zero
    }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        if (scale > 1f) {
            panOffset += offsetChange
        } else {
            panOffset = Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .transformable(state = transformState)
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${currentPage + 1} / $pageCount",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = panOffset.x,
                        translationY = panOffset.y
                    )
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(pageCount) { pageIdx ->
                        PdfPage(
                            renderer = renderer,
                            pageIndex = pageIdx,
                            editorMode = editorMode,
                            currentColor = currentColor,
                            strokeWidth = strokeWidth,
                            strokes = pageStrokesMap[pageIdx].orEmpty(),
                            texts = pageTextsMap[pageIdx].orEmpty(),
                            shapes = pageShapesMap[pageIdx].orEmpty(),
                            signatures = pageSignaturesMap[pageIdx].orEmpty(),
                            activeSignaturePreset = activeSignaturePreset,
                            pageColorFilter = pageColorFilter,
                            rendererMutex = rendererMutex,
                            onStrokeAdded = { onStrokeAdded(pageIdx, it) },
                            onTextAdded = { onTextAdded(pageIdx, it) },
                            onShapeAdded = { onShapeAdded(pageIdx, it) },
                            onSignatureAdded = { onSignatureAdded(pageIdx, it) },
                            onEraseAt = { onEraseAt(pageIdx, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(
    renderer: PdfRenderer,
    pageIndex: Int,
    editorMode: EditorMode,
    currentColor: Color,
    strokeWidth: Float,
    strokes: List<PdfStroke>,
    texts: List<PdfText>,
    shapes: List<PdfShape>,
    signatures: List<PdfSignature>,
    activeSignaturePreset: List<List<Offset>>?,
    pageColorFilter: PageColorFilter,
    rendererMutex: Mutex,
    onStrokeAdded: (PdfStroke) -> Unit,
    onTextAdded: (PdfText) -> Unit,
    onShapeAdded: (PdfShape) -> Unit,
    onSignatureAdded: (PdfSignature) -> Unit,
    onEraseAt: (Offset) -> Unit
) {
    val context = LocalContext.current
    var renderInfo by remember(pageIndex, renderer) { mutableStateOf<PageRenderInfo?>(null) }
    var renderError by remember(pageIndex, renderer) { mutableStateOf<String?>(null) }

    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var shapeStart by remember { mutableStateOf<Offset?>(null) }
    var shapeEnd by remember { mutableStateOf<Offset?>(null) }

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
                        val scale = minOf(
                            1f,
                            MAX_DISPLAY_RENDER_DIMENSION.toFloat() / maxOf(page.width, page.height).toFloat()
                        )
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        var bitmap: Bitmap? = null
                        try {
                            bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                                eraseColor(android.graphics.Color.WHITE)
                            }
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap.prepareToDraw()
                            val ready = bitmap
                            bitmap = null
                            PageRenderInfo(ready)
                        } finally {
                            bitmap?.let { if (!it.isRecycled) it.recycle() }
                        }
                    }
                }
            }
            if (isActive) renderInfo = result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            renderError = e.message ?: "Unable to render page ${pageIndex + 1}"
        }
    }

    val latinTypeface = remember(context) { Typeface.createFromAsset(context.assets, "NotoSans-Regular.ttf") }
    val devanagariTypeface = remember(context) { Typeface.createFromAsset(context.assets, "NotoSansDevanagari-Regular.ttf") }

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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val info = renderInfo
        when {
            info == null && renderError == null -> {
                Box(modifier = Modifier.height(300.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            info == null -> {
                Text(renderError ?: "Error loading page", color = Color.Red, modifier = Modifier.padding(24.dp))
            }
            else -> {
                val viewportWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val fitScale = viewportWidth / info.bitmap.width.toFloat()
                val pageWidthPx = (info.bitmap.width * fitScale).toInt().coerceAtLeast(1)
                val pageHeightPx = (info.bitmap.height * fitScale).toInt().coerceAtLeast(1)
                val density = LocalDensity.current
                val pageWidthDp = (pageWidthPx / density.density).dp
                val pageHeightDp = (pageHeightPx / density.density).dp

                Box(
                    modifier = Modifier
                        .width(pageWidthDp)
                        .height(pageHeightDp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                ) {
                    val filter = when (pageColorFilter) {
                        PageColorFilter.INVERT -> ColorFilter.colorMatrix(
                            ColorMatrix(
                                floatArrayOf(
                                    -1f, 0f, 0f, 0f, 255f,
                                    0f, -1f, 0f, 0f, 255f,
                                    0f, 0f, -1f, 0f, 255f,
                                    0f, 0f, 0f, 1f, 0f
                                )
                            )
                        )
                        PageColorFilter.SEPIA -> ColorFilter.colorMatrix(
                            ColorMatrix(
                                floatArrayOf(
                                    0.393f, 0.769f, 0.189f, 0f, 0f,
                                    0.349f, 0.686f, 0.168f, 0f, 0f,
                                    0.272f, 0.534f, 0.131f, 0f, 0f,
                                    0f, 0f, 0f, 1f, 0f
                                )
                            )
                        )
                        PageColorFilter.NORMAL -> null
                    }

                    Image(
                        bitmap = info.bitmap.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                        colorFilter = filter
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(editorMode, currentColor, strokeWidth, activeSignaturePreset) {
                                when (editorMode) {
                                    EditorMode.DRAW, EditorMode.HIGHLIGHT -> {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                currentPoints = listOf(
                                                    Offset((offset.x / size.width.toFloat()).coerceIn(0f, 1f), (offset.y / size.height.toFloat()).coerceIn(0f, 1f))
                                                )
                                            },
                                            onDragEnd = {
                                                if (currentPoints.isNotEmpty()) {
                                                    val minDim = kotlin.math.max(1f, minOf(size.width.toFloat(), size.height.toFloat()))
                                                    onStrokeAdded(
                                                        PdfStroke(
                                                            points = currentPoints,
                                                            color = currentColor,
                                                            widthFraction = (strokeWidth / minDim).coerceAtLeast(0.001f)
                                                        )
                                                    )
                                                }
                                                currentPoints = emptyList()
                                            },
                                            onDragCancel = { currentPoints = emptyList() }
                                        ) { change, _ ->
                                            change.consume()
                                            currentPoints = currentPoints + Offset(
                                                (change.position.x / size.width.toFloat()).coerceIn(0f, 1f),
                                                (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                            )
                                        }
                                    }
                                    EditorMode.SHAPE_RECT, EditorMode.SHAPE_ARROW, EditorMode.SHAPE_LINE, EditorMode.UNDERLINE, EditorMode.STRIKETHROUGH -> {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val norm = Offset((offset.x / size.width.toFloat()).coerceIn(0f, 1f), (offset.y / size.height.toFloat()).coerceIn(0f, 1f))
                                                shapeStart = norm
                                                shapeEnd = norm
                                            },
                                            onDragEnd = {
                                                val s = shapeStart
                                                val e = shapeEnd
                                                if (s != null && e != null) {
                                                    val shapeType = when (editorMode) {
                                                        EditorMode.SHAPE_RECT -> ShapeType.RECTANGLE
                                                        EditorMode.SHAPE_ARROW -> ShapeType.ARROW
                                                        EditorMode.SHAPE_LINE -> ShapeType.LINE
                                                        EditorMode.UNDERLINE -> ShapeType.UNDERLINE
                                                        else -> ShapeType.STRIKETHROUGH
                                                    }
                                                    val minDim = kotlin.math.max(1f, minOf(size.width.toFloat(), size.height.toFloat()))
                                                    onShapeAdded(
                                                        PdfShape(
                                                            type = shapeType,
                                                            start = s,
                                                            end = e,
                                                            color = currentColor,
                                                            widthFraction = (strokeWidth / minDim).coerceAtLeast(0.002f)
                                                        )
                                                    )
                                                }
                                                shapeStart = null
                                                shapeEnd = null
                                            },
                                            onDragCancel = { shapeStart = null; shapeEnd = null }
                                        ) { change, _ ->
                                            change.consume()
                                            shapeEnd = Offset((change.position.x / size.width.toFloat()).coerceIn(0f, 1f), (change.position.y / size.height.toFloat()).coerceIn(0f, 1f))
                                        }
                                    }
                                    EditorMode.SIGNATURE -> {
                                        detectTapGestures { offset ->
                                            val preset = activeSignaturePreset
                                            if (preset != null && preset.isNotEmpty()) {
                                                val norm = Offset((offset.x / size.width.toFloat()).coerceIn(0f, 1f), (offset.y / size.height.toFloat()).coerceIn(0f, 1f))
                                                onSignatureAdded(
                                                    PdfSignature(
                                                        position = norm,
                                                        size = Offset(0.25f, 0.1f),
                                                        strokes = preset,
                                                        color = currentColor
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    EditorMode.ERASE -> {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                onEraseAt(Offset((offset.x / size.width.toFloat()).coerceIn(0f, 1f), (offset.y / size.height.toFloat()).coerceIn(0f, 1f)))
                                            },
                                            onDragEnd = {},
                                            onDragCancel = {}
                                        ) { change, _ ->
                                            change.consume()
                                            onEraseAt(Offset((change.position.x / size.width.toFloat()).coerceIn(0f, 1f), (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)))
                                        }
                                    }
                                    EditorMode.TEXT -> {
                                        detectTapGestures { offset ->
                                            textPosition = Offset((offset.x / size.width.toFloat()).coerceIn(0f, 1f), (offset.y / size.height.toFloat()).coerceIn(0f, 1f))
                                            showTextDialog = true
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                    ) {
                        strokes.forEach { drawNormalizedStroke(it) }
                        if (currentPoints.isNotEmpty()) {
                            drawNormalizedStroke(
                                PdfStroke(
                                    points = currentPoints,
                                    color = currentColor,
                                    widthFraction = (strokeWidth / maxOf(1f, minOf(size.width, size.height))).coerceAtLeast(0.001f)
                                )
                            )
                        }

                        shapes.forEach { drawNormalizedShape(it) }
                        val s = shapeStart
                        val e = shapeEnd
                        if (s != null && e != null) {
                            val activeShapeType = when (editorMode) {
                                EditorMode.SHAPE_RECT -> ShapeType.RECTANGLE
                                EditorMode.SHAPE_ARROW -> ShapeType.ARROW
                                EditorMode.SHAPE_LINE -> ShapeType.LINE
                                EditorMode.UNDERLINE -> ShapeType.UNDERLINE
                                else -> ShapeType.STRIKETHROUGH
                            }
                            val minDim = minOf(size.width, size.height).coerceAtLeast(1f)
                            drawNormalizedShape(
                                PdfShape(
                                    type = activeShapeType,
                                    start = s,
                                    end = e,
                                    color = currentColor,
                                    widthFraction = (strokeWidth / minDim).coerceAtLeast(0.002f)
                                )
                            )
                        }

                        signatures.forEach { drawNormalizedSignature(it) }

                        texts.forEach { text ->
                            val fontSize = text.sizeFraction * minOf(size.width, size.height)
                            val colorInt = text.color.toAndroidColor()

                            val latinPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = colorInt
                                textSize = fontSize
                                typeface = latinTypeface
                                isFakeBoldText = text.isBold
                            }
                            val devanagariPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = colorInt
                                textSize = fontSize
                                typeface = devanagariTypeface
                                isFakeBoldText = text.isBold
                            }

                            val startX = text.position.x * size.width
                            val startY = text.position.y * size.height
                            val lines = text.text.split('\n')

                            drawIntoCanvas { canvas ->
                                lines.forEachIndexed { lineIndex, line ->
                                    val lineY = startY + lineIndex * fontSize * 1.2f
                                    val runs = splitLineToPreviewRuns(line, latinPaint, devanagariPaint)
                                    var currentX = startX

                                    text.backgroundColor?.let { bgCol ->
                                        val totalWidth = runs.sumOf { r ->
                                            val p = if (r.fontChoice == PreviewFontChoice.LATIN) latinPaint else devanagariPaint
                                            p.measureText(r.text).toDouble()
                                        }.toFloat()
                                        val bgPaint = android.graphics.Paint().apply {
                                            color = bgCol.toAndroidColor()
                                        }
                                        canvas.nativeCanvas.drawRect(
                                            currentX - 4f,
                                            lineY - fontSize + 2f,
                                            currentX + totalWidth + 4f,
                                            lineY + 4f,
                                            bgPaint
                                        )
                                    }

                                    runs.forEach { run ->
                                        val paint = if (run.fontChoice == PreviewFontChoice.LATIN) latinPaint else devanagariPaint
                                        canvas.nativeCanvas.drawText(run.text, currentX, lineY, paint)
                                        currentX += paint.measureText(run.text)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class PreviewFontChoice { LATIN, DEVANAGARI }

private data class PreviewTextRun(val text: String, val fontChoice: PreviewFontChoice)

private fun splitLineToPreviewRuns(
    line: String,
    latinPaint: android.graphics.Paint,
    devanagariPaint: android.graphics.Paint
): List<PreviewTextRun> {
    if (line.isEmpty()) return emptyList()
    val runs = mutableListOf<PreviewTextRun>()
    var currentChoice: PreviewFontChoice? = null
    val currentSb = StringBuilder()

    var i = 0
    while (i < line.length) {
        val codePoint = line.codePointAt(i)
        val charCount = Character.charCount(codePoint)
        val cpString = line.substring(i, i + charCount)

        val choice = if (latinPaint.hasGlyph(cpString)) {
            PreviewFontChoice.LATIN
        } else if (devanagariPaint.hasGlyph(cpString)) {
            PreviewFontChoice.DEVANAGARI
        } else {
            PreviewFontChoice.LATIN
        }

        if (currentChoice == null) {
            currentChoice = choice
            currentSb.append(cpString)
        } else if (choice == currentChoice) {
            currentSb.append(cpString)
        } else {
            runs.add(PreviewTextRun(currentSb.toString(), currentChoice))
            currentChoice = choice
            currentSb.clear()
            currentSb.append(cpString)
        }

        i += charCount
    }
    if (currentChoice != null && currentSb.isNotEmpty()) {
        runs.add(PreviewTextRun(currentSb.toString(), currentChoice))
    }
    return runs
}
