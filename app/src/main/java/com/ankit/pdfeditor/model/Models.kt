package com.ankit.pdfeditor.model

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import java.util.UUID

internal enum class EditorMode { VIEW, DRAW, HIGHLIGHT, TEXT, ERASE }
internal enum class AppScreen { HOME, FILES, TOOLS, EDITOR, SETTINGS }
internal enum class SortOption { DATE, NAME }

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
    val sizeFraction: Float = 0.025f
)

internal data class RecentFileItem(
    val uri: String,
    val fileName: String,
    val openedAt: String,
    val fileSizeBytes: Long = -1L,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)

internal data class PageRenderInfo(val bitmap: Bitmap)
