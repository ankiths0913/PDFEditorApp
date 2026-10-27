package com.ankit.pdfeditor.ui.editor

import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    strokes: List<PdfStroke>,
    texts: List<PdfText>,
    rendererMutex: Mutex,
    modifier: Modifier = Modifier,
    onPageChange: (Int) -> Unit,
    onStrokeAdded: (PdfStroke) -> Unit,
    onTextAdded: (PdfText) -> Unit,
    onEraseAt: (Offset) -> Unit
) {
    val context = LocalContext.current
    val stripState = rememberLazyListState()
    LaunchedEffect(currentPage) {
        stripState.animateScrollToItem((currentPage - 1).coerceAtLeast(0))
    }

    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)
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
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onPageChange(currentPage - 1) },
                            enabled = currentPage > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page", modifier = Modifier.size(20.dp))
                        }
                        Text(
                            "${currentPage + 1} / $pageCount",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        IconButton(
                            onClick = { onPageChange(currentPage + 1) },
                            enabled = currentPage < pageCount - 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page", modifier = Modifier.size(20.dp))
                        }
                    }
            }
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
            if (pageCount > 1) {
                LazyRow(
                    state = stripState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pageCount) { index ->
                        val selected = index == currentPage
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 52.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onPageChange(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
    val context = LocalContext.current
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
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest).graphicsLayer(scaleX = zoomLevel, scaleY = zoomLevel),
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
                                typeface = Typeface.createFromAsset(
                                    context.assets,
                                    "NotoSansDevanagari-Regular.ttf"
                                )
                            }
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawText(
                                    text.text,
                                    text.position.x * size.width,
                                    (1f - text.position.y) * size.height,
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
