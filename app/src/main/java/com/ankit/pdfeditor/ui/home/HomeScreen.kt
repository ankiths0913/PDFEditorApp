package com.ankit.pdfeditor.ui.home

import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankit.pdfeditor.R
import com.ankit.pdfeditor.model.*
import com.ankit.pdfeditor.ui.theme.*
import com.ankit.pdfeditor.util.*

private val PdfPrimary = Color(0xFF6366F1)

@Composable
private fun DashboardTopBar(
    subtitle: String,
    onSettingsTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(listOf(StudioIndigo600, PdfPrimary))),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.pdf_editor_logo_foreground),
                    contentDescription = "PDF Studio",
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("PDF Studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(StudioEmerald500))
                    Spacer(Modifier.width(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Surface(onClick = onSettingsTap, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun HeroBanner(onOpenPdf: () -> Unit, onScanDoc: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(StudioIndigo600, StudioBlue500)))
    ) {
        Box(modifier = Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = (-30).dp).size(140.dp)
            .clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).blur(30.dp))
        Box(modifier = Modifier.align(Alignment.BottomEnd).offset(x = 20.dp, y = 20.dp).size(110.dp)
            .clip(CircleShape).background(PdfPrimary.copy(alpha = 0.25f)).blur(24.dp))
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.20f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("PDF Reader & Editor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("Read, annotate, highlight & organize all your PDFs easily.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = Color.White.copy(alpha = 0.9f))
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenPdf, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White)) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = StudioIndigo600, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open PDF", color = StudioIndigo600, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Button(onClick = onScanDoc, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.18f))) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan Doc", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ImportGridSection(
    onStorageClick: () -> Unit,
    onPhotoClick: () -> Unit,
    onFilesClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Import From",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ImportGridItem(Icons.Default.Storage, "Storage", StudioIndigo50, StudioIndigo600, Modifier.weight(1f), onStorageClick)
            ImportGridItem(Icons.Default.Image, "Photos", StudioBlue50, StudioBlue600, Modifier.weight(1f), onPhotoClick)
            ImportGridItem(Icons.Default.Cloud, "Files", StudioSky50, StudioSky600, Modifier.weight(1f), onFilesClick)
            ImportGridItem(Icons.Default.PhotoCamera, "Camera", StudioViolet50, StudioViolet600, Modifier.weight(1f), onCameraClick)
        }
    }
}

@Composable
private fun ImportGridItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconBackground: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(iconBackground), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
internal fun DashboardSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    favoritesOnly: Boolean,
    onFavoritesOnlyChange: (Boolean) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search recent PDFs...") }, singleLine = true, shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = { IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.Tune, contentDescription = "Filter", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) } },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = PdfPrimary,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(if (favoritesOnly) "Show all files" else "Show favorites only") },
                onClick = { onFavoritesOnlyChange(!favoritesOnly); menuExpanded = false },
                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = if (favoritesOnly) StudioAmber400 else MaterialTheme.colorScheme.onSurfaceVariant) }
            )
        }
    }
}

@Composable
internal fun RecentFilesHeader(count: Int, currentSort: SortOption, onSortChange: (SortOption) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Recent Files",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.width(6.dp))
            Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text("$count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(2.dp)) {
            SortPill("Date", currentSort == SortOption.DATE) { onSortChange(SortOption.DATE) }
            SortPill("Name", currentSort == SortOption.NAME) { onSortChange(SortOption.NAME) }
            SortPill("Size", currentSort == SortOption.SIZE) { onSortChange(SortOption.SIZE) }
        }
    }
}

@Composable
private fun SortPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent, shadowElevation = if (selected) 1.dp else 0.dp) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) StudioIndigo600 else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun RecentFileCard(
    file: RecentFileItem,
    onTap: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(StudioRed50).border(1.dp, StudioRed100, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = StudioRed500, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.fileName, fontWeight = FontWeight.SemiBold, maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text("${file.openedAt} • ${formatFileSize(file.fileSizeBytes)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onFavoriteToggle) { Icon(Icons.Default.Star, contentDescription = "Favorite", tint = if (file.isFavorite) StudioAmber400 else MaterialTheme.colorScheme.onSurfaceVariant) }
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menuExpanded = false; onShare() }, leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuExpanded = false; onRename() }, leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) })
                    DropdownMenuItem(text = { Text("Remove from list") }, onClick = { menuExpanded = false; onRemove() }, leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) })
                    DropdownMenuItem(
                        text = { Text("Delete file", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmptyFilesMessage(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
    )
}

@Composable
internal fun HomeScreen(
    recentFiles: List<RecentFileItem>,
    onOpenPdf: () -> Unit,
    onPhotoClick: () -> Unit,
    onScanDoc: () -> Unit,
    onFileTap: (RecentFileItem) -> Unit,
    onSettingsTap: () -> Unit,
    onToggleFavorite: (RecentFileItem) -> Unit,
    onRemove: (RecentFileItem) -> Unit,
    onShare: (RecentFileItem) -> Unit,
    onRename: (RecentFileItem) -> Unit,
    onDelete: (RecentFileItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var currentSort by remember { mutableStateOf(SortOption.DATE) }
    var favoritesOnly by remember { mutableStateOf(false) }
    val filteredFiles = recentFiles.filter { it.fileName.contains(searchQuery, ignoreCase = true) }
        .filter { !favoritesOnly || it.isFavorite }
        .sortedWith(when (currentSort) {
            SortOption.DATE -> compareByDescending { it.timestamp }
            SortOption.NAME -> compareBy { it.fileName.lowercase() }
            SortOption.SIZE -> compareByDescending { it.fileSizeBytes }
        })
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        DashboardTopBar(subtitle = "Local Storage", onSettingsTap = onSettingsTap)
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { HeroBanner(onOpenPdf, onScanDoc) }
            item { ImportGridSection(onOpenPdf, onPhotoClick, onOpenPdf, onScanDoc) }
            item { DashboardSearchBar(searchQuery, { searchQuery = it }, favoritesOnly, { favoritesOnly = it }) }
            item { RecentFilesHeader(filteredFiles.size, currentSort) { currentSort = it } }
            if (filteredFiles.isNotEmpty()) {
                items(filteredFiles, key = { it.uri }) { file ->
                    RecentFileCard(file, { onFileTap(file) }, { onToggleFavorite(file) }, { onShare(file) }, { onRename(file) }, { onRemove(file) }, { onDelete(file) })
                }
            } else if (recentFiles.isEmpty()) item { EmptyFilesMessage("No PDF files yet. Open one from Home to get started!") }
            else item { EmptyFilesMessage("No files match your search.") }
        }
    }
}
