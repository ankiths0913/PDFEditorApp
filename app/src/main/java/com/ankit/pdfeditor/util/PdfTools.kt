package com.ankit.pdfeditor.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.ankit.pdfeditor.model.NumberPosition
import com.ankit.pdfeditor.model.PageSpec
import com.ankit.pdfeditor.model.WatermarkConfig
import com.ankit.pdfeditor.model.WatermarkType
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal suspend fun mergePdfs(context: Context, uris: List<Uri>): File? = withContext(Dispatchers.IO) {
    if (uris.size < 2) return@withContext null
    try {
        val merger = PDFMergerUtility()
        val outputFile = File(appDocumentsDir(context), "Merged_${System.currentTimeMillis()}.pdf")
        val outputStream = FileOutputStream(outputFile)
        val inputStreams = uris.map { uri -> context.contentResolver.openInputStream(uri) }
        if (inputStreams.any { it == null }) {
            inputStreams.forEach { runCatching { it?.close() } }
            outputStream.close()
            return@withContext null
        }
        inputStreams.filterNotNull().forEach { merger.addSource(it) }
        merger.destinationStream = outputStream
        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        inputStreams.forEach { runCatching { it?.close() } }
        outputStream.close()
        outputFile
    } catch (_: Exception) {
        null
    }
}

internal suspend fun splitPdf(context: Context, uri: Uri, pagesToKeep: List<Int>): File? = withContext(Dispatchers.IO) {
    if (pagesToKeep.isEmpty()) return@withContext null
    try {
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
        input.use { stream ->
            PDDocument.load(stream, MemoryUsageSetting.setupTempFileOnly()).use { srcDoc ->
                if (srcDoc.numberOfPages == 0) return@withContext null
                val newDoc = PDDocument()
                pagesToKeep.forEach { pageIdx ->
                    if (pageIdx in 0 until srcDoc.numberOfPages) {
                                newDoc.importPage(srcDoc.getPage(pageIdx))
                    }
                }
                if (newDoc.numberOfPages == 0) {
                    newDoc.close()
                    return@withContext null
                }
                val outputFile = File(appDocumentsDir(context), "Split_${System.currentTimeMillis()}.pdf")
                newDoc.save(outputFile)
                newDoc.close()
                outputFile
            }
        }
    } catch (_: Exception) {
        null
    }
}

internal suspend fun reorderRotateDeletePdf(
    context: Context,
    uri: Uri,
    pageSpecs: List<PageSpec>
): File? = withContext(Dispatchers.IO) {
    if (pageSpecs.isEmpty()) return@withContext null
    try {
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
        input.use { stream ->
            PDDocument.load(stream, MemoryUsageSetting.setupTempFileOnly()).use { srcDoc ->
                val newDoc = PDDocument()
                pageSpecs.forEach { spec ->
                    if (spec.originalPageIndex in 0 until srcDoc.numberOfPages) {
                        val page = srcDoc.getPage(spec.originalPageIndex)
                        if (spec.rotationDelta != 0) {
                            val currentRotation = page.rotation
                            page.rotation = ((currentRotation + spec.rotationDelta) % 360 + 360) % 360
                        }
                        newDoc.importPage(page)
                    }
                }
                if (newDoc.numberOfPages == 0) {
                    newDoc.close()
                    return@withContext null
                }
                val outputFile = File(appDocumentsDir(context), "Edited_${System.currentTimeMillis()}.pdf")
                newDoc.save(outputFile)
                newDoc.close()
                outputFile
            }
        }
    } catch (_: Exception) {
        null
    }
}

