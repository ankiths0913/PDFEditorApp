package com.ankit.pdfeditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.ankit.pdfeditor.ui.PdfStudioApp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        enableEdgeToEdge()
        val intentUri = intent?.data ?: intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        setContent { PdfStudioApp(initialUri = intentUri) }
    }
}
