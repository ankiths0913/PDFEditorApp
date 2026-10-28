package com.ankit.pdfeditor.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ankit.pdfeditor.model.*
import com.ankit.pdfeditor.util.PREFS_NAME
import com.ankit.pdfeditor.util.decodeAnnotations
import com.ankit.pdfeditor.util.encodeAnnotations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
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

    var textIsBold = mutableStateOf(false)
    var textBgColor = mutableStateOf<Color?>(null)

    val pageStrokes = mutableStateMapOf<Int, List<PdfStroke>>()
    val pageTexts = mutableStateMapOf<Int, List<PdfText>>()
    val pageShapes = mutableStateMapOf<Int, List<PdfShape>>()
    val pageSignatures = mutableStateMapOf<Int, List<PdfSignature>>()

    val annotationHistory = mutableStateListOf<AnnotationAction>()
    val redoHistory = mutableStateListOf<AnnotationAction>()

    val savedSignatures = mutableStateListOf<List<List<Offset>>>()

    private var saveJob: Job? = null

    init {
        loadSavedSignatures()
    }

    fun pushAction(action: AnnotationAction) {
        annotationHistory.add(action)
        redoHistory.clear()
        if (annotationHistory.size > 50) annotationHistory.removeAt(0)
        hasUnsavedChanges = true
        schedulePersistAnnotations(pdfUri)
    }

    fun undo() {
        if (annotationHistory.isEmpty()) return
        val action = annotationHistory.removeAt(annotationHistory.size - 1)
        redoHistory.add(action)
        applyReverseAction(action)
        hasUnsavedChanges = pageStrokes.values.any { it.isNotEmpty() } ||
                pageTexts.values.any { it.isNotEmpty() } ||
                pageShapes.values.any { it.isNotEmpty() } ||
                pageSignatures.values.any { it.isNotEmpty() }
        schedulePersistAnnotations(pdfUri)
    }

    fun redo() {
        if (redoHistory.isEmpty()) return
        val action = redoHistory.removeAt(redoHistory.size - 1)
        annotationHistory.add(action)
        applyAction(action)
        hasUnsavedChanges = true
        schedulePersistAnnotations(pdfUri)
    }

    private fun applyAction(action: AnnotationAction) {
        when (action) {
            is AnnotationAction.StrokeAdded -> {
                pageStrokes[action.page] = pageStrokes[action.page].orEmpty() + action.stroke
            }
            is AnnotationAction.StrokeRemoved -> {
                pageStrokes[action.page] = pageStrokes[action.page].orEmpty().filterNot { it.id == action.stroke.id }
            }
            is AnnotationAction.TextAdded -> {
                pageTexts[action.page] = pageTexts[action.page].orEmpty() + action.text
            }
            is AnnotationAction.TextRemoved -> {
                pageTexts[action.page] = pageTexts[action.page].orEmpty().filterNot { it.id == action.text.id }
            }
            is AnnotationAction.ShapeAdded -> {
                pageShapes[action.page] = pageShapes[action.page].orEmpty() + action.shape
            }
            is AnnotationAction.ShapeRemoved -> {
                pageShapes[action.page] = pageShapes[action.page].orEmpty().filterNot { it.id == action.shape.id }
            }
            is AnnotationAction.SignatureAdded -> {
                pageSignatures[action.page] = pageSignatures[action.page].orEmpty() + action.signature
            }
            is AnnotationAction.SignatureRemoved -> {
                pageSignatures[action.page] = pageSignatures[action.page].orEmpty().filterNot { it.id == action.signature.id }
            }
        }
    }

    private fun applyReverseAction(action: AnnotationAction) {
        when (action) {
            is AnnotationAction.StrokeAdded -> {
                pageStrokes[action.page] = pageStrokes[action.page].orEmpty().filterNot { it.id == action.stroke.id }
            }
            is AnnotationAction.StrokeRemoved -> {
                pageStrokes[action.page] = pageStrokes[action.page].orEmpty() + action.stroke
            }
            is AnnotationAction.TextAdded -> {
                pageTexts[action.page] = pageTexts[action.page].orEmpty().filterNot { it.id == action.text.id }
            }
            is AnnotationAction.TextRemoved -> {
                pageTexts[action.page] = pageTexts[action.page].orEmpty() + action.text
            }
            is AnnotationAction.ShapeAdded -> {
                pageShapes[action.page] = pageShapes[action.page].orEmpty().filterNot { it.id == action.shape.id }
            }
            is AnnotationAction.ShapeRemoved -> {
                pageShapes[action.page] = pageShapes[action.page].orEmpty() + action.shape
            }
            is AnnotationAction.SignatureAdded -> {
                pageSignatures[action.page] = pageSignatures[action.page].orEmpty().filterNot { it.id == action.signature.id }
            }
            is AnnotationAction.SignatureRemoved -> {
                pageSignatures[action.page] = pageSignatures[action.page].orEmpty() + action.signature
            }
        }
    }

    fun restoreAnnotations(uri: String?) {
        pageStrokes.clear()
        pageTexts.clear()
        pageShapes.clear()
        pageSignatures.clear()
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
                pageShapes.clear()
                pageSignatures.clear()
                pageStrokes.putAll(snapshot.strokes)
                pageTexts.putAll(snapshot.texts)
                pageShapes.putAll(snapshot.shapes)
                pageSignatures.putAll(snapshot.signatures)
            }
        }
    }

    fun schedulePersistAnnotations(uri: String?) {
        if (uri == null) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            val json = encodeAnnotations(pageStrokes, pageTexts, pageShapes, pageSignatures)
            withContext(Dispatchers.IO) { annotationFile(uri).writeText(json) }
        }
    }

    fun clearAnnotations(uri: String?, deletePersisted: Boolean) {
        pageStrokes.clear()
        pageTexts.clear()
        pageShapes.clear()
        pageSignatures.clear()
        annotationHistory.clear()
        redoHistory.clear()
        hasUnsavedChanges = false
        if (deletePersisted && uri != null) {
            viewModelScope.launch(Dispatchers.IO) {
                annotationFile(uri).delete()
            }
        }
    }

    fun saveSignaturePreset(strokes: List<List<Offset>>) {
        if (strokes.isEmpty()) return
        savedSignatures.add(strokes)
        persistSavedSignatures()
    }

    private fun loadSavedSignatures() {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString("saved_signatures", null) ?: return
        runCatching {
            val array = JSONArray(raw)
            savedSignatures.clear()
            for (i in 0 until array.length()) {
                val strokeArray = array.getJSONArray(i)
                val strokesList = buildList {
                    for (s in 0 until strokeArray.length()) {
                        val ptsArray = strokeArray.getJSONArray(s)
                        add(buildList {
                            for (p in 0 until ptsArray.length()) {
                                val pt = ptsArray.getJSONArray(p)
                                add(Offset(pt.getDouble(0).toFloat(), pt.getDouble(1).toFloat()))
                            }
                        })
                    }
                }
                savedSignatures.add(strokesList)
            }
        }
    }

    private fun persistSavedSignatures() {
        val array = JSONArray()
        savedSignatures.forEach { sigStrokes ->
            val strokeArr = JSONArray()
            sigStrokes.forEach { strokePts ->
                val ptsArr = JSONArray()
                strokePts.forEach { pt ->
                    ptsArr.put(JSONArray().put(pt.x).put(pt.y))
                }
                strokeArr.put(ptsArr)
            }
            array.put(strokeArr)
        }
        getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString("saved_signatures", array.toString())
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
            val json = encodeAnnotations(pageStrokes, pageTexts, pageShapes, pageSignatures)
            runCatching { annotationFile(uri).writeText(json) }
        }
        super.onCleared()
    }

    internal sealed interface AnnotationAction {
        data class StrokeAdded(val page: Int, val stroke: PdfStroke) : AnnotationAction
        data class StrokeRemoved(val page: Int, val stroke: PdfStroke) : AnnotationAction
        data class TextAdded(val page: Int, val text: PdfText) : AnnotationAction
        data class TextRemoved(val page: Int, val text: PdfText) : AnnotationAction
        data class ShapeAdded(val page: Int, val shape: PdfShape) : AnnotationAction
        data class ShapeRemoved(val page: Int, val shape: PdfShape) : AnnotationAction
        data class SignatureAdded(val page: Int, val signature: PdfSignature) : AnnotationAction
        data class SignatureRemoved(val page: Int, val signature: PdfSignature) : AnnotationAction
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
