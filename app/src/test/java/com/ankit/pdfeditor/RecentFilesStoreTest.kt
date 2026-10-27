package com.ankit.pdfeditor

import com.ankit.pdfeditor.model.RecentFileItem
import com.ankit.pdfeditor.util.recentFilesFromJson
import com.ankit.pdfeditor.util.recentFilesJson
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentFilesStoreTest {
    @Test fun roundTripsJson() {
        val source = listOf(RecentFileItem("content://one", "one.pdf", "today", 42, 10, true))
        assertEquals(source, recentFilesFromJson(recentFilesJson(source)))
    }
}
