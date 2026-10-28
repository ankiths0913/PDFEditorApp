package com.ankit.pdfeditor.util

import android.content.Context
import android.net.Uri
import com.ankit.pdfeditor.model.PdfOutlineItem
import com.ankit.pdfeditor.model.SearchMatch
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun extractPdfOutline(
    context: Context,
    uri: Uri
): List<PdfOutlineItem> = withContext(Dispatchers.IO) {
    try {
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext emptyList()
        input.use { stream ->
            PDDocument.load(stream, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val outline = doc.documentCatalog.documentOutline ?: return@withContext emptyList()
                parseOutlineNode(doc, outline)
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun parseOutlineNode(
    doc: PDDocument,
    node: PDOutlineNode
): List<PdfOutlineItem> {
    val items = mutableListOf<PdfOutlineItem>()
    var current: PDOutlineItem? = node.firstChild
    while (current != null) {
        val title = current.title.orEmpty().ifBlank { "Bookmark" }
        var pageIdx = 0
        try {
            val page = current.findDestinationPage(doc)
            if (page != null) {
                pageIdx = doc.pages.indexOf(page).coerceAtLeast(0)
            }
        } catch (_: Exception) {
        }
        val children = parseOutlineNode(doc, current)
        items.add(PdfOutlineItem(title = title, pageIndex = pageIdx, children = children))
        current = current.nextSibling
    }
    return items
}

internal suspend fun searchPdfText(
    context: Context,
    uri: Uri,
    query: String
): List<SearchMatch> = withContext(Dispatchers.IO) {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return@withContext emptyList()
    val matches = mutableListOf<SearchMatch>()
    try {
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext emptyList()
        input.use { stream ->
            PDDocument.load(stream, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val stripper = PDFTextStripper()
                for (p in 0 until doc.numberOfPages) {
                    stripper.startPage = p + 1
                    stripper.endPage = p + 1
                    val text = stripper.getText(doc)
                    if (text.isNotBlank()) {
                        var index = text.indexOf(trimmed, ignoreCase = true)
                        while (index >= 0) {
                            val start = (index - 20).coerceAtLeast(0)
                            val end = (index + trimmed.length + 20).coerceAtMost(text.length)
                            val snippet = "..." + text.substring(start, end).replace('\n', ' ') + "..."
                            matches.add(
                                SearchMatch(
                                    pageIndex = p,
                                    matchText = text.substring(index, index + trimmed.length),
                                    snippet = snippet
                                )
                            )
                            index = text.indexOf(trimmed, index + trimmed.length, ignoreCase = true)
                        }
                    }
                }
            }
        }
    } catch (_: Exception) {
    }
    matches
}
