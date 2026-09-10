package com.ankit.pdfeditor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankit.pdfeditor.ui.theme.PDFEdittorAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

enum class EditorMode { VIEW, DRAW, TEXT, ERASE }
enum class AppScreen { HOME, EDITOR, SETTINGS }

data class PdfStroke(
    val path: Path,
    val color: Color = Color.Red,
    val strokeWidth: Float = 8f
)

data class RecentFileItem(
    val uri: String,
    val fileName: String,
    val openedAt: String,
    var isFavorite: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PDFEdittorAppTheme {
                PdfStudioApp()
            }
        }
    }
}

@Composable
fun PdfStudioApp() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var pdfFile by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }

    var editorMode by remember { mutableStateOf(EditorMode.VIEW) }
    var currentColor by remember { mutableStateOf(Color.Red) }
    var strokeWidth by remember { mutableFloatStateOf(8f) }

    val recentFiles = remember { mutableStateListOf<RecentFileItem>() }
    val pageStrokes = remember { mutableStateMapOf<Int, MutableList<PdfStroke>>() }
    val strokeHistory = remember { mutableStateListOf<Pair<Int, PdfStroke>>() }

    // Using OpenDocument is safer for persistent permissions, but GetContent works if we request persistable permissions.
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // FIX: Take persistable URI permissions so recent files don't crash the app on restart
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                e.printStackTrace()
            }

            val name = resolveFileName(context, it)
            val openedAt = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date())

            recentFiles.removeAll { item -> item.uri == it.toString() }
            recentFiles.add(0, RecentFileItem(uri = it.toString(), fileName = name, openedAt = openedAt))

            pdfUri = it
            zoomLevel = 1f
            currentPage = 0
            editorMode = EditorMode.VIEW
            pageStrokes.clear()
            strokeHistory.clear()
            currentScreen = AppScreen.EDITOR

            try {
                renderer?.close()
                pdfFile?.close()

                val fileDescriptor = context.contentResolver.openFileDescriptor(it, "r")
                if (fileDescriptor != null) {
                    pdfFile = fileDescriptor
                    renderer = PdfRenderer(fileDescriptor)
                    pageCount = renderer!!.pageCount
                } else {
                    renderer = null
                    pdfFile = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            renderer?.close()
            pdfFile?.close()
        }
    }

    when (currentScreen) {
        AppScreen.HOME -> {
            HomeScreen(
                recentFiles = recentFiles,
                onOpenPdf = { filePicker.launch("application/pdf") },
                onFileTap = { selectedUri ->
                    pdfUri = selectedUri
                    currentScreen = AppScreen.EDITOR
                },
                onSettingsTap = { currentScreen = AppScreen.SETTINGS }
            )
        }
        AppScreen.EDITOR -> {
            // CLEANUP: Used .let to avoid !! operators
            renderer?.let { safeRenderer ->
                EditorScreen(
                    renderer = safeRenderer,
                    currentPage = currentPage,
                    pageCount = pageCount,
                    zoomLevel = zoomLevel,
                    editorMode = editorMode,
                    currentColor = currentColor,
                    strokeWidth = strokeWidth,
                    pageStrokes = pageStrokes,
                    onPageChange = { currentPage = it },
                    onZoomChange = { zoomLevel = it },
                    onModeChange = { editorMode = it },
                    onColorChange = { currentColor = it },
                    onWidthChange = { strokeWidth = it },
                    onStrokeAdded = { pageIdx, stroke ->
                        pageStrokes.getOrPut(pageIdx) { mutableListOf() }.add(stroke)
                        strokeHistory.add(pageIdx to stroke)
                    },
                    onUndo = {
                        if (strokeHistory.isNotEmpty()) {
                            val (pageIdx, stroke) = strokeHistory.removeAt(strokeHistory.size - 1)
                            pageStrokes[pageIdx]?.remove(stroke)
                        }
                    },
                    onBackClick = { currentScreen = AppScreen.HOME }
                )
            }
        }
        AppScreen.SETTINGS -> {
            SettingsScreen(onBackClick = { currentScreen = AppScreen.HOME })
        }
    }
}

