package com.ankit.pdfeditor.ui.files

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ankit.pdfeditor.model.*
import com.ankit.pdfeditor.ui.home.*
import com.ankit.pdfeditor.ui.theme.StudioSlate50
import com.ankit.pdfeditor.util.*

@Composable
internal fun FilesScreen(
    recentFiles: List<RecentFileItem>,
    onFileTap: (Uri) -> Unit,
    onToggleFavorite: (RecentFileItem) -> Unit,
    onRemove: (RecentFileItem) -> Unit,
    onShare: (RecentFileItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var currentSort by remember { mutableStateOf(SortOption.DATE) }
    var favoritesOnly by remember { mutableStateOf(false) }
    val filteredFiles = recentFiles.filter { it.fileName.contains(searchQuery, ignoreCase = true) }
        .filter { !favoritesOnly || it.isFavorite }
        .sortedWith(when (currentSort) {
            SortOption.DATE -> compareByDescending { it.timestamp }
            SortOption.NAME -> compareBy { it.fileName.lowercase() }
        })
    Column(modifier = Modifier.fillMaxSize().background(StudioSlate50)) {
        Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text("My Files", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { DashboardSearchBar(searchQuery, { searchQuery = it }, favoritesOnly, { favoritesOnly = it }) }
            item { RecentFilesHeader(filteredFiles.size, currentSort) { currentSort = it } }
            if (filteredFiles.isNotEmpty()) {
                items(filteredFiles, key = { it.uri }) { file ->
                    RecentFileCard(file, { onFileTap(Uri.parse(file.uri)) }, { onToggleFavorite(file) }, { onShare(file) }, { onRemove(file) })
                }
            } else if (recentFiles.isEmpty()) item { EmptyFilesMessage("No PDF files yet. Open one from Home to get started!") }
            else item { EmptyFilesMessage("No files match your search.") }
        }
    }
}
