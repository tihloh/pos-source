package com.tihloh.pos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RetailLight = lightColorScheme(
    primary = Color(0xFF155EEF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF072A69),
    secondary = Color(0xFF0E9F6E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F7E9),
    tertiary = Color(0xFFF59E0B),
    surface = Color(0xFFFAFBFF),
    surfaceVariant = Color(0xFFF0F3FA),
    background = Color(0xFFF6F8FC),
    error = Color(0xFFD92D20)
)

private val RetailDark = darkColorScheme(
    primary = Color(0xFF8BB4FF),
    secondary = Color(0xFF5ED6A6),
    tertiary = Color(0xFFFFC65A),
    surface = Color(0xFF121722),
    surfaceVariant = Color(0xFF202737),
    background = Color(0xFF0D111A)
)

@Composable
fun PosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) RetailDark else RetailLight,
        content = content
    )
}
