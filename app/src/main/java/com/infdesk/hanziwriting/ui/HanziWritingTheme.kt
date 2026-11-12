package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// A restrained ink-and-lavender palette. Low-contrast surfaces and minimal
// elevation keep the interface calm without expensive shadows or blur effects.
private val HanziColorScheme = darkColorScheme(
    primary = Color(0xFFD9C9FF),
    onPrimary = Color(0xFF302047),
    primaryContainer = Color(0xFF493568),
    onPrimaryContainer = Color(0xFFF0E8FF),
    secondary = Color(0xFFCABDDC),
    onSecondary = Color(0xFF302A3A),
    secondaryContainer = Color(0xFF3E354C),
    onSecondaryContainer = Color(0xFFECE3F8),
    background = Color(0xFF100F17),
    onBackground = Color(0xFFEAE5F0),
    surface = Color(0xFF17151F),
    onSurface = Color(0xFFEAE5F0),
    surfaceVariant = Color(0xFF252331),
    onSurfaceVariant = Color(0xFFC9C2D3),
    outline = Color(0xFF8F879D),
    outlineVariant = Color(0xFF46404F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val HanziShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(11.dp),
    medium = RoundedCornerShape(15.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun HanziWritingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HanziColorScheme,
        typography = Typography(),
        shapes = HanziShapes,
        content = content
    )
}
