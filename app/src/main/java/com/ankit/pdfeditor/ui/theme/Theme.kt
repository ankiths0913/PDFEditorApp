package com.ankit.pdfeditor.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = PDFPrimary,
    onPrimary = PDFBackground,
    primaryContainer = PDFPrimary,
    onPrimaryContainer = PDFBackground,
    secondary = PDFSecondary,
    onSecondary = PDFBackground,
    secondaryContainer = PDFSecondary,
    onSecondaryContainer = PDFBackground,
    tertiary = PDFAccent,
    onTertiary = PDFBackground,
    tertiaryContainer = PDFAccent,
    onTertiaryContainer = PDFBackground,
    background = PDFBackground,
    onBackground = PDFText,
    surface = PDFSurface,
    onSurface = PDFText,
    surfaceVariant = PDFSurfaceVariant,
    onSurfaceVariant = PDFText,
    outline = PDFOutline
)

private val DarkColorScheme = darkColorScheme(
    primary = PDFPrimary,
    onPrimary = PDFDarkBackground,
    primaryContainer = PDFSecondary,
    onPrimaryContainer = PDFDarkText,
    secondary = PDFSecondary,
    onSecondary = PDFDarkBackground,
    secondaryContainer = PDFAccent,
    onSecondaryContainer = PDFDarkText,
    tertiary = PDFAccent,
    onTertiary = PDFDarkBackground,
    tertiaryContainer = PDFAccent,
    onTertiaryContainer = PDFDarkText,
    background = PDFDarkBackground,
    onBackground = PDFDarkText,
    surface = PDFDarkSurface,
    onSurface = PDFDarkText,
    surfaceVariant = Color(0xFF374151),
    onSurfaceVariant = PDFDarkText,
    outline = Color(0xFF4B5563)
)

@Composable
fun PDFEdittorAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}