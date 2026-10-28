package com.ankit.pdfeditor.model

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class EditorMode {
    VIEW, DRAW, HIGHLIGHT, TEXT, ERASE,
    SHAPE_RECT, SHAPE_ARROW, SHAPE_LINE,
    UNDERLINE, STRIKETHROUGH, SIGNATURE, SELECT
}

enum class AppScreen { HOME, FILES, TOOLS, EDITOR, SETTINGS }
enum class SortOption { DATE, NAME, SIZE }
enum class PageColorFilter { NORMAL, INVERT, SEPIA }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ShapeType { RECTANGLE, ARROW, LINE, UNDERLINE, STRIKETHROUGH }

internal data class PdfStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<Offset>,
    val color: Color = Color.Red,
    val widthFraction: Float = 0.008f
)

internal data class PdfText(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val position: Offset,
    val color: Color = Color.Black,
    val sizeFraction: Float = 0.025f,
    val isBold: Boolean = false,
    val backgroundColor: Color? = null
)

internal data class PdfShape(
    val id: String = UUID.randomUUID().toString(),
    val type: ShapeType,
    val start: Offset,
    val end: Offset,
    val color: Color = Color.Red,
    val widthFraction: Float = 0.008f
)

internal data class PdfSignature(
    val id: String = UUID.randomUUID().toString(),
    val position: Offset,
    val size: Offset = Offset(0.25f, 0.1f),
    val strokes: List<List<Offset>>,
    val color: Color = Color.Black
)

internal data class RecentFileItem(
    val uri: String,
    val fileName: String,
    val openedAt: String,
    val fileSizeBytes: Long = -1L,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val lastPage: Int = 0
)

internal data class PageRenderInfo(val bitmap: Bitmap)

internal data class PageSpec(
    val originalPageIndex: Int,
    val rotationDelta: Int = 0
)

internal enum class WatermarkType { PAGE_NUMBERS, WATERMARK_TEXT }
internal enum class NumberPosition { BOTTOM_CENTER, BOTTOM_RIGHT, TOP_RIGHT, TOP_CENTER }

internal data class WatermarkConfig(
    val type: WatermarkType,
    val text: String = "",
    val position: NumberPosition = NumberPosition.BOTTOM_CENTER,
    val opacity: Float = 0.3f
)

internal enum class CompressQuality(
    val label: String,
    val qualityRatio: Float,
    val scaleFactor: Float
) {
    LOW("Low (Maximum Compression)", 0.3f, 0.6f),
    MEDIUM("Medium (Balanced)", 0.6f, 0.8f),
    HIGH("High (High Quality)", 0.85f, 1.0f)
}

internal data class PdfOutlineItem(
    val title: String,
    val pageIndex: Int,
    val children: List<PdfOutlineItem> = emptyList()
)

internal data class SearchMatch(
    val pageIndex: Int,
    val matchText: String,
    val snippet: String = ""
)

internal data class FormFieldItem(
    val name: String,
    val value: String,
    val type: String,
    val pageIndex: Int,
    val rect: androidx.compose.ui.geometry.Rect
)
