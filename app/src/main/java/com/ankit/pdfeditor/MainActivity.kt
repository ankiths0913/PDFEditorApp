package com.ankit.pdfeditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import com.ankit.pdfeditor.ui.PdfStudioApp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        enableEdgeToEdge()
        val intentUri = intent?.data ?: IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (intentUri != null) {
            runCatching {
                if (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                    contentResolver.takePersistableUriPermission(
                        intentUri,
                        intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    )
                }
            }
        }
        setContent { PdfStudioApp(initialUri = intentUri, initialAction = intent?.action) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val intentUri = intent.data ?: IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        setContent { PdfStudioApp(initialUri = intentUri, initialAction = intent.action) }
    }
}
