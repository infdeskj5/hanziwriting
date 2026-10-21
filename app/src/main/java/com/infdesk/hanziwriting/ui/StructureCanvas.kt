package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun StructureCanvas(
    leafCount: Int,
    activeIndex: Int,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier.background(surfaceColor)) {
        val padding = size.width * 0.1f
        val spacing = size.width * 0.05f
        val boxCount = leafCount.coerceAtLeast(1)
        val boxWidth = (size.width - padding * 2 - spacing * (boxCount - 1)) / boxCount
        val boxHeight = size.height - padding * 2

        for (i in 0 until boxCount) {
            val x = padding + i * (boxWidth + spacing)
            val y = padding
            
            val color = when {
                i < activeIndex -> primaryColor.copy(alpha = 0.3f)
                i == activeIndex -> primaryColor
                else -> outlineColor
            }
            
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(12f),
                style = Stroke(width = 4f)
            )
        }
    }
}
