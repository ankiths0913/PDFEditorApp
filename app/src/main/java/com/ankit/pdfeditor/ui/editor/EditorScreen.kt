package com.ankit.pdfeditor.ui.editor

import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ankit.pdfeditor.model.*
import com.ankit.pdfeditor.util.*
import kotlinx.coroutines.sync.Mutex

private const val DEFAULT_HIGHLIGHT_ALPHA = 0.7f
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
    rendererMutex: Mutex,
    isBusy: Boolean,
    hasUnsavedChanges: Boolean,
    hasUndoHistory: Boolean,
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
    val context = LocalContext.current
    // Resolving a display name from a content:// Uri is a blocking ContentResolver
    // query. Calling it directly inside the topBar composable re-ran it on every
    // recomposition of EditorScreen (e.g. every stroke drawn), blocking the main
    // thread each time. Resolve it once per pdfUri instead.
    val fileName = remember(pdfUri) { resolveFileName(context, pdfUri) }

    Scaffold(
        topBar = {
            EditorTopBar(
                fileName = fileName,
                currentPage = currentPage,
                pageCount = pageCount,
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
                currentColor = currentColor,
                onModeChange = onModeChange,
                onColorChange = onColorChange,
                strokeWidth = strokeWidth,
                onWidthChange = onWidthChange,
                zoomLevel = zoomLevel,
                onZoomChange = onZoomChange,
                canUndo = hasUndoHistory,
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
    currentPage: Int,
    pageCount: Int,
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
            IconButton(onClick = onSaveClick, enabled = saveEnabled) {
                Icon(Icons.Default.Save, contentDescription = "Save PDF", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    zoomLevel: Float,
    onZoomChange: (Float) -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (editorMode == EditorMode.DRAW || editorMode == EditorMode.HIGHLIGHT || editorMode == EditorMode.TEXT) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        when (editorMode) {
                            EditorMode.HIGHLIGHT -> "Highlighter Settings"
                            EditorMode.DRAW -> "Pen Settings"
                            else -> "Text Settings"
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
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        colors.forEach { color ->
                            val selected = color == currentColor
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (selected) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .pointerInput(color) { detectTapGestures { onColorChange(color) } }
                            )
                        }
                    }
                    if (editorMode != EditorMode.TEXT) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    EditorModeButton(Icons.Default.Visibility, "View", editorMode == EditorMode.VIEW) {
                        onModeChange(EditorMode.VIEW)
                    }
                    EditorModeButton(Icons.Default.Edit, "Draw", editorMode == EditorMode.DRAW) {
                        onModeChange(EditorMode.DRAW); onColorChange(Color.Red); onWidthChange(8f)
                    }
                    EditorModeButton(Icons.Default.Highlight, "Mark", editorMode == EditorMode.HIGHLIGHT) {
                        onModeChange(EditorMode.HIGHLIGHT); onColorChange(Color.Yellow.copy(alpha = DEFAULT_HIGHLIGHT_ALPHA)); onWidthChange(28f)
                    }
                    EditorModeButton(Icons.Default.TextFields, "Text", editorMode == EditorMode.TEXT) {
                        onModeChange(EditorMode.TEXT)
                    }
                    EditorModeButton(Icons.Default.Delete, "Erase", editorMode == EditorMode.ERASE) {
                        onModeChange(EditorMode.ERASE)
                    }
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                )
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onZoomChange(zoomLevel - 0.2f) }, enabled = zoomLevel > 1f, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom out", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        "${(zoomLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    IconButton(onClick = { onZoomChange(zoomLevel + 0.2f) }, enabled = zoomLevel < 3f, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom in", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
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
    Column(
        modifier = Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (isSelected) MaterialTheme.colorScheme.tertiary else Color.Transparent)
        )
    }
}