internal suspend fun compressPdf(
    context: Context,
    uri: Uri,
    qualityRatio: Float,
    scaleFactor: Float
): File? = withContext(Dispatchers.IO) {
    try {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
        val renderer = PdfRenderer(pfd)
        val newDoc = PDDocument()
        try {
            val jpegQuality = (qualityRatio * 100).toInt().coerceIn(10, 100)
            for (pageIdx in 0 until renderer.pageCount) {
                renderer.openPage(pageIdx).use { page ->
                    val renderWidth = (page.width * scaleFactor).toInt().coerceAtLeast(1)
                    val renderHeight = (page.height * scaleFactor).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                    bitmap.recycle()

                    val pdImage = JPEGFactory.createFromByteArray(newDoc, baos.toByteArray())
                    val pdPage = com.tom_roush.pdfbox.pdmodel.PDPage(
                        com.tom_roush.pdfbox.pdmodel.common.PDRectangle(page.width.toFloat(), page.height.toFloat())
                    )
                    newDoc.addPage(pdPage)
                    PDPageContentStream(newDoc, pdPage).use { content ->
                        content.drawImage(pdImage, 0f, 0f, page.width.toFloat(), page.height.toFloat())
                    }
                }
            }
            val outputFile = File(appDocumentsDir(context), "Compressed_${System.currentTimeMillis()}.pdf")
            newDoc.save(outputFile)
            outputFile
        } finally {
            newDoc.close()
            renderer.close()
            pfd.close()
        }
    } catch (_: Exception) {
        null
    }
}

internal suspend fun protectPdf(
    context: Context,
    uri: Uri,
    userPassword: String,
    ownerPassword: String
): File? = withContext(Dispatchers.IO) {
    try {
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
        input.use { stream ->
            PDDocument.load(stream, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val ap = AccessPermission()
                val spp = StandardProtectionPolicy(
                    ownerPassword.ifBlank { userPassword },
                    userPassword,
                    ap
                )
                spp.encryptionKeyLength = 256
                spp.permissions = ap
                doc.protect(spp)
                val outputFile = File(appDocumentsDir(context), "Protected_${System.currentTimeMillis()}.pdf")
                doc.save(outputFile)
                outputFile
            }
        }
    } catch (_: Exception) {
        null
    }
}

internal suspend fun unlockPdf(
    context: Context,
    uri: Uri,
    password: String
): File? = withContext(Dispatchers.IO) {
    try {
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
        input.use { stream ->
            PDDocument.load(stream, password, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                doc.isAllSecurityToBeRemoved = true
                val outputFile = File(appDocumentsDir(context), "Unlocked_${System.currentTimeMillis()}.pdf")
                doc.save(outputFile)
                outputFile
            }
        }
    } catch (_: Exception) {
        null
    }
}

internal suspend fun convertPdfToImages(
    context: Context,
    uri: Uri,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG
): List<File> = withContext(Dispatchers.IO) {
    val resultFiles = mutableListOf<File>()
    try {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext emptyList()
        val renderer = PdfRenderer(pfd)
        val ext = if (format == Bitmap.CompressFormat.JPEG) "jpg" else "png"
        val timestamp = System.currentTimeMillis()

        for (pageIdx in 0 until renderer.pageCount) {
            renderer.openPage(pageIdx).use { page ->
                val scale = minOf(
                    1f,
                    MAX_EXPORT_RENDER_DIMENSION.toFloat() / maxOf(page.width, page.height).toFloat()
                )
                val width = (page.width * scale * 2).toInt().coerceAtLeast(1)
                val height = (page.height * scale * 2).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val imageFile = File(appDocumentsDir(context), "Page_${pageIdx + 1}_$timestamp.$ext")
                FileOutputStream(imageFile).use { fos ->
                    bitmap.compress(format, 90, fos)
                }
                bitmap.recycle()
                resultFiles.add(imageFile)
            }
        }
        renderer.close()
        pfd.close()
    } catch (_: Exception) {
        resultFiles.forEach { runCatching { it.delete() } }
        return@withContext emptyList()
    }
    resultFiles
}

