package com.ankit.pdfeditor.util

import android.content.Context
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import com.ankit.pdfeditor.model.FormFieldItem
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal suspend fun extractFormFields(
    context: Context,
    uri: Uri
): List<FormFieldItem> = withContext(Dispatchers.IO) {
    val items = mutableListOf<FormFieldItem>()
    try {
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext emptyList()
        input.use { stream ->
            PDDocument.load(stream, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val acroForm = doc.documentCatalog.acroForm ?: return@withContext emptyList()
                val fields = acroForm.fieldTree
                for (field in fields) {
                    val widgets = field.widgets
                    for (widget in widgets) {
                        val page = widget.page
                        if (page != null) {
                            val pageIndex = doc.pages.indexOf(page)
                            val rect = widget.rectangle
                            if (rect != null) {
                                val crop = page.cropBox ?: page.mediaBox
                                val x = (rect.lowerLeftX - crop.lowerLeftX) / crop.width
                                val y = 1f - ((rect.upperRightY - crop.lowerLeftY) / crop.height)
                                val w = rect.width / crop.width
                                val h = rect.height / crop.height
                                items.add(
                                    FormFieldItem(
                                        name = field.fullyQualifiedName.orEmpty().ifBlank { "Field" },
                                        value = field.valueAsString.orEmpty(),
                                        type = field.fieldType.orEmpty(),
                                        pageIndex = pageIndex.coerceAtLeast(0),
                                        rect = Rect(x, y, x + w, y + h)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    } catch (_: Exception) {
    }
    items
}

internal suspend fun saveFilledFormPdf(
    context: Context,
    uri: Uri,
    fieldValues: Map<String, String>
): File? = withContext(Dispatchers.IO) {
    try {
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
        input.use { stream ->
            PDDocument.load(stream, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val acroForm = doc.documentCatalog.acroForm ?: return@withContext null
                fieldValues.forEach { (name, value) ->
                    val field = acroForm.getField(name)
                    field?.setValue(value)
                }
                if (acroForm.fields.isNotEmpty()) {
                    acroForm.flatten()
                }
                val outputFile = File(appDocumentsDir(context), "Filled_${System.currentTimeMillis()}.pdf")
                doc.save(outputFile)
                outputFile
            }
        }
    } catch (_: Exception) {
        null
    }
}
