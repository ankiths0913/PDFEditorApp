package com.ankit.pdfeditor.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.ankit.pdfeditor.model.RecentFileItem
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val PREFS_NAME = "pdf_studio_prefs"
internal const val RECENT_FILES_KEY = "recent_files"
internal const val FILE_PROVIDER_SUFFIX = ".fileprovider"
internal const val MAX_RECENT_FILES = 20
internal const val MAX_EXPORT_RENDER_DIMENSION = 2048
internal const val MAX_DISPLAY_RENDER_DIMENSION = 4096
internal const val DEFAULT_HIGHLIGHT_ALPHA = 0.7f

internal fun appDocumentsDir(context: Context): File =
    File(context.filesDir, "pdf_documents").apply { mkdirs() }

internal fun cleanBaseName(input: String): String =
    input.trim().removeSuffix(".pdf").removeSuffix(".PDF").trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100)

internal fun uniqueFile(dir: File, baseName: String): File {
    var candidate = File(dir, "$baseName.pdf")
    var i = 1
    while (candidate.exists()) {
        candidate = File(dir, "$baseName ($i).pdf")
        i++
    }
    return candidate
}

internal fun defaultScanName(): String =
    "Scan_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())

private fun appFileFromUri(context: Context, uri: Uri): File? =
    if (uri.authority == context.packageName + FILE_PROVIDER_SUFFIX) {
        uri.lastPathSegment?.let { File(appDocumentsDir(context), it) }
    } else {
        null
    }

internal fun renameLocalFile(file: File, newBaseName: String): File {
    val base = cleanBaseName(newBaseName)
    if (base.isBlank() || file.name == "$base.pdf") return file
    val target = uniqueFile(file.parentFile ?: return file, base)
    return if (file.renameTo(target)) target else file
}

internal suspend fun renamePdfDocument(context: Context, uri: Uri, newBaseName: String): Uri? =
    withContext(Dispatchers.IO) {
        runCatching {
            val base = cleanBaseName(newBaseName)
            if (base.isBlank()) return@runCatching null
            val appFile = appFileFromUri(context, uri)
            when {
                appFile != null -> {
                    if (!appFile.exists()) null
                    else if (appFile.name == "$base.pdf") uri
                    else {
                        val target = uniqueFile(appFile.parentFile!!, base)
                        if (appFile.renameTo(target)) fileProviderUri(context, target) else null
                    }
                }
                DocumentsContract.isDocumentUri(context, uri) ->
                    DocumentsContract.renameDocument(context.contentResolver, uri, "$base.pdf")
                else -> null
            }
        }.getOrNull()
    }

internal suspend fun deletePdfDocument(context: Context, uri: Uri): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val appFile = appFileFromUri(context, uri)
            when {
                appFile != null -> appFile.delete()
                DocumentsContract.isDocumentUri(context, uri) ->
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                else -> false
            }
        }.getOrDefault(false)
    }

internal fun copyUriToAppFile(context: Context, uri: Uri, fileName: String): File? =
    try {
        val target = File(appDocumentsDir(context), fileName)
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { source -> target.outputStream().use { output -> source.copyTo(output) } }
        target
    } catch (_: Exception) {
        null
    }

internal suspend fun copyFileToUri(context: Context, source: File, target: Uri): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(target, "wt")?.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
                true
            } ?: false
        }.getOrDefault(false)
    }

internal suspend fun copyFilesToFolder(context: Context, files: List<File>, treeUri: Uri): Int =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        var saved = 0
        files.forEach { file ->
            runCatching {
                val mime = if (file.extension.equals("png", ignoreCase = true)) {
                    "image/png"
                } else {
                    "image/jpeg"
                }
                val target = DocumentsContract.createDocument(resolver, parent, mime, file.name)
                if (target != null) {
                    resolver.openOutputStream(target)?.use { out ->
                        file.inputStream().use { input -> input.copyTo(out) }
                    }
                    saved++
                }
            }
        }
        saved
    }

internal fun cleanupGeneratedFiles(context: Context, recentItems: List<RecentFileItem>): List<RecentFileItem> {
    val referencedNames = recentItems.mapNotNull { Uri.parse(it.uri).lastPathSegment }.toSet()
    val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
    appDocumentsDir(context).listFiles()
        .orEmpty()
        .filter {
            it.name.startsWith("Converted_") ||
                it.name.startsWith("Scan_") ||
                it.name.startsWith("Merged_") ||
                it.name.startsWith("Split_") ||
                it.name.startsWith("Page_") ||
                it.name.startsWith("Compressed_") ||
                it.name.startsWith("Protected_") ||
                it.name.startsWith("Unlocked_") ||
                it.name.startsWith("Annotated_") ||
                it.name.startsWith("Filled_") ||
                it.name.startsWith("Redacted_") ||
                it.name.startsWith("Watermarked_") ||
                it.name.startsWith("Numbered_") ||
                it.name.startsWith("Searchable_OCR_") ||
                it.name.startsWith("ImagesToPdf_")
        }
        .filter { it.lastModified() < cutoff }
        .filterNot { it.name in referencedNames }
        .forEach { it.delete() }
    return recentItems.filter { isReadableUri(context, Uri.parse(it.uri)) }
}

internal fun isReadableUri(context: Context, uri: Uri): Boolean =
    runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)
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
