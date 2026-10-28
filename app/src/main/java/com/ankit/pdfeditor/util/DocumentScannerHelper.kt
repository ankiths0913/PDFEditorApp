package com.ankit.pdfeditor.util

import android.app.Activity
import android.content.Intent
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

internal fun buildDocumentScannerOptions(): GmsDocumentScannerOptions =
    GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setPageLimit(100)
        .setResultFormats(
            GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
            GmsDocumentScannerOptions.RESULT_FORMAT_PDF
        )
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .build()

internal fun getDocumentScannerClient(activity: Activity): GmsDocumentScanner =
    GmsDocumentScanning.getClient(buildDocumentScannerOptions())

internal fun parseScannerResult(data: Intent?): GmsDocumentScanningResult? {
    if (data == null) return null
    return GmsDocumentScanningResult.fromActivityResultIntent(data)
}
