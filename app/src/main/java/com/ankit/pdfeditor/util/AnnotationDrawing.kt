package com.ankit.pdfeditor.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ankit.pdfeditor.model.PdfShape
import com.ankit.pdfeditor.model.PdfSignature
import com.ankit.pdfeditor.model.PdfStroke
import com.ankit.pdfeditor.model.ShapeType
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

internal fun DrawScope.drawNormalizedShape(shape: PdfShape) {
    val minDimension = minOf(size.width, size.height)
    val strokeWidthPx = shape.widthFraction * minDimension
    val startPx = Offset(shape.start.x * size.width, shape.start.y * size.height)
    val endPx = Offset(shape.end.x * size.width, shape.end.y * size.height)

    when (shape.type) {
        ShapeType.RECTANGLE -> {
            val left = minOf(startPx.x, endPx.x)
            val top = minOf(startPx.y, endPx.y)
            val width = abs(endPx.x - startPx.x)
            val height = abs(endPx.y - startPx.y)
            drawRect(
                color = shape.color,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(width, height),
                style = Stroke(width = strokeWidthPx)
            )
        }
        ShapeType.LINE, ShapeType.UNDERLINE, ShapeType.STRIKETHROUGH -> {
            drawLine(
                color = shape.color,
                start = startPx,
                end = endPx,
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
        }
        ShapeType.ARROW -> {
            drawLine(
                color = shape.color,
                start = startPx,
                end = endPx,
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )
            val angle = kotlin.math.atan2(endPx.y - startPx.y, endPx.x - startPx.x)
            val arrowLength = (strokeWidthPx * 4f).coerceAtLeast(16f)
            val arrowAngle = Math.toRadians(30.0)

            val p1 = Offset(
                endPx.x - arrowLength * kotlin.math.cos(angle - arrowAngle).toFloat(),
                endPx.y - arrowLength * kotlin.math.sin(angle - arrowAngle).toFloat()
            )
            val p2 = Offset(
                endPx.x - arrowLength * kotlin.math.cos(angle + arrowAngle).toFloat(),
                endPx.y - arrowLength * kotlin.math.sin(angle + arrowAngle).toFloat()
            )
            drawLine(color = shape.color, start = endPx, end = p1, strokeWidth = strokeWidthPx, cap = StrokeCap.Round)
            drawLine(color = shape.color, start = endPx, end = p2, strokeWidth = strokeWidthPx, cap = StrokeCap.Round)
        }
    }
}

internal fun DrawScope.drawNormalizedSignature(signature: PdfSignature) {
    val posX = signature.position.x * size.width
    val posY = signature.position.y * size.height
    val sigW = signature.size.x * size.width
    val sigH = signature.size.y * size.height

    signature.strokes.forEach { strokePoints ->
        if (strokePoints.isNotEmpty()) {
            val screenPoints = strokePoints.map { Offset(posX + it.x * sigW, posY + it.y * sigH) }
            drawPath(
                path = buildComposePath(screenPoints),
                color = signature.color,
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
        }
    }
}
