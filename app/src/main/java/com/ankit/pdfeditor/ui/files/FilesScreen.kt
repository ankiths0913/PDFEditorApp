package com.ankit.pdfeditor.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ankit.pdfeditor.model.*
import com.ankit.pdfeditor.ui.home.*

@Composable
internal fun FilesScreen(
    recentFiles: List<RecentFileItem>,
    onFileTap: (RecentFileItem) -> Unit,
    onToggleFavorite: (RecentFileItem) -> Unit,
    onRemove: (RecentFileItem) -> Unit,
    onShare: (RecentFileItem) -> Unit,
    onClearAll: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var currentSort by remember { mutableStateOf(SortOption.DATE) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    val filteredFiles = recentFiles.filter { it.fileName.contains(searchQuery, ignoreCase = true) }
        .filter { !favoritesOnly || it.isFavorite }
        .sortedWith(when (currentSort) {
            SortOption.DATE -> compareByDescending { it.timestamp }
            SortOption.NAME -> compareBy { it.fileName.lowercase() }
            SortOption.SIZE -> compareByDescending { it.fileSizeBytes }
        })

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Recents") },
            text = { Text("Are you sure you want to clear all recent files from the list?") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClearAll()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("My Files", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (recentFiles.isNotEmpty()) {
                TextButton(onClick = { showClearDialog = true }) {
                    Text("Clear Recents", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { DashboardSearchBar(searchQuery, { searchQuery = it }, favoritesOnly, { favoritesOnly = it }) }
            item { RecentFilesHeader(filteredFiles.size, currentSort) { currentSort = it } }
            if (filteredFiles.isNotEmpty()) {
                items(filteredFiles, key = { it.uri }) { file ->
                    RecentFileCard(file, { onFileTap(file) }, { onToggleFavorite(file) }, { onShare(file) }, { onRemove(file) })
                }
            } else if (recentFiles.isEmpty()) item { EmptyFilesMessage("No PDF files yet. Open one from Home to get started!") }
            else item { EmptyFilesMessage("No files match your search.") }
        }
    }
}
