package com.heartbeets.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HeartBeetsColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFE53935),     // red
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = androidx.compose.ui.graphics.Color(0xFFFF8A80),   // light red
    surface = androidx.compose.ui.graphics.Color(0xFF121212),
    onSurface = androidx.compose.ui.graphics.Color.White,
)

@Composable
fun HeartBeetsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HeartBeetsColors, content = content)
}
