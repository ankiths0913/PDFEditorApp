package com.ankit.pdfeditor.ui.editor

import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankit.pdfeditor.model.*
import com.ankit.pdfeditor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorScreen(
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
    pageShapes: Map<Int, List<PdfShape>>,
    pageSignatures: Map<Int, List<PdfSignature>>,
    savedSignatures: List<List<List<Offset>>>,
    textIsBold: Boolean,
    textBgColor: Color?,
    pageColorFilter: PageColorFilter,
    rendererMutex: Mutex,
    isBusy: Boolean,
    hasUnsavedChanges: Boolean,
    hasUndoHistory: Boolean,
    hasRedoHistory: Boolean,
    onPageChange: (Int) -> Unit,
    onZoomChange: (Float) -> Unit,
    onModeChange: (EditorMode) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onTextBoldToggle: () -> Unit,
    onTextBgColorChange: (Color?) -> Unit,
    onPageColorFilterChange: (PageColorFilter) -> Unit,
    onStrokeAdded: (Int, PdfStroke) -> Unit,
    onTextAdded: (Int, PdfText) -> Unit,
    onShapeAdded: (Int, PdfShape) -> Unit,
    onSignatureAdded: (Int, PdfSignature) -> Unit,
    onSaveSignaturePreset: (List<List<Offset>>) -> Unit,
    onEraseAt: (Int, Offset) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSaveClick: () -> Unit,
    onShareClick: (Uri) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fileName = remember(pdfUri) { resolveFileName(context, pdfUri) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<SearchMatch>>(emptyList()) }
    var currentMatchIndex by remember { mutableIntStateOf(-1) }

    var isOutlineSheetOpen by remember { mutableStateOf(false) }
    var outlineItems by remember { mutableStateOf<List<PdfOutlineItem>>(emptyList()) }

    var showSignatureCanvasDialog by remember { mutableStateOf(false) }
    var activeSignaturePreset by remember { mutableStateOf<List<List<Offset>>?>(savedSignatures.firstOrNull()) }

    LaunchedEffect(pdfUri) {
        withContext(Dispatchers.IO) {
            outlineItems = extractPdfOutline(context, pdfUri)
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isSearchActive = false; searchQuery = ""; searchMatches = emptyList(); currentMatchIndex = -1 }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Search")
                        }
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { q ->
                                searchQuery = q
                                scope.launch {
                                    val results = searchPdfText(context, pdfUri, q)
                                    searchMatches = results
                                    currentMatchIndex = if (results.isNotEmpty()) 0 else -1
                                    if (results.isNotEmpty()) {
                                        onPageChange(results[0].pageIndex)
                                    }
                                }
                            },
                            placeholder = { Text("Search text...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (searchMatches.isNotEmpty()) {
                            Text(
                                "${currentMatchIndex + 1}/${searchMatches.size}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = {
                                    if (currentMatchIndex > 0) {
                                        currentMatchIndex--
                                        onPageChange(searchMatches[currentMatchIndex].pageIndex)
                                    }
                                },
                                enabled = currentMatchIndex > 0
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Match")
                            }
                            IconButton(
                                onClick = {
                                    if (currentMatchIndex < searchMatches.size - 1) {
                                        currentMatchIndex++
                                        onPageChange(searchMatches[currentMatchIndex].pageIndex)
                                    }
                                },
                                enabled = currentMatchIndex < searchMatches.size - 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Match")
                            }
                        }
                    }
                }
            } else {
                EditorTopBar(
                    fileName = fileName,
                    currentPage = currentPage,
                    pageCount = pageCount,
                    pageColorFilter = pageColorFilter,
                    onPageColorFilterChange = onPageColorFilterChange,
                    onSearchClick = { isSearchActive = true },
                    onOutlineClick = { isOutlineSheetOpen = true },
                    onShareClick = { onShareClick(pdfUri) },
                    onSaveClick = onSaveClick,
                    onBackClick = onBackClick,
                    saveEnabled = !isBusy,
                    shareEnabled = !isBusy,
                    hasUnsavedChanges = hasUnsavedChanges
                )
            }
        },
        bottomBar = {
            EditorBottomBar(
                editorMode = editorMode,
                currentColor = currentColor,
                onModeChange = onModeChange,
                onColorChange = onColorChange,
                strokeWidth = strokeWidth,
                onWidthChange = onWidthChange,
                textIsBold = textIsBold,
                onTextBoldToggle = onTextBoldToggle,
                textBgColor = textBgColor,
                onTextBgColorChange = onTextBgColorChange,
                zoomLevel = zoomLevel,
                onZoomChange = onZoomChange,
                canUndo = hasUndoHistory,
                canRedo = hasRedoHistory,
                onUndo = onUndo,
                onRedo = onRedo,
                onOpenSignatureCanvas = { showSignatureCanvasDialog = true }
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
            pageStrokesMap = pageStrokes,
            pageTextsMap = pageTexts,
            pageShapesMap = pageShapes,
            pageSignaturesMap = pageSignatures,
            activeSignaturePreset = activeSignaturePreset,
            pageColorFilter = pageColorFilter,
            rendererMutex = rendererMutex,
            modifier = Modifier.padding(innerPadding),
            onPageChange = onPageChange,
            onStrokeAdded = onStrokeAdded,
            onTextAdded = onTextAdded,
            onShapeAdded = onShapeAdded,
            onSignatureAdded = onSignatureAdded,
            onEraseAt = onEraseAt
        )
    }

    if (isOutlineSheetOpen) {
        ModalBottomSheet(onDismissRequest = { isOutlineSheetOpen = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Table of Contents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                if (outlineItems.isEmpty()) {
                    Text("No bookmarks or table of contents found in this PDF.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        items(outlineItems) { item ->
                            OutlineRow(item) { targetPage ->
                                onPageChange(targetPage)
                                isOutlineSheetOpen = false
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSignatureCanvasDialog) {
        SignatureCanvasDialog(
            onDismiss = { showSignatureCanvasDialog = false },
            onSaveSignature = { strokes ->
                onSaveSignaturePreset(strokes)
                activeSignaturePreset = strokes
                onModeChange(EditorMode.SIGNATURE)
                showSignatureCanvasDialog = false
            }
        )
    }
}

@Composable
private fun OutlineRow(item: PdfOutlineItem, onSelectPage: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectPage(item.pageIndex) }
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(item.title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            Text("Page ${item.pageIndex + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        item.children.forEach { child ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectPage(child.pageIndex) }
                    .padding(start = 24.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(child.title, style = MaterialTheme.typography.bodySmall)
                Text("Page ${child.pageIndex + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun NightModeButton(
    currentFilter: PageColorFilter,
    onFilterChange: (PageColorFilter) -> Unit
) {
    val nextFilter = when (currentFilter) {
        PageColorFilter.NORMAL -> PageColorFilter.INVERT
        PageColorFilter.INVERT -> PageColorFilter.SEPIA
        PageColorFilter.SEPIA -> PageColorFilter.NORMAL
    }
    val label = when (currentFilter) {
        PageColorFilter.NORMAL -> "Normal"
        PageColorFilter.INVERT -> "Invert"
        PageColorFilter.SEPIA -> "Sepia"
    }
    Surface(
        onClick = { onFilterChange(nextFilter) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    fileName: String,
    currentPage: Int,
    pageCount: Int,
    pageColorFilter: PageColorFilter,
    onPageColorFilterChange: (PageColorFilter) -> Unit,
    onSearchClick: () -> Unit,
    onOutlineClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
    saveEnabled: Boolean,
    shareEnabled: Boolean,
    hasUnsavedChanges: Boolean
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        fileName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (hasUnsavedChanges) {
                            "Page ${currentPage + 1} of $pageCount • Unsaved changes"
                        } else {
                            "Page ${currentPage + 1} of $pageCount"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasUnsavedChanges) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
            }
        },
        actions = {
            NightModeButton(currentFilter = pageColorFilter, onFilterChange = onPageColorFilterChange)
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "Search Text")
            }
            IconButton(onClick = onOutlineClick) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Table of Contents")
            }
            IconButton(onClick = onSaveClick, enabled = saveEnabled) {
                Icon(Icons.Default.Save, contentDescription = "Save PDF")
            }
            IconButton(onClick = onShareClick, enabled = shareEnabled) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share PDF",
                    tint = Color.White,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(6.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
private fun EditorBottomBar(
    editorMode: EditorMode,
    currentColor: Color,
    onModeChange: (EditorMode) -> Unit,
    onColorChange: (Color) -> Unit,
    strokeWidth: Float,
    onWidthChange: (Float) -> Unit,
    textIsBold: Boolean,
    onTextBoldToggle: () -> Unit,
    textBgColor: Color?,
    onTextBgColorChange: (Color?) -> Unit,
    zoomLevel: Float,
    onZoomChange: (Float) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOpenSignatureCanvas: () -> Unit
) {
    val isShapeMode = editorMode == EditorMode.SHAPE_RECT || editorMode == EditorMode.SHAPE_ARROW ||
        editorMode == EditorMode.SHAPE_LINE || editorMode == EditorMode.UNDERLINE ||
        editorMode == EditorMode.STRIKETHROUGH
    val showSettings = editorMode == EditorMode.DRAW || editorMode == EditorMode.HIGHLIGHT ||
        editorMode == EditorMode.TEXT || isShapeMode

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showSettings) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        when (editorMode) {
                            EditorMode.HIGHLIGHT -> "Highlighter Settings"
                            EditorMode.DRAW -> "Pen Settings"
                            EditorMode.TEXT -> "Text Settings"
                            else -> "Shape Settings"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
                    if (isShapeMode) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                EditorMode.SHAPE_RECT to "Rectangle",
                                EditorMode.SHAPE_ARROW to "Arrow",
                                EditorMode.SHAPE_LINE to "Line",
                                EditorMode.UNDERLINE to "Underline",
                                EditorMode.STRIKETHROUGH to "Strike"
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = editorMode == mode,
                                    onClick = { onModeChange(mode) },
                                    label = { Text(label, maxLines = 1, softWrap = false) }
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colors.forEach { color ->
                            val selected = color == currentColor
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                    .pointerInput(color) { detectTapGestures { onColorChange(color) } }
                            )
                        }
                    }

                    if (editorMode == EditorMode.TEXT) {
                        FilterChip(
                            selected = textIsBold,
                            onClick = onTextBoldToggle,
                            label = { Text("Bold", fontWeight = FontWeight.Bold) }
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Background", style = MaterialTheme.typography.labelMedium)
                            listOf(null to "None", Color.Yellow.copy(alpha = 0.4f) to "Yellow", Color.Cyan.copy(alpha = 0.4f) to "Cyan").forEach { (col, lbl) ->
                                FilterChip(
                                    selected = textBgColor == col,
                                    onClick = { onTextBgColorChange(col) },
                                    label = { Text(lbl, maxLines = 1, softWrap = false) }
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(4f to "Fine", 12f to "Med", 28f to "Bold").forEach { (width, label) ->
                                val selected = strokeWidth == width
                                Surface(
                                    onClick = { onWidthChange(width) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        label,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    EditorModeButton(Modifier.weight(1f), Icons.Default.Visibility, "View", editorMode == EditorMode.VIEW) {
                        onModeChange(EditorMode.VIEW)
                    }
                    EditorModeButton(Modifier.weight(1f), Icons.Default.Edit, "Draw", editorMode == EditorMode.DRAW) {
                        onModeChange(EditorMode.DRAW); onColorChange(Color.Red); onWidthChange(8f)
                    }
                    EditorModeButton(Modifier.weight(1f), Icons.Default.Highlight, "Mark", editorMode == EditorMode.HIGHLIGHT) {
                        onModeChange(EditorMode.HIGHLIGHT); onColorChange(Color.Yellow.copy(alpha = DEFAULT_HIGHLIGHT_ALPHA)); onWidthChange(28f)
                    }
                    EditorModeButton(Modifier.weight(1f), Icons.Default.Category, "Shapes", isShapeMode) {
                        if (!isShapeMode) {
                            onModeChange(EditorMode.SHAPE_RECT); onColorChange(Color.Red); onWidthChange(4f)
                        }
                    }
                    EditorModeButton(Modifier.weight(1f), Icons.Default.TextFields, "Text", editorMode == EditorMode.TEXT) {
                        if (editorMode != EditorMode.TEXT) onColorChange(Color.Black)
                        onModeChange(EditorMode.TEXT)
                    }
                    EditorModeButton(Modifier.weight(1f), Icons.Default.Draw, "Sign", editorMode == EditorMode.SIGNATURE) {
                        onOpenSignatureCanvas()
                    }
                    EditorModeButton(Modifier.weight(1f), Icons.Default.Delete, "Erase", editorMode == EditorMode.ERASE) {
                        onModeChange(EditorMode.ERASE)
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .width(1.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
                IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (canUndo) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (canRedo) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun EditorModeButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SignatureCanvasDialog(
    onDismiss: () -> Unit,
    onSaveSignature: (List<List<Offset>>) -> Unit
) {
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw Signature") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStroke = listOf(Offset(offset.x / size.width.toFloat(), offset.y / size.height.toFloat()))
                                },
                                onDragEnd = {
                                    if (currentStroke.isNotEmpty()) {
                                        strokes = strokes + listOf(currentStroke)
                                    }
                                    currentStroke = emptyList()
                                },
                                onDragCancel = { currentStroke = emptyList() }
                            ) { change, _ ->
                                change.consume()
                                currentStroke = currentStroke + Offset(change.position.x / size.width.toFloat(), change.position.y / size.height.toFloat())
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        strokes.forEach { pts ->
                            if (pts.isNotEmpty()) {
                                val screenPts = pts.map { Offset(it.x * size.width, it.y * size.height) }
                                drawPath(buildComposePath(screenPts), color = Color.Black, style = Stroke(3f, cap = StrokeCap.Round))
                            }
                        }
                        if (currentStroke.isNotEmpty()) {
                            val screenPts = currentStroke.map { Offset(it.x * size.width, it.y * size.height) }
                            drawPath(buildComposePath(screenPts), color = Color.Black, style = Stroke(3f, cap = StrokeCap.Round))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { strokes = emptyList(); currentStroke = emptyList() }) {
                    Text("Clear Canvas")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSaveSignature(strokes) },
                enabled = strokes.isNotEmpty()
            ) { Text("Save & Use Signature") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
