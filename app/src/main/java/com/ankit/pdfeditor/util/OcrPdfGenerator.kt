package com.ankit.pdfeditor.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal data class OcrPdfBox(
    val x: Float,
    val y: Float,
    val fontHeight: Float
)

internal fun mapOcrBoundsToPdf(
    left: Int,
    bottom: Int,
    rectHeight: Int,
    imageWidth: Int,
    imageHeight: Int,
    pdfWidth: Float,
    pdfHeight: Float
): OcrPdfBox {
    val scaleX = if (imageWidth > 0) pdfWidth / imageWidth else 1f
    val scaleY = if (imageHeight > 0) pdfHeight / imageHeight else 1f

    val x = left * scaleX
    val y = pdfHeight - (bottom * scaleY)
    val fontHeight = (rectHeight * scaleY).coerceAtLeast(6f)

    return OcrPdfBox(x, y, fontHeight)
}

internal fun mapOcrRectToPdf(
    rect: Rect,
    imageWidth: Int,
    imageHeight: Int,
    pdfWidth: Float,
    pdfHeight: Float
): OcrPdfBox = mapOcrBoundsToPdf(rect.left, rect.bottom, rect.height(), imageWidth, imageHeight, pdfWidth, pdfHeight)

internal suspend fun generateSearchablePdfFromImages(
    context: Context,
    imageUris: List<Uri>,
    onProgress: (pageNumber: Int, totalPages: Int) -> Unit = { _, _ -> }
): File? = withContext(Dispatchers.IO) {
    if (imageUris.isEmpty()) return@withContext null
    try {
        val doc = PDDocument()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val font = context.assets.open("NotoSans-Regular.ttf").use {
            PDType0Font.load(doc, it, true)
        }

        imageUris.forEachIndexed { index, imageUri ->
            onProgress(index + 1, imageUris.size)

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(imageUri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            val imgWidth = options.outWidth.coerceAtLeast(1)
            val imgHeight = options.outHeight.coerceAtLeast(1)

            val isLandscape = imgWidth > imgHeight
            val pdfWidth = if (isLandscape) 842f else 595f
            val pdfHeight = if (isLandscape) 595f else 842f

            val page = PDPage(PDRectangle(pdfWidth, pdfHeight))
            doc.addPage(page)

            // Perform ML Kit OCR Text Recognition
            val inputImage = InputImage.fromFilePath(context, imageUri)
            val visionText = Tasks.await(recognizer.process(inputImage))

            PDPageContentStream(doc, page).use { content ->
                // Draw background image
                context.contentResolver.openInputStream(imageUri)?.use { imgStream ->
                    val pdImage = JPEGFactory.createFromStream(doc, imgStream)
                    content.drawImage(pdImage, 0f, 0f, pdfWidth, pdfHeight)
                }

                // Enable invisible text layer (Mode 3: neither fill nor stroke text)
                content.appendRawCommands("3 Tr\n")

                visionText.textBlocks.forEach { block ->
                    block.lines.forEach { line ->
                        line.elements.forEach { element ->
                            val box = element.boundingBox
                            if (box != null && element.text.isNotBlank()) {
                                val mapped = mapOcrRectToPdf(box, imgWidth, imgHeight, pdfWidth, pdfHeight)
                                try {
                                    font.encode(element.text)
                                    content.beginText()
                                    content.setFont(font, mapped.fontHeight)
                                    content.setTextMatrix(Matrix(1f, 0f, 0f, 1f, mapped.x, mapped.y))
                                    content.showText(element.text)
                                    content.endText()
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }

                // Reset text rendering mode back to normal
                content.appendRawCommands("0 Tr\n")
            }
        }

        val outputFile = File(appDocumentsDir(context), "Searchable_OCR_${System.currentTimeMillis()}.pdf")
        doc.save(outputFile)
        doc.close()
        recognizer.close()
        outputFile
    } catch (_: Exception) {
        null
    }
}
