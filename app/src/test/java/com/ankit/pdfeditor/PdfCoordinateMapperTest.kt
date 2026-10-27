package com.ankit.pdfeditor

import androidx.compose.ui.geometry.Offset
import com.ankit.pdfeditor.util.PdfPageGeometry
import com.ankit.pdfeditor.util.mapNormalizedPointToPdf
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfCoordinateMapperTest {
    private val geometry = PdfPageGeometry(200f, 100f, 10f, 20f, 0)

    @Test fun mapsCropOriginAtZeroRotation() {
        assertEquals(Offset(10f, 120f), mapNormalizedPointToPdf(Offset(0f, 0f), geometry))
    }

    @Test fun mapsAllRotations() {
        assertEquals(Offset(10f, 20f), mapNormalizedPointToPdf(Offset(0f, 0f), geometry.copy(rotation = 90)))
        assertEquals(Offset(210f, 20f), mapNormalizedPointToPdf(Offset(0f, 0f), geometry.copy(rotation = 180)))
        assertEquals(Offset(210f, 120f), mapNormalizedPointToPdf(Offset(0f, 0f), geometry.copy(rotation = 270)))
    }
}
