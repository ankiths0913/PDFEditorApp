package com.ankit.pdfeditor.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ankit.pdfeditor.model.AppScreen
import com.ankit.pdfeditor.model.EditorMode
import com.ankit.pdfeditor.model.PdfStroke
import com.ankit.pdfeditor.model.PdfText
import com.ankit.pdfeditor.util.decodeAnnotations
import com.ankit.pdfeditor.util.encodeAnnotations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

internal class StudioViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val screenState = mutableStateOf(AppScreen.valueOf(savedStateHandle[SCREEN_KEY] ?: AppScreen.HOME.name))
    var currentScreen: AppScreen
        get() = screenState.value
        set(value) { screenState.value = value; savedStateHandle[SCREEN_KEY] = value.name }

    private val uriState = mutableStateOf<String?>(savedStateHandle[URI_KEY])
    var pdfUri: String?
        get() = uriState.value
        set(value) { uriState.value = value; savedStateHandle[URI_KEY] = value }

    private val pageState = mutableStateOf(savedStateHandle[PAGE_KEY] ?: 0)
    var currentPage: Int
        get() = pageState.value
        set(value) { pageState.value = value; savedStateHandle[PAGE_KEY] = value }

    private val zoomState = mutableStateOf(savedStateHandle[ZOOM_KEY] ?: 1f)
    var zoomLevel: Float
        get() = zoomState.value
        set(value) { zoomState.value = value; savedStateHandle[ZOOM_KEY] = value }

    private val modeState = mutableStateOf(EditorMode.valueOf(savedStateHandle[MODE_KEY] ?: EditorMode.VIEW.name))
    var editorMode: EditorMode
        get() = modeState.value
        set(value) { modeState.value = value; savedStateHandle[MODE_KEY] = value.name }

    private val colorState = mutableStateOf(
        Color(savedStateHandle.get<Long>(COLOR_KEY)?.toULong() ?: Color.Red.value)
    )
    var currentColor: Color
        get() = colorState.value
        set(value) { colorState.value = value; savedStateHandle[COLOR_KEY] = value.value.toLong() }

    private val widthState = mutableStateOf(savedStateHandle[WIDTH_KEY] ?: 8f)
    var strokeWidth: Float
        get() = widthState.value
        set(value) { widthState.value = value; savedStateHandle[WIDTH_KEY] = value }

    private val changedState = mutableStateOf(savedStateHandle[CHANGED_KEY] ?: false)
    var hasUnsavedChanges: Boolean
        get() = changedState.value
        set(value) { changedState.value = value; savedStateHandle[CHANGED_KEY] = value }

    val pageStrokes = mutableStateMapOf<Int, List<PdfStroke>>()
    val pageTexts = mutableStateMapOf<Int, List<PdfText>>()
    val annotationHistory = mutableStateListOf<AnnotationAction>()
    private var saveJob: Job? = null

    fun restoreAnnotations(uri: String?) {
        pageStrokes.clear()
        pageTexts.clear()
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val file = annotationFile(uri)
            if (!file.exists()) return@launch
            val raw = runCatching { file.readText() }.getOrNull() ?: return@launch
            if (raw.isBlank()) return@launch
            val snapshot = runCatching { decodeAnnotations(raw) }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) {
                pageStrokes.clear()
                pageTexts.clear()
                pageStrokes.putAll(snapshot.strokes)
                pageTexts.putAll(snapshot.texts)
            }
        }
    }

    fun schedulePersistAnnotations(uri: String?) {
        if (uri == null) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            val json = encodeAnnotations(pageStrokes, pageTexts)
            withContext(Dispatchers.IO) { annotationFile(uri).writeText(json) }
        }
    }

    fun clearAnnotations(uri: String?, deletePersisted: Boolean) {
        pageStrokes.clear()
        pageTexts.clear()
        annotationHistory.clear()
        hasUnsavedChanges = false
        if (deletePersisted && uri != null) {
            viewModelScope.launch(Dispatchers.IO) {
                annotationFile(uri).delete()
            }
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun annotationFile(uri: String) =
        File(getApplication<Application>().filesDir, "annotations_${sha256(uri)}.json")

    override fun onCleared() {
        saveJob?.cancel()
        pdfUri?.let { uri ->
            val json = encodeAnnotations(pageStrokes, pageTexts)
            runCatching { annotationFile(uri).writeText(json) }
        }
        super.onCleared()
    }

    internal sealed interface AnnotationAction {
        data class StrokeAdded(val page: Int, val stroke: PdfStroke) : AnnotationAction
        data class StrokeRemoved(val page: Int, val stroke: PdfStroke) : AnnotationAction
        data class TextAdded(val page: Int, val text: PdfText) : AnnotationAction
        data class TextRemoved(val page: Int, val text: PdfText) : AnnotationAction
    }

    private companion object {
        const val SCREEN_KEY = "screen"
        const val URI_KEY = "pdfUri"
        const val PAGE_KEY = "page"
        const val ZOOM_KEY = "zoom"
        const val MODE_KEY = "mode"
        const val COLOR_KEY = "color"
        const val WIDTH_KEY = "width"
        const val CHANGED_KEY = "changed"
    }
}
