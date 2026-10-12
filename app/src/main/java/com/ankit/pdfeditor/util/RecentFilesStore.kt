package com.ankit.pdfeditor.util

import android.content.Context
import androidx.core.content.edit
import com.ankit.pdfeditor.model.*
import org.json.JSONArray
import org.json.JSONObject

internal fun persistRecentFiles(context: Context, items: List<RecentFileItem>) {
    val array = JSONArray()
    items.take(MAX_RECENT_FILES).forEach { item ->
        array.put(
            JSONObject().apply {
                put("uri", item.uri)
                put("fileName", item.fileName)
                put("openedAt", item.openedAt)
                put("fileSizeBytes", item.fileSizeBytes)
                put("timestamp", item.timestamp)
                put("isFavorite", item.isFavorite)
            }
        )
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit {
            putString(RECENT_FILES_KEY, array.toString())
        }
}

internal fun loadRecentFiles(context: Context): List<RecentFileItem> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(RECENT_FILES_KEY, null)
        ?: return emptyList()

    return try {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    RecentFileItem(
                        uri = obj.getString("uri"),
                        fileName = obj.optString("fileName", "PDF Document"),
                        openedAt = obj.optString("openedAt", ""),
                        fileSizeBytes = obj.optLong("fileSizeBytes", -1L),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isFavorite = obj.optBoolean("isFavorite", false)
                    )
                )
            }
        }.sortedByDescending { it.timestamp }.take(MAX_RECENT_FILES)
    } catch (_: Exception) {
        emptyList()
    }
}
