package com.ankit.pdfeditor.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ankit.pdfeditor.model.AppScreen
import com.ankit.pdfeditor.ui.theme.StudioSlate400

private val PdfPrimary = Color(0xFF6366F1)

@Composable
internal fun BottomNavBar(
    currentScreen: AppScreen,
    onTabSelected: (AppScreen) -> Unit,
    onScanClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavTabItem(Icons.Default.GridView, "Home", currentScreen == AppScreen.HOME) { onTabSelected(AppScreen.HOME) }
            NavTabItem(Icons.Default.Description, "Files", currentScreen == AppScreen.FILES) { onTabSelected(AppScreen.FILES) }
            Surface(
                onClick = onScanClick,
                shape = CircleShape,
                color = PdfPrimary,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .size(48.dp)
                    .offset(y = (-14).dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Default.Add, contentDescription = "Scan document", tint = Color.White)
                }
            }
            NavTabItem(Icons.Default.Build, "Tools", currentScreen == AppScreen.TOOLS) { onTabSelected(AppScreen.TOOLS) }
            NavTabItem(Icons.Default.Person, "Account", currentScreen == AppScreen.SETTINGS) { onTabSelected(AppScreen.SETTINGS) }
        }
    }
}

@Composable
private fun NavTabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) PdfPrimary else StudioSlate400
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}
