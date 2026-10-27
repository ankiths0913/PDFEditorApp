package com.ankit.pdfeditor.util

import androidx.compose.ui.geometry.Offset
import kotlin.math.max

internal data class PdfPageGeometry(
    val width: Float,
    val height: Float,
    val cropLeft: Float,
    val cropBottom: Float,
    val rotation: Int
)

internal fun mapNormalizedPointToPdf(
    point: Offset,
    geometry: PdfPageGeometry
): Offset {
    val rotation = ((geometry.rotation % 360) + 360) % 360
    val displayWidth = if (rotation == 90 || rotation == 270) geometry.height else geometry.width
    val displayHeight = if (rotation == 90 || rotation == 270) geometry.width else geometry.height
    val displayX = point.x.coerceIn(0f, 1f) * displayWidth
    val displayY = (1f - point.y.coerceIn(0f, 1f)) * displayHeight
    val (x, y) = when (rotation) {
        90 -> displayY to geometry.height - displayX
        180 -> geometry.width - displayX to geometry.height - displayY
        270 -> geometry.width - displayY to displayX
        else -> displayX to displayY
    }
    return Offset(
        geometry.cropLeft + x.coerceIn(0f, geometry.width),
        geometry.cropBottom + y.coerceIn(0f, geometry.height)
    )
}

internal fun displayedMinDimension(geometry: PdfPageGeometry): Float =
    max(1f, minOf(
        if (geometry.rotation % 180 == 0) geometry.width else geometry.height,
        if (geometry.rotation % 180 == 0) geometry.height else geometry.width
    ))
