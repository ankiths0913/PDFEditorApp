package com.ankit.pdfeditor.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.ankit.pdfeditor.model.PdfStroke
import com.ankit.pdfeditor.model.PdfText
import org.json.JSONArray
import org.json.JSONObject

internal data class AnnotationSnapshot(
    val strokes: Map<Int, List<PdfStroke>>,
    val texts: Map<Int, List<PdfText>>
)

internal fun encodeAnnotations(
    strokes: Map<Int, List<PdfStroke>>,
    texts: Map<Int, List<PdfText>>
): String = JSONObject().apply {
    put("strokes", JSONArray().apply {
        strokes.forEach { (page, values) -> values.forEach { stroke ->
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
        texts.forEach { (page, values) -> values.forEach { text ->
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
}.toString()

internal fun decodeAnnotations(raw: String): AnnotationSnapshot {
    val root = JSONObject(raw)
    val strokes = mutableMapOf<Int, List<PdfStroke>>()
    val strokeArray = root.optJSONArray("strokes") ?: JSONArray()
    for (index in 0 until strokeArray.length()) {
        val value = strokeArray.getJSONObject(index)
        val pointsJson = value.getJSONArray("points")
        val points = buildList {
            for (pointIndex in 0 until pointsJson.length()) {
                val point = pointsJson.getJSONArray(pointIndex)
                add(Offset(point.getDouble(0).toFloat(), point.getDouble(1).toFloat()))
            }
        }
        val page = value.getInt("page")
        strokes[page] = strokes[page].orEmpty() + PdfStroke(
            id = value.getString("id"),
            points = points,
            color = Color(value.getLong("color").toULong()),
            widthFraction = value.getDouble("width").toFloat()
        )
    }
    val texts = mutableMapOf<Int, List<PdfText>>()
    val textArray = root.optJSONArray("texts") ?: JSONArray()
    for (index in 0 until textArray.length()) {
        val value = textArray.getJSONObject(index)
        val page = value.getInt("page")
        texts[page] = texts[page].orEmpty() + PdfText(
            id = value.getString("id"),
            text = value.getString("text"),
            position = Offset(value.getDouble("x").toFloat(), value.getDouble("y").toFloat()),
            color = Color(value.getLong("color").toULong()),
            sizeFraction = value.getDouble("size").toFloat()
        )
    }
    return AnnotationSnapshot(strokes, texts)
}
