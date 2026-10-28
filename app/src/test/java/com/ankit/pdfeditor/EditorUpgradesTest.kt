package com.ankit.pdfeditor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.ankit.pdfeditor.model.*
import com.ankit.pdfeditor.util.decodeAnnotations
import com.ankit.pdfeditor.util.encodeAnnotations
import com.ankit.pdfeditor.util.recentFilesFromJson
import com.ankit.pdfeditor.util.recentFilesJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EditorUpgradesTest {

    @Test
    fun testShapeAndSignatureJsonRoundTrip() {
        val shape = PdfShape(
            type = ShapeType.ARROW,
            start = Offset(0.1f, 0.1f),
            end = Offset(0.5f, 0.5f),
            color = Color.Green,
            widthFraction = 0.01f
        )
        val signature = PdfSignature(
            position = Offset(0.3f, 0.4f),
            size = Offset(0.2f, 0.1f),
            strokes = listOf(
                listOf(Offset(0f, 0f), Offset(0.1f, 0.1f))
            ),
            color = Color.Black
        )
        val textWithStyle = PdfText(
            text = "Styled Text",
            position = Offset(0.2f, 0.2f),
            color = Color.Red,
            isBold = true,
            backgroundColor = Color.Yellow
        )

        val json = encodeAnnotations(
            strokes = emptyMap(),
            texts = mapOf(1 to listOf(textWithStyle)),
            shapes = mapOf(1 to listOf(shape)),
            signatures = mapOf(1 to listOf(signature))
        )

        val snapshot = decodeAnnotations(json)

        val restoredShape = snapshot.shapes[1]?.singleOrNull()
        assertNotNull(restoredShape)
        assertEquals(ShapeType.ARROW, restoredShape?.type)
        assertEquals(0.1f, restoredShape?.start?.x ?: 0f, 0.001f)

        val restoredSig = snapshot.signatures[1]?.singleOrNull()
        assertNotNull(restoredSig)
        assertEquals(0.3f, restoredSig?.position?.x ?: 0f, 0.001f)

        val restoredText = snapshot.texts[1]?.singleOrNull()
        assertNotNull(restoredText)
        assertEquals(true, restoredText?.isBold)
        assertEquals(Color.Yellow.value.toLong(), restoredText?.backgroundColor?.value?.toLong())
    }

    @Test
    fun testRecentFileLastPagePersistence() {
        val item = RecentFileItem(
            uri = "content://test",
            fileName = "test.pdf",
            openedAt = "today",
            lastPage = 4
        )
        val json = recentFilesJson(listOf(item))
        val restored = recentFilesFromJson(json).firstOrNull()

        assertNotNull(restored)
        assertEquals(4, restored?.lastPage)
    }
}
