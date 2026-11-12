package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val HanziColorScheme = darkColorScheme(
    primary = Color(0xFFD5C4FF),
    onPrimary = Color(0xFF352052),
    primaryContainer = Color(0xFF503879),
    onPrimaryContainer = Color(0xFFEBDDFF),
    secondary = Color(0xFFCEC1E2),
    onSecondary = Color(0xFF352B43),
    secondaryContainer = Color(0xFF493E58),
    onSecondaryContainer = Color(0xFFEBDDFA),
    background = Color(0xFF111019),
    onBackground = Color(0xFFE8E1EF),
    surface = Color(0xFF17151F),
    onSurface = Color(0xFFE8E1EF),
    surfaceVariant = Color(0xFF292633),
    onSurfaceVariant = Color(0xFFCBC3D6),
    outline = Color(0xFF958DA1),
    outlineVariant = Color(0xFF494353),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val HanziShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
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
