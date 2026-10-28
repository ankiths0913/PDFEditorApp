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
                        val geometry = PdfPageGeometry(
                            width = crop.width,
                            height = crop.height,
                            cropLeft = crop.lowerLeftX,
                            cropBottom = crop.lowerLeftY,
                            rotation = page.rotation
                        )
                        PDPageContentStream(
                            doc,
                            page,
                            PDPageContentStream.AppendMode.APPEND,
                            true,
                            true
                        ).use { content ->
                            content.setNonStrokingColor(0f, 0f, 0f)
                            rects.forEach { rect ->
                                val first = mapNormalizedPointToPdf(
                                    androidx.compose.ui.geometry.Offset(rect.left, rect.top),
                                    geometry
                                )
                                val second = mapNormalizedPointToPdf(
                                    androidx.compose.ui.geometry.Offset(rect.right, rect.bottom),
                                    geometry
                                )
                                val x = minOf(first.x, second.x)
                                val y = minOf(first.y, second.y)
                                val w = kotlin.math.abs(second.x - first.x)
                                val h = kotlin.math.abs(second.y - first.y)
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
