package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas

@Composable
fun PracticeScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Practice: 龙 (Dragon)", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        HanziCanvas()
    }
}

@Composable
fun HanziCanvas() {
    // Stores completed strokes
    val paths = remember { mutableStateListOf<Path>() }
    // The current active stroke being drawn
    val currentPath = remember { Path() }

    Canvas(
        modifier = Modifier
            .size(300.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentPath.moveTo(offset.x, offset.y)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentPath.relativeLineTo(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = {
                        // Save stroke and reset for next one
                        paths.add(Path().apply { addPath(currentPath) })
                        currentPath.reset()
                        // TODO: Trigger Stroke Evaluation here
                    },
                    onDragCancel = {
                        currentPath.reset()
                    }
                )
            }
    ) {
        // Draw grid guides (optional, for elegance)
        val strokeColor = MaterialTheme.colorScheme.onSurfaceVariant
        drawLine(strokeColor, Offset(size.width/2, 0f), Offset(size.width/2, size.height), alpha = 0.3f)
        drawLine(strokeColor, Offset(0f, size.height/2), Offset(size.width, size.height/2), alpha = 0.3f)

        // Draw completed strokes
        paths.forEach { path ->
            drawPath(path, Color.White, style = Stroke(width = 14f, cap = StrokeCap.Round))
        }
        // Draw active stroke
        drawPath(currentPath, Color.White, style = Stroke(width = 14f, cap = StrokeCap.Round))
    }
}
