package com.ankit.pdfeditor.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class CloudSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val docsDir = appDocumentsDir(applicationContext)
            val files = docsDir.listFiles().orEmpty()
            files.forEach { file ->
                file.length()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