@Composable
fun HomeScreen(
    recentFiles: List<RecentFileItem>,
    onOpenPdf: () -> Unit,
    onFileTap: (Uri) -> Unit,
    onSettingsTap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        TopBarHome(onSettingsTap = onSettingsTap)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                WelcomeSection(onOpenPdf = onOpenPdf)
            }

            // --- NEW: Added the Horizontal Import Chips Here ---
            item {
                ImportSourcesSection(onOpenPdf = onOpenPdf)
            }

            if (recentFiles.isNotEmpty()) {
                item {
                    RecentFilesSection(
                        recentFiles = recentFiles,
                        onFileTap = { onFileTap(Uri.parse(it.uri)) }
                    )
                }
            }

            item {
                QuickActionsSection(onOpenPdf = onOpenPdf)
            }
        }
    }
}
@Composable
fun TopBarHome(onSettingsTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "PDF Studio",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1F2937)
        )
        IconButton(onClick = onSettingsTap) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF6366F1))
        }
    }
}

@Composable
fun WelcomeSection(onOpenPdf: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF6366F1)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
                "PDF Reader, Read All Docs",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Read, manage & organize all your PDFs easily in one place.",
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
fun RecentFilesSection(
    recentFiles: List<RecentFileItem>,
    onFileTap: (RecentFileItem) -> Unit
) {
    Column {
        Text(
            "Recently Added",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1F2937)
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(recentFiles.take(5)) { file ->
                RecentFileCard(file = file, onTap = { onFileTap(file) })
            }
        }
    }
}

