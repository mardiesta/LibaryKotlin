package com.example.libary.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Binding,
    onPrimary = Paper,
    primaryContainer = Color(0xFFD7E8DE),
    onPrimaryContainer = BindingDark,
    secondary = Shelf,
    onSecondary = Color(0xFFFFF8F1),
    secondaryContainer = Color(0xFFF3DCC6),
    onSecondaryContainer = Color(0xFF3D2108),
    background = Paper,
    onBackground = Ink,
    surface = Card,
    surfaceContainer = Color(0xFFEFE9DE),
    surfaceContainerLow = Card,
    surfaceContainerHigh = Color(0xFFE7DFD2),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7DFD2),
    onSurfaceVariant = Color(0xFF534C43),
    outline = Color(0xFFD5CBBC),
    error = Color(0xFF9C2F2F),
    onError = Color(0xFFFFF7F6),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB7D4C4),
    onPrimary = BindingDark,
    primaryContainer = Binding,
    onPrimaryContainer = Paper,
    secondary = Color(0xFFE7C09A),
    onSecondary = Color(0xFF3D2108),
    background = Color(0xFF141311),
    onBackground = Paper,
    surface = Color(0xFF1E1C19),
    surfaceContainer = Color(0xFF292722),
    surfaceContainerLow = Color(0xFF211F1B),
    surfaceContainerHigh = Color(0xFF34312B),
    onSurface = Paper,
    surfaceVariant = Color(0xFF2A2723),
    onSurfaceVariant = Color(0xFFD5CBBC),
    error = Color(0xFFFFB4AB),
)

@Composable
fun LibaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
