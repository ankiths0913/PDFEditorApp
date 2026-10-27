package com.ankit.pdfeditor.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ankit.pdfeditor.model.PdfStroke
import kotlin.math.abs
import kotlin.math.hypot

internal fun distancePointToSegment(point: Offset, a: Offset, b: Offset): Float {
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

internal fun strokeDistance(point: Offset, stroke: PdfStroke): Float {
    if (stroke.points.isEmpty()) return Float.MAX_VALUE
    if (stroke.points.size == 1) return hypot(point.x - stroke.points[0].x, point.y - stroke.points[0].y)
    return stroke.points.zipWithNext().minOf { (a, b) -> distancePointToSegment(point, a, b) }
}

internal fun Color.toAndroidColor(): Int = android.graphics.Color.argb(
    (alpha.coerceIn(0f, 1f) * 255).toInt(),
    (red.coerceIn(0f, 1f) * 255).toInt(),
    (green.coerceIn(0f, 1f) * 255).toInt(),
    (blue.coerceIn(0f, 1f) * 255).toInt()
)

internal fun buildComposePath(points: List<Offset>): Path = Path().apply {
    if (points.isNotEmpty()) {
        moveTo(points.first().x, points.first().y)
        for (point in points.drop(1)) lineTo(point.x, point.y)
    }
}

internal fun DrawScope.drawNormalizedStroke(stroke: PdfStroke) {
    if (stroke.points.isEmpty()) return
    val minDimension = minOf(size.width, size.height)
    val screenPoints = stroke.points.map { Offset(it.x * size.width, it.y * size.height) }
    drawPath(
        path = buildComposePath(screenPoints),
        color = stroke.color,
        style = Stroke(width = stroke.widthFraction * minDimension, cap = StrokeCap.Round)
    )
}
