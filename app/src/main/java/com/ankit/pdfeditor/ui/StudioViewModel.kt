package com.ankit.pdfeditor.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ankit.pdfeditor.model.AppScreen
import com.ankit.pdfeditor.model.EditorMode
import com.ankit.pdfeditor.model.PdfStroke
import com.ankit.pdfeditor.model.PdfText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
        runCatching {
            val root = JSONObject(annotationFile(uri).takeIf { it.exists() }?.readText().orEmpty())
            root.optJSONArray("strokes").forEachObject { value ->
                val page = value.getInt("page")
                val points = value.getJSONArray("points").let { pointsJson ->
                    buildList {
                        for (index in 0 until pointsJson.length()) {
                            val point = pointsJson.getJSONArray(index)
                            add(Offset(point.getDouble(0).toFloat(), point.getDouble(1).toFloat()))
                        }
                    }
                }
                pageStrokes[page] = pageStrokes[page].orEmpty() + PdfStroke(
                    id = value.getString("id"),
                    points = points,
                    color = Color(value.getLong("color").toULong()),
                    widthFraction = value.getDouble("width").toFloat()
                )
            }
            root.optJSONArray("texts").forEachObject { value ->
                val page = value.getInt("page")
                pageTexts[page] = pageTexts[page].orEmpty() + PdfText(
                    id = value.getString("id"),
                    text = value.getString("text"),
                    position = Offset(value.getDouble("x").toFloat(), value.getDouble("y").toFloat()),
                    color = Color(value.getLong("color").toULong()),
                    sizeFraction = value.getDouble("size").toFloat()
                )
            }
        }
    }

    fun schedulePersistAnnotations(uri: String?) {
        if (uri == null) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            val json = annotationsJson().toString()
            launch(Dispatchers.IO) { annotationFile(uri).writeText(json) }
        }
    }

    fun clearAnnotations(uri: String?, deletePersisted: Boolean) {
        pageStrokes.clear()
        pageTexts.clear()
        annotationHistory.clear()
        hasUnsavedChanges = false
        if (deletePersisted && uri != null) annotationFile(uri).delete()
    }

    private fun annotationsJson() = JSONObject().apply {
        put("strokes", JSONArray().apply {
            pageStrokes.forEach { (page, values) -> values.forEach { stroke ->
                put(JSONObject().apply {
                    put("page", page)
                    put("id", stroke.id)
                    put("points", JSONArray().apply {
                        stroke.points.forEach { put(JSONArray().put(it.x).put(it.y)) }
                    })
                    put("color", stroke.color.value.toLong())
                    put("width", stroke.widthFraction)
                })
            } }
        })
        put("texts", JSONArray().apply {
            pageTexts.forEach { (page, values) -> values.forEach { text ->
                put(JSONObject().apply {
                    put("page", page)
                    put("id", text.id)
                    put("text", text.text)
                    put("x", text.position.x)
                    put("y", text.position.y)
                    put("color", text.color.value.toLong())
                    put("size", text.sizeFraction)
                })
            } }
        })
    }

    private fun annotationFile(uri: String) =
        File(getApplication<Application>().filesDir, "annotations_${uri.hashCode()}.json")

    override fun onCleared() {
        saveJob?.cancel()
        pdfUri?.let { uri ->
            runCatching { annotationFile(uri).writeText(annotationsJson().toString()) }
        }
        super.onCleared()
    }

    private fun JSONArray?.forEachObject(action: (JSONObject) -> Unit) {
        if (this == null) return
        for (index in 0 until length()) action(getJSONObject(index))
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
