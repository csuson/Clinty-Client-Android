package com.clinty.client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    secondary = Color(0xFF5856D6),
    background = Color(0xFFF2F2F7),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    error = Color(0xFFFF3B30),
)

@Composable
fun ClintyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content,
    )
}
