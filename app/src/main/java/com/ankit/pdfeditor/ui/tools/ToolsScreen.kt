package com.ankit.pdfeditor.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ankit.pdfeditor.ui.theme.StudioSlate400
import com.ankit.pdfeditor.ui.theme.StudioSlate50
import com.ankit.pdfeditor.ui.theme.StudioSlate500

@Composable
internal fun ToolsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioSlate50)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Text("Tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Build, contentDescription = null, tint = StudioSlate400, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    "More PDF tools are coming soon.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StudioSlate500,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
