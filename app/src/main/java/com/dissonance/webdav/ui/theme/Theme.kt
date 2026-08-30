package com.dissonance.webdav.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
  lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryContainer,
    onPrimaryContainer = BrandOnPrimaryContainer,
    secondary = BrandSecondary,
    onSecondary = Color.White,
    secondaryContainer = BrandSecondaryContainer,
    onSecondaryContainer = BrandOnSecondaryContainer,
    tertiary = BrandTertiary,
    onTertiary = Color.White,
    tertiaryContainer = BrandTertiaryContainer,
    onTertiaryContainer = Color(0xFF001F24),
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceLight,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHigh,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorRose,
    onError = Color.White,
    errorContainer = ErrorRoseBg,
    onErrorContainer = ErrorRoseDark
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Clean, unified Light UI
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = LightColorScheme,
    typography = Typography,
    content = content
  )
}



