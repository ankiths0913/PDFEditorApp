package com.ankit.pdfeditor.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.graphics.scale
import java.io.File

internal fun convertImageToPdfOnDevice(context: Context, imageUri: Uri): File? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (largest / sample > MAX_EXPORT_RENDER_DIMENSION * 2) sample *= 2

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null

        val finalBitmap = if (maxOf(bitmap.width, bitmap.height) > MAX_EXPORT_RENDER_DIMENSION) {
            val ratio = MAX_EXPORT_RENDER_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height)
            val targetWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
            val targetHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)

            if (targetWidth == bitmap.width && targetHeight == bitmap.height) {
                bitmap
            } else {
                val scaledBitmap = bitmap.scale(targetWidth, targetHeight)
                if (scaledBitmap !== bitmap && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
                scaledBitmap
            }
        } else {
            bitmap
        }

        val outputFile = File(
            appDocumentsDir(context),
            "Converted_${System.currentTimeMillis()}.pdf"
        )
        val pdfDocument = PdfDocument()
        try {
            val pageWidth = 612
            val pageHeight = 792
            val pageInfo = PdfDocument.PageInfo.Builder(
                pageWidth,
                pageHeight,
                1
            ).create()
            val page = pdfDocument.startPage(pageInfo)
            page.canvas.drawColor(Color.WHITE)
            val scale = minOf(
                pageWidth.toFloat() / finalBitmap.width,
                pageHeight.toFloat() / finalBitmap.height
            )
            val drawWidth = finalBitmap.width * scale
            val drawHeight = finalBitmap.height * scale
            val left = (pageWidth - drawWidth) / 2f
            val top = (pageHeight - drawHeight) / 2f
            page.canvas.drawBitmap(
                finalBitmap,
                null,
                android.graphics.RectF(left, top, left + drawWidth, top + drawHeight),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            )
            pdfDocument.finishPage(page)
            outputFile.outputStream().use { pdfDocument.writeTo(it) }
        } finally {
            pdfDocument.close()
            if (!finalBitmap.isRecycled) finalBitmap.recycle()
        }
        outputFile
    } catch (_: Exception) {
        null
    }
}
