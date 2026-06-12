package com.heartbeets.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HeartBeetsColors = darkColorScheme(
    primary = Color(0xFFE53935),           // red
    onPrimary = Color.White,
    secondary = Color(0xFFFF8A80),         // light red
    surface = Color(0x99000000),           // semi-transparent black (lets bg show)
    onSurface = Color.White,
    background = Color.Transparent,
    onBackground = Color.White,
)

@Composable
fun HeartBeetsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HeartBeetsColors, content = content)
}