@Composable
fun RecentFileCard(file: RecentFileItem, onTap: () -> Unit) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .pointerInput(Unit) {
                detectTapGestures { onTap() }
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color(0xFFEF4444)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                file.fileName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                file.openedAt,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun QuickActionsSection(onOpenPdf: () -> Unit) {
    Column {
        Text(
            "Quick Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1F2937)
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
fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(120.dp)
            .pointerInput(Unit) {
                detectTapGestures { onClick() }
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = Color(0xFF6366F1))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EditorScreen(
    renderer: PdfRenderer,
    currentPage: Int,
    pageCount: Int,
    zoomLevel: Float,
    editorMode: EditorMode,
    currentColor: Color,
    strokeWidth: Float,
    pageStrokes: MutableMap<Int, MutableList<PdfStroke>>,
    onPageChange: (Int) -> Unit,
    onZoomChange: (Float) -> Unit,
    onModeChange: (EditorMode) -> Unit,
    onColorChange: (Color) -> Unit,
    onWidthChange: (Float) -> Unit,
    onStrokeAdded: (Int, PdfStroke) -> Unit,
    onUndo: () -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            EditorTopBar(
                fileName = "PDF Document",
                onBackClick = onBackClick
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
                canUndo = pageStrokes.values.any { it.isNotEmpty() },
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
            pageStrokes = pageStrokes,
            modifier = Modifier.padding(innerPadding),
            onPageChange = onPageChange,
            onStrokeAdded = onStrokeAdded
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(fileName: String, onBackClick: () -> Unit) {
    TopAppBar(
        title = { Text(fileName, fontWeight = FontWeight.Bold) },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorBottomBar(
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
        if (editorMode == EditorMode.DRAW) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF3F4F6))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Color.Red, Color.Blue, Color.Green, Color.Black, Color.Yellow).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(color, shape = RoundedCornerShape(50))
                                .pointerInput(Unit) {
                                    detectTapGestures { onColorChange(color) }
                                }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(4f to "T", 10f to "M", 24f to "B").forEach { (width, label) ->
                        FilterChip(
                            selected = strokeWidth == width,
                            onClick = { onWidthChange(width) },
                            label = { Text(label, fontSize = 10.sp) }
                        )
                    }
                }
            }
        }

        BottomAppBar(containerColor = Color.White) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        onClick = { onModeChange(EditorMode.DRAW) }
                    )
                    EditorModeButton(
                        icon = Icons.Default.Close,
                        label = "Erase",
                        isSelected = editorMode == EditorMode.ERASE,
                        onClick = { onModeChange(EditorMode.ERASE) }
                    )
                    IconButton(onClick = onUndo, enabled = canUndo) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo", modifier = Modifier.size(18.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onZoomChange((zoomLevel - 0.2f).coerceAtLeast(1f)) }) {
                        Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Text("${(zoomLevel * 100).toInt()}%", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    IconButton(onClick = { onZoomChange((zoomLevel + 0.2f).coerceAtMost(3f)) }) {
                        Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun EditorModeButton(
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
fun PdfViewerWithSwipe(
    renderer: PdfRenderer,
    currentPage: Int,
    pageCount: Int,
    zoomLevel: Float,
    editorMode: EditorMode,
    currentColor: Color,
    strokeWidth: Float,
    pageStrokes: MutableMap<Int, MutableList<PdfStroke>>,
    modifier: Modifier = Modifier,
    onPageChange: (Int) -> Unit,
    onStrokeAdded: (Int, PdfStroke) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    // Only allow swipe-to-change-page if we are in VIEW mode and NOT zoomed in.
                    // Otherwise, the gestures will clash.
                    if (editorMode == EditorMode.VIEW && zoomLevel == 1f) {
                        change.consume()
                        val (_, dy) = dragAmount
                        if (dy < -50 && currentPage < pageCount - 1) {
                            onPageChange(currentPage + 1)
                        } else if (dy > 50 && currentPage > 0) {
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
                    fontWeight = FontWeight.Medium
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
                    strokes = pageStrokes.getOrPut(currentPage) { mutableListOf() },
                    onStrokeAdded = { stroke -> onStrokeAdded(currentPage, stroke) }
                )
            }
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
    strokes: MutableList<PdfStroke>,
    onStrokeAdded: (PdfStroke) -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    val context = LocalContext.current

    // FIX: Memory leak & crash fix using try-finally and executing on Dispatchers.IO
    // FIX: Scaled up resolution using screen density to eliminate blurriness.
    LaunchedEffect(pageIndex, renderer) {
        isLoading = true
        withContext(Dispatchers.IO) {
            var page: PdfRenderer.Page? = null
            try {
                page = renderer.openPage(pageIndex)

                val density = context.resources.displayMetrics.density
                // Create a higher resolution bitmap based on device pixel density
                val width = (page.width * density).toInt()
                val height = (page.height * density).toInt()

                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Guarantee the page is closed, allowing the next one to open
                page?.close()
                isLoading = false
            }
        }
    }

    // FIX: Applied zoom graphics layer to the PARENT box so Image & Canvas scale perfectly together.
    // This fixes the bug where drawn lines appeared in the wrong spot when zoomed.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = zoomLevel,
                scaleY = zoomLevel
            )
            .then(
                when (editorMode) {
                    EditorMode.DRAW -> Modifier.pointerInput(currentColor, strokeWidth) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPath = Path().apply { moveTo(offset.x, offset.y) }
                            },
                            onDragEnd = {
                                currentPath?.let { path ->
                                    onStrokeAdded(PdfStroke(path, currentColor, strokeWidth))
                                }
                                currentPath = null
                            }
                        ) { change, _ ->
                            change.consume()
                            currentPath?.lineTo(change.position.x, change.position.y)
                        }
                    }
                    else -> Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> CircularProgressIndicator()
            bitmap != null -> {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Page $pageIndex",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    strokes.forEach { stroke ->
                        drawPath(stroke.path, stroke.color, style = Stroke(stroke.strokeWidth))
                    }
                    currentPath?.let {
                        drawPath(it, currentColor, style = Stroke(strokeWidth))
                    }
                }
            }
            else -> Text("Error loading page", color = Color.Red)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
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

        LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SettingItem(title = "Theme", value = "Light")
            }
            item {
                SettingItem(title = "Version", value = "1.0")
            }
            item {
                SettingItem(title = "About", value = "PDF Studio")
            }
        }
    }
}

@Composable
fun SettingItem(title: String, value: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
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
fun ImportSourceChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = com.ankit.pdfeditor.ui.theme.CardBackground,
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
                tint = com.ankit.pdfeditor.ui.theme.PurplePrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = com.ankit.pdfeditor.ui.theme.TextPrimary
            )
        }
    }
}

@Composable
fun ImportSourcesSection(onOpenPdf: () -> Unit) {
    Column {
        Text(
            text = "Import From",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = com.ankit.pdfeditor.ui.theme.TextPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                    onClick = onOpenPdf
                )
            }
            item {
                ImportSourceChip(
                    icon = Icons.Default.Description, // Make sure Icons.Default.Description is imported!
                    label = "Docs",
                    onClick = onOpenPdf
                )
            }
        }
    }
}





// FIX: Added a check for index != -1 to prevent CursorIndexOutOfBoundsException
private fun resolveFileName(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) cursor.getString(index) else "Unknown PDF"
            } else "Unknown PDF"
        } ?: "Unknown PDF"
    } catch (e: Exception) {
        "Unknown PDF"
    }
}