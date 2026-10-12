package com.ankit.pdfeditor.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.Date

internal const val PREFS_NAME = "pdf_studio_prefs"
internal const val RECENT_FILES_KEY = "recent_files"
internal const val FILE_PROVIDER_SUFFIX = ".fileprovider"
internal const val MAX_RECENT_FILES = 20
internal const val MAX_EXPORT_RENDER_DIMENSION = 2048

internal fun appDocumentsDir(context: Context): File =
    File(context.filesDir, "pdf_documents").apply { mkdirs() }

internal fun fileProviderUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, file)

internal fun safeFileName(name: String): String {
    val normalized = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return normalized.ifBlank { "document.pdf" }
}

internal fun dateString(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())

internal fun resolveFileName(context: Context, uri: Uri): String {
    if (uri.scheme == "file") return File(uri.path.orEmpty()).name.ifBlank { "PDF Document" }
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else "PDF Document"
            } else {
                "PDF Document"
            }
        } ?: "PDF Document"
    } catch (_: Exception) {
        "PDF Document"
    }
}

internal fun resolveFileSize(context: Context, uri: Uri): Long {
    if (uri.scheme == "file") return File(uri.path.orEmpty()).length().takeIf { it > 0L } ?: -1L
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else -1L
            } else {
                -1L
            }
        } ?: -1L
    } catch (_: Exception) {
        -1L
    }
}

internal fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes < 0L) return "Size unknown"
    if (sizeBytes < 1024L) return "$sizeBytes B"
    val kb = sizeBytes / 1024.0
    if (kb < 1024.0) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

internal fun sharePdf(context: Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("PDF", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF via"))
    } catch (_: Exception) {
        throw IOException("No compatible app is available to share this PDF")
    }
}