internal suspend fun convertImagesToPdfOnDevice(
    context: Context,
    imageUris: List<Uri>
): File? = withContext(Dispatchers.IO) {
    if (imageUris.isEmpty()) return@withContext null
    try {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val outputFile = File(appDocumentsDir(context), "ImagesToPdf_${System.currentTimeMillis()}.pdf")

        imageUris.forEachIndexed { index, imageUri ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                var sample = 1
                val largest = maxOf(bounds.outWidth, bounds.outHeight)
                while (largest / sample > MAX_EXPORT_RENDER_DIMENSION * 2) sample *= 2

                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
                if (bitmap != null) {
                    val landscape = bitmap.width > bitmap.height
                    val pageWidth = if (landscape) 842 else 595
                    val pageHeight = if (landscape) 595 else 842
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(
                        pageWidth,
                        pageHeight,
                        index + 1
                    ).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawColor(android.graphics.Color.WHITE)

                    val scale = minOf(
                        pageWidth.toFloat() / bitmap.width,
                        pageHeight.toFloat() / bitmap.height
                    )
                    val drawWidth = bitmap.width * scale
                    val drawHeight = bitmap.height * scale
                    val left = (pageWidth - drawWidth) / 2f
                    val top = (pageHeight - drawHeight) / 2f
                    page.canvas.drawBitmap(
                        bitmap,
                        null,
                        android.graphics.RectF(left, top, left + drawWidth, top + drawHeight),
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
                    )
                    pdfDocument.finishPage(page)
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
        }
        outputFile.outputStream().use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        outputFile
    } catch (_: Exception) {
        null
    }
}

internal suspend fun addWatermarkOrPageNumbers(
    context: Context,
    uri: Uri,
    config: WatermarkConfig
): File? = withContext(Dispatchers.IO) {
    try {
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
        input.use { stream ->
            PDDocument.load(stream, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val font = context.assets.open("NotoSans-Regular.ttf").use {
                    PDType0Font.load(doc, it, true)
                }
                val totalPages = doc.numberOfPages

                for (pageIdx in 0 until totalPages) {
                    val page = doc.getPage(pageIdx)
                    val crop = page.cropBox ?: page.mediaBox
                    val pageMin = minOf(crop.width, crop.height)

                    PDPageContentStream(
                        doc,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                    ).use { content ->
                        val graphicsState = PDExtendedGraphicsState().apply {
                            nonStrokingAlphaConstant = config.opacity
                        }
                        content.saveGraphicsState()
                        try {
                            content.setGraphicsStateParameters(graphicsState)
                            content.setNonStrokingColor(0f, 0f, 0f)

                            if (config.type == WatermarkType.WATERMARK_TEXT) {
                                val text = config.text.ifBlank { "CONFIDENTIAL" }
                                val fontSize = pageMin * 0.08f
                                content.beginText()
                                content.setFont(font, fontSize)
                                                val textWidth = font.getStringWidth(text) / 1000f * fontSize
                                val textMatrix = Matrix.getRotateInstance(
                                    Math.toRadians(45.0),
                                    crop.lowerLeftX + crop.width / 2f - textWidth / 2f,
                                    crop.lowerLeftY + crop.height / 2f
                                )
                                content.setTextMatrix(textMatrix)
                                content.showText(text)
                                content.endText()
                            } else {
                                val text = "Page ${pageIdx + 1} of $totalPages"
                                val fontSize = pageMin * 0.035f
                                val textWidth = font.getStringWidth(text) / 1000f * fontSize

                                val (x, y) = when (config.position) {
                                    NumberPosition.BOTTOM_CENTER -> Pair(crop.lowerLeftX + crop.width / 2f - textWidth / 2f, crop.lowerLeftY + 25f)
                                    NumberPosition.BOTTOM_RIGHT -> Pair(crop.lowerLeftX + crop.width - textWidth - 30f, crop.lowerLeftY + 25f)
                                    NumberPosition.TOP_RIGHT -> Pair(crop.lowerLeftX + crop.width - textWidth - 30f, crop.lowerLeftY + crop.height - 35f)
                                    NumberPosition.TOP_CENTER -> Pair(crop.lowerLeftX + crop.width / 2f - textWidth / 2f, crop.lowerLeftY + crop.height - 35f)
                                }

                                content.beginText()
                                content.setFont(font, fontSize)
                                content.setTextMatrix(Matrix(1f, 0f, 0f, 1f, x, y))
                                content.showText(text)
                                content.endText()
                            }
                        } finally {
                            content.restoreGraphicsState()
                        }
                    }
                }
                val prefix = if (config.type == WatermarkType.WATERMARK_TEXT) "Watermarked_" else "Numbered_"
                val outputFile = File(appDocumentsDir(context), "$prefix${System.currentTimeMillis()}.pdf")
                doc.save(outputFile)
                outputFile
            }
        }
    } catch (_: Exception) {
        null
    }
}
