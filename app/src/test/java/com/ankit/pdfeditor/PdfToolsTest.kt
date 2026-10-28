package com.ankit.pdfeditor

import com.ankit.pdfeditor.model.CompressQuality
import com.ankit.pdfeditor.model.NumberPosition
import com.ankit.pdfeditor.model.PageSpec
import com.ankit.pdfeditor.model.WatermarkConfig
import com.ankit.pdfeditor.model.WatermarkType
import com.ankit.pdfeditor.ui.tools.parsePageRange
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfToolsTest {

    @Test
    fun testParsePageRange_singlePagesAndRanges() {
        val result = parsePageRange("1-3, 5, 7-8", 10)
        assertEquals(listOf(0, 1, 2, 4, 6, 7), result)
    }

    @Test
    fun testParsePageRange_reverseRangeAndDuplicates() {
        val result = parsePageRange("3-1, 2, 2, 5-5", 10)
        assertEquals(listOf(0, 1, 2, 4), result)
    }

    @Test
    fun testParsePageRange_outOfBoundsCoerced() {
        val result = parsePageRange("0-2, 12", 5)
        assertEquals(listOf(0, 1), result)
    }

    @Test
    fun testPageSpec_rotationDelta() {
        val spec = PageSpec(originalPageIndex = 0, rotationDelta = 90)
        assertEquals(0, spec.originalPageIndex)
        assertEquals(90, spec.rotationDelta)
    }

    @Test
    fun testWatermarkConfig_defaults() {
        val config = WatermarkConfig(type = WatermarkType.PAGE_NUMBERS)
        assertEquals(WatermarkType.PAGE_NUMBERS, config.type)
        assertEquals(NumberPosition.BOTTOM_CENTER, config.position)
        assertEquals(0.3f, config.opacity, 0.001f)
    }

    @Test
    fun testCompressQuality_presets() {
        assertEquals("Low (Maximum Compression)", CompressQuality.LOW.label)
        assertEquals(0.3f, CompressQuality.LOW.qualityRatio, 0.001f)
        assertEquals(0.6f, CompressQuality.LOW.scaleFactor, 0.001f)

        assertEquals("Medium (Balanced)", CompressQuality.MEDIUM.label)
        assertEquals("High (High Quality)", CompressQuality.HIGH.label)
    }
}
