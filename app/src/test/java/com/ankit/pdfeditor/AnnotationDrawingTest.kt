package com.ankit.pdfeditor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.ankit.pdfeditor.model.PdfStroke
import com.ankit.pdfeditor.util.distancePointToSegment
import com.ankit.pdfeditor.util.strokeDistance
import com.ankit.pdfeditor.util.decodeAnnotations
import com.ankit.pdfeditor.util.encodeAnnotations
import org.junit.Assert.assertEquals
import org.junit.Test

class AnnotationDrawingTest {
    @Test fun distanceToSegment() {
        assertEquals(1f, distancePointToSegment(Offset(1f, 1f), Offset(0f, 0f), Offset(2f, 0f)), 0.0001f)
    }

    @Test fun distanceToStroke() {
        val stroke = PdfStroke(points = listOf(Offset(0f, 0f), Offset(2f, 0f)), color = Color.Red)
        assertEquals(1f, strokeDistance(Offset(1f, 1f), stroke), 0.0001f)
    }

    @Test fun annotationJsonRoundTrips() {
        val stroke = PdfStroke(points = listOf(Offset(0f, 0f), Offset(1f, 1f)), color = Color.Red)
        val text = com.ankit.pdfeditor.model.PdfText(
            text = "हिंदी\ntext",
            position = Offset(.2f, .3f),
            color = Color.Blue
        )
        val restored = decodeAnnotations(
            encodeAnnotations(mapOf(2 to listOf(stroke)), mapOf(2 to listOf(text)))
        )
        assertEquals(stroke, restored.strokes[2]?.single())
        assertEquals(text, restored.texts[2]?.single())
    }
}
