package com.tihloh.pos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val RetailLight = lightColorScheme(
    primary = Color(0xFF315FEA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0D2C78),
    secondary = Color(0xFF078A68),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F5E9),
    onSecondaryContainer = Color(0xFF063F32),
    tertiary = Color(0xFFE99A05),
    onTertiary = Color(0xFF2A1A00),
    tertiaryContainer = Color(0xFFFFE9B8),
    background = Color(0xFFF5F7FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDF1F8),
    outline = Color(0xFF7B8497),
    error = Color(0xFFD92D20)
)

private val RetailDark = darkColorScheme(
    primary = Color(0xFF7EA9FF),
    onPrimary = Color(0xFF002E6B),
    primaryContainer = Color(0xFF173F86),
    onPrimaryContainer = Color(0xFFDCE8FF),
    secondary = Color(0xFF56D8AD),
    onSecondary = Color(0xFF00382B),
    secondaryContainer = Color(0xFF0C4E3E),
    onSecondaryContainer = Color(0xFFC9F5E5),
    tertiary = Color(0xFFFFC857),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5D4300),
    onTertiaryContainer = Color(0xFFFFE8AD),
    background = Color(0xFF070B14),
    surface = Color(0xFF111827),
    surfaceVariant = Color(0xFF1E293B),
    outline = Color(0xFF67748B),
    error = Color(0xFFFF6B6B)
)

private val RetailShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun PosTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) RetailDark else RetailLight,
        shapes = RetailShapes,
        content = content
    )
}
