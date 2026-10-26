package com.ankit.pdfeditor.ui

import android.graphics.Color
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ankit.pdfeditor.model.PdfStroke
import com.ankit.pdfeditor.model.PdfText
import org.json.JSONArray
import org.json.JSONObject

internal class StudioViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    val pageStrokes = mutableStateMapOf<Int, List<PdfStroke>>()
    val pageTexts = mutableStateMapOf<Int, List<PdfText>>()

    init {
        restoreAnnotations(savedStateHandle.get<String>(ANNOTATIONS_KEY).orEmpty())
    }

    fun persistAnnotations() {
        val result = JSONObject()
        result.put("strokes", JSONArray().apply {
            pageStrokes.forEach { (page, strokes) ->
                strokes.forEach { stroke ->
                    put(JSONObject().apply {
                        put("page", page)
                        put("id", stroke.id)
                        put("points", JSONArray().apply {
                            stroke.points.forEach { point ->
                                put(JSONArray().put(point.x).put(point.y))
                            }
                        })
                        put("color", stroke.color.toAndroidColor())
                        put("width", stroke.widthFraction)
                    })
                }
            }
        })
        result.put("texts", JSONArray().apply {
            pageTexts.forEach { (page, texts) ->
                texts.forEach { text ->
                    put(JSONObject().apply {
                        put("page", page)
                        put("id", text.id)
                        put("text", text.text)
                        put("x", text.position.x)
                        put("y", text.position.y)
                        put("color", text.color.toAndroidColor())
                        put("size", text.sizeFraction)
                    })
                }
            }
        })
        savedStateHandle[ANNOTATIONS_KEY] = result.toString()
    }

    fun clearAnnotations() {
        pageStrokes.clear()
        pageTexts.clear()
        savedStateHandle.remove<String>(ANNOTATIONS_KEY)
    }

    private fun restoreAnnotations(raw: String) {
        if (raw.isBlank()) return
        runCatching {
            val root = JSONObject(raw)
            val strokes = root.optJSONArray("strokes").orEmpty()
            for (index in 0 until strokes.length()) {
                val value = strokes.getJSONObject(index)
                val points = value.getJSONArray("points").let { array ->
                    buildList {
                        for (pointIndex in 0 until array.length()) {
                            val point = array.getJSONArray(pointIndex)
                            add(Offset(point.getDouble(0).toFloat(), point.getDouble(1).toFloat()))
                        }
                    }
                }
                val page = value.getInt("page")
                pageStrokes[page] = pageStrokes[page].orEmpty() + PdfStroke(
                    id = value.getString("id"),
                    points = points,
                    color = ComposeColor(value.getInt("color")),
                    widthFraction = value.getDouble("width").toFloat()
                )
            }
            val texts = root.optJSONArray("texts").orEmpty()
            for (index in 0 until texts.length()) {
                val value = texts.getJSONObject(index)
                val page = value.getInt("page")
                pageTexts[page] = pageTexts[page].orEmpty() + PdfText(
                    id = value.getString("id"),
                    text = value.getString("text"),
                    position = Offset(
                        value.getDouble("x").toFloat(),
                        value.getDouble("y").toFloat()
                    ),
                    color = ComposeColor(value.getInt("color")),
                    sizeFraction = value.getDouble("size").toFloat()
                )
            }
        }
    }

    private fun ComposeColor.toAndroidColor(): Int = Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )

    private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

    private companion object {
        const val ANNOTATIONS_KEY = "annotations"
    }
}
