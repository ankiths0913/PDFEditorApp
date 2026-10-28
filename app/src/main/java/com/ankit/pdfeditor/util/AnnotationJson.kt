package com.ankit.pdfeditor.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.ankit.pdfeditor.model.*
import org.json.JSONArray
import org.json.JSONObject

internal data class AnnotationSnapshot(
    val strokes: Map<Int, List<PdfStroke>> = emptyMap(),
    val texts: Map<Int, List<PdfText>> = emptyMap(),
    val shapes: Map<Int, List<PdfShape>> = emptyMap(),
    val signatures: Map<Int, List<PdfSignature>> = emptyMap()
)

internal fun encodeAnnotations(
    strokes: Map<Int, List<PdfStroke>>,
    texts: Map<Int, List<PdfText>>,
    shapes: Map<Int, List<PdfShape>> = emptyMap(),
    signatures: Map<Int, List<PdfSignature>> = emptyMap()
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
                put("isBold", text.isBold)
                text.backgroundColor?.let { put("bgColor", it.value.toLong()) }
            })
        } }
    })
    put("shapes", JSONArray().apply {
        shapes.forEach { (page, values) -> values.forEach { shape ->
            put(JSONObject().apply {
                put("page", page)
                put("id", shape.id)
                put("type", shape.type.name)
                put("startX", shape.start.x)
                put("startY", shape.start.y)
                put("endX", shape.end.x)
                put("endY", shape.end.y)
                put("color", shape.color.value.toLong())
                put("width", shape.widthFraction)
            })
        } }
    })
    put("signatures", JSONArray().apply {
        signatures.forEach { (page, values) -> values.forEach { sig ->
            put(JSONObject().apply {
                put("page", page)
                put("id", sig.id)
                put("x", sig.position.x)
                put("y", sig.position.y)
                put("w", sig.size.x)
                put("h", sig.size.y)
                put("color", sig.color.value.toLong())
                put("strokes", JSONArray().apply {
                    sig.strokes.forEach { strokePoints ->
                        put(JSONArray().apply {
                            strokePoints.forEach { put(JSONArray().put(it.x).put(it.y)) }
                        })
                    }
                })
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
        val bgColor = if (value.has("bgColor")) Color(value.getLong("bgColor").toULong()) else null
        texts[page] = texts[page].orEmpty() + PdfText(
            id = value.getString("id"),
            text = value.getString("text"),
            position = Offset(value.getDouble("x").toFloat(), value.getDouble("y").toFloat()),
            color = Color(value.getLong("color").toULong()),
            sizeFraction = value.getDouble("size").toFloat(),
            isBold = value.optBoolean("isBold", false),
            backgroundColor = bgColor
        )
    }

    val shapes = mutableMapOf<Int, List<PdfShape>>()
    val shapeArray = root.optJSONArray("shapes") ?: JSONArray()
    for (index in 0 until shapeArray.length()) {
        val value = shapeArray.getJSONObject(index)
        val page = value.getInt("page")
        shapes[page] = shapes[page].orEmpty() + PdfShape(
            id = value.getString("id"),
            type = ShapeType.valueOf(value.getString("type")),
            start = Offset(value.getDouble("startX").toFloat(), value.getDouble("startY").toFloat()),
            end = Offset(value.getDouble("endX").toFloat(), value.getDouble("endY").toFloat()),
            color = Color(value.getLong("color").toULong()),
            widthFraction = value.getDouble("width").toFloat()
        )
    }

    val signatures = mutableMapOf<Int, List<PdfSignature>>()
    val sigArray = root.optJSONArray("signatures") ?: JSONArray()
    for (index in 0 until sigArray.length()) {
        val value = sigArray.getJSONObject(index)
        val page = value.getInt("page")
        val strokesJson = value.getJSONArray("strokes")
        val sigStrokes = buildList {
            for (i in 0 until strokesJson.length()) {
                val pArray = strokesJson.getJSONArray(i)
                add(buildList {
                    for (j in 0 until pArray.length()) {
                        val pt = pArray.getJSONArray(j)
                        add(Offset(pt.getDouble(0).toFloat(), pt.getDouble(1).toFloat()))
                    }
                })
            }
        }
        signatures[page] = signatures[page].orEmpty() + PdfSignature(
            id = value.getString("id"),
            position = Offset(value.getDouble("x").toFloat(), value.getDouble("y").toFloat()),
            size = Offset(value.getDouble("w").toFloat(), value.getDouble("h").toFloat()),
            strokes = sigStrokes,
            color = Color(value.getLong("color").toULong())
        )
    }

    return AnnotationSnapshot(strokes, texts, shapes, signatures)
}
