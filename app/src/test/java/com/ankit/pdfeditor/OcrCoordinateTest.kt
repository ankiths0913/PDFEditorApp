package com.ankit.pdfeditor

import com.ankit.pdfeditor.util.mapOcrBoundsToPdf
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrCoordinateTest {

    @Test
    fun testMapOcrBoundsToPdfCoordinates() {
        val left = 100
        val bottom = 250
        val rectHeight = 50
        val imageWidth = 1000
        val imageHeight = 2000
        val pdfWidth = 595f
        val pdfHeight = 842f

        val box = mapOcrBoundsToPdf(left, bottom, rectHeight, imageWidth, imageHeight, pdfWidth, pdfHeight)

        val expectedX = 100 * (595f / 1000f)
        val expectedY = 842f - (250 * (842f / 2000f))
        val expectedFontHeight = 50 * (842f / 2000f)

        assertEquals(expectedX, box.x, 0.001f)
        assertEquals(expectedY, box.y, 0.001f)
        assertEquals(expectedFontHeight, box.fontHeight, 0.001f)
    }
}
