package com.ankit.pdfeditor.util

import android.content.Context
import androidx.core.content.edit
import com.ankit.pdfeditor.model.RecentFileItem
import org.json.JSONArray
import org.json.JSONObject

internal fun persistRecentFiles(context: Context, items: List<RecentFileItem>) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
        putString(RECENT_FILES_KEY, recentFilesJson(items))
    }
}

internal fun loadRecentFiles(context: Context): List<RecentFileItem> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(RECENT_FILES_KEY, null) ?: return emptyList()
    return runCatching { recentFilesFromJson(raw) }.getOrDefault(emptyList())
}

internal fun recentFilesJson(items: List<RecentFileItem>): String = JSONArray().apply {
    items.take(MAX_RECENT_FILES).forEach { item ->
        put(JSONObject().apply {
            put("uri", item.uri)
            put("fileName", item.fileName)
            put("openedAt", item.openedAt)
            put("fileSizeBytes", item.fileSizeBytes)
            put("timestamp", item.timestamp)
            put("isFavorite", item.isFavorite)
            put("lastPage", item.lastPage)
        })
    }
}.toString()

internal fun recentFilesFromJson(raw: String): List<RecentFileItem> {
    val array = JSONArray(raw)
    return buildList {
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            add(
                RecentFileItem(
                    uri = obj.getString("uri"),
                    fileName = obj.optString("fileName", "PDF Document"),
                    openedAt = obj.optString("openedAt", ""),
                    fileSizeBytes = obj.optLong("fileSizeBytes", -1L),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    isFavorite = obj.optBoolean("isFavorite", false),
                    lastPage = obj.optInt("lastPage", 0)
                )
            )
        }
    }.sortedByDescending { it.timestamp }.take(MAX_RECENT_FILES)
}
