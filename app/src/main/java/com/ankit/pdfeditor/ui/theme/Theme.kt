package com.ankit.pdfeditor.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ankit.pdfeditor.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = PdfPrimary,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val DarkColorScheme = darkColorScheme(
    primary = PdfPrimary,
    onPrimary = Color.White,
    primaryContainer = PdfPrimary,
    onPrimaryContainer = EditorDarkOnPrimaryContainer,
    secondary = EditorDarkSecondary,
    onSecondary = EditorDarkOnSecondary,
    secondaryContainer = EditorDarkSecondaryContainer,
    onSecondaryContainer = EditorDarkOnSecondaryContainer,
    tertiary = EditorDarkTertiary,
    onTertiary = EditorDarkOnTertiary,
    tertiaryContainer = EditorDarkTertiaryContainer,
    error = EditorDarkError,
    onError = EditorDarkOnError,
    errorContainer = EditorDarkErrorContainer,
    onErrorContainer = EditorDarkOnErrorContainer,
    background = EditorDarkBackground,
    onBackground = EditorDarkOnSurface,
    surface = EditorDarkBackground,
    onSurface = EditorDarkOnSurface,
    surfaceVariant = EditorDarkSurfaceContainerHighest,
    onSurfaceVariant = EditorDarkOnSurfaceVariant,
    outline = EditorDarkOutline,
    outlineVariant = EditorDarkOutlineVariant,
    surfaceDim = EditorDarkSurfaceDim,
    surfaceBright = EditorDarkSurfaceBright,
    surfaceContainerLowest = EditorDarkSurfaceContainerLowest,
    surfaceContainerLow = EditorDarkSurfaceContainerLow,
    surfaceContainer = EditorDarkSurfaceContainer,
    surfaceContainerHigh = EditorDarkSurfaceContainerHigh,
    surfaceContainerHighest = EditorDarkSurfaceContainerHighest
)

@Composable
fun PDFEditorAppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
