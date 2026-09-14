package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CinemaColorScheme = darkColorScheme(
  primary = CinemaCyan,
  onPrimary = Color.Black,
  primaryContainer = CinemaSurfaceVariant,
  onPrimaryContainer = CinemaCyan,
  secondary = CinemaPurple,
  onSecondary = Color.White,
  secondaryContainer = CinemaSurfaceCard,
  onSecondaryContainer = CinemaTextPrimary,
  tertiary = CinemaAccent,
  background = CinemaBackground,
  onBackground = CinemaTextPrimary,
  surface = CinemaSurface,
  onSurface = CinemaTextPrimary,
  surfaceVariant = CinemaSurfaceVariant,
  onSurfaceVariant = CinemaTextSecondary,
  error = CinemaRed,
  onError = Color.White
)

@Composable
fun WatchTogetherTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = CinemaColorScheme,
    typography = Typography,
    content = content
  )
}

// Retain alias for existing screenshot test compatibility
@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit
) {
  WatchTogetherTheme(content = content)
}
