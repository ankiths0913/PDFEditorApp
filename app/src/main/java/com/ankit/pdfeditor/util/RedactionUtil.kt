package com.ankit.pdfeditor.util

import android.content.Context
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal suspend fun applyRedactions(
    context: Context,
    uri: Uri,
    redactionsByPage: Map<Int, List<Rect>>
): File? = withContext(Dispatchers.IO) {
    if (redactionsByPage.isEmpty()) return@withContext null
    try {
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
        input.use { stream ->
            PDDocument.load(stream, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                redactionsByPage.forEach { (pageIdx, rects) ->
                    if (pageIdx in 0 until doc.numberOfPages) {
                        val page = doc.getPage(pageIdx)
                        val crop = page.cropBox ?: page.mediaBox
                        PDPageContentStream(
                            doc,
                            page,
                            PDPageContentStream.AppendMode.APPEND,
                            true,
                            true
                        ).use { content ->
                            content.setNonStrokingColor(0f, 0f, 0f)
                            rects.forEach { rect ->
                                val x = crop.lowerLeftX + rect.left * crop.width
                                val y = crop.lowerLeftY + (1f - rect.bottom) * crop.height
                                val w = rect.width * crop.width
                                val h = rect.height * crop.height
                                content.addRect(x, y, w, h)
                                content.fill()
                            }
                        }
                    }
                }
                val outputFile = File(appDocumentsDir(context), "Redacted_${System.currentTimeMillis()}.pdf")
                doc.save(outputFile)
                outputFile
            }
        }
    } catch (_: Exception) {
        null
    }
}
