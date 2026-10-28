package com.ankit.pdfeditor.util

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PdfDiffResult(
    val pageIndex: Int,
    val text1: String,
    val text2: String,
    val isIdentical: Boolean
)

internal suspend fun comparePdfs(
    context: Context,
    uri1: Uri,
    uri2: Uri
): List<PdfDiffResult> = withContext(Dispatchers.IO) {
    val results = mutableListOf<PdfDiffResult>()
    try {
        val in1 = context.contentResolver.openInputStream(uri1) ?: return@withContext emptyList()
        val in2 = context.contentResolver.openInputStream(uri2) ?: return@withContext emptyList()

        in1.use { s1 ->
            in2.use { s2 ->
                PDDocument.load(s1, MemoryUsageSetting.setupTempFileOnly()).use { doc1 ->
                    PDDocument.load(s2, MemoryUsageSetting.setupTempFileOnly()).use { doc2 ->
                        val maxPages = maxOf(doc1.numberOfPages, doc2.numberOfPages)
                        val stripper = PDFTextStripper()
                        for (p in 0 until maxPages) {
                            stripper.startPage = p + 1
                            stripper.endPage = p + 1
                            val t1 = if (p < doc1.numberOfPages) stripper.getText(doc1).trim() else ""
                            val t2 = if (p < doc2.numberOfPages) stripper.getText(doc2).trim() else ""
                            results.add(
                                PdfDiffResult(
                                    pageIndex = p,
                                    text1 = t1,
                                    text2 = t2,
                                    isIdentical = t1 == t2
                                )
                            )
                        }
                    }
                }
            }
        }
    } catch (_: Exception) {
    }
    results
}
