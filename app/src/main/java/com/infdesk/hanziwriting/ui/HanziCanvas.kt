package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun HanziCanvas(
    viewModel: PracticeViewModel,
    modifier: Modifier = Modifier
) {
    val data = viewModel.practiceData

    // SAFE: wrap in if-block, never use an early return in a @Composable
    if (data != null) {
        val density = LocalDensity.current
        val canvasSizePx = with(density) { 300.dp.toPx() }

        val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
        val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
        val userColor = MaterialTheme.colorScheme.primary

        Canvas(
            modifier = modifier
                .size(300.dp)
                .background(surfaceVariantColor)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (!viewModel.isCompleted) {
                                viewModel.currentStrokePoints.clear()
                                viewModel.addPointToCurrentStroke(offset)
                            }
                        },
                        onDrag = { change, dragAmount ->
                            if (!viewModel.isCompleted) {
                                change.consume()
                                if (viewModel.currentStrokePoints.isNotEmpty()) {
                                    val newPoint = Offset(
                                        viewModel.currentStrokePoints.last().x + dragAmount.x,
                                        viewModel.currentStrokePoints.last().y + dragAmount.y
                                    )
                                    viewModel.addPointToCurrentStroke(newPoint)
                                }
                            }
                        },
                        onDragEnd = {
                            if (!viewModel.isCompleted) {
                                viewModel.finishStroke(canvasSizePx)
                            }
                        },
                        onDragCancel = {
                            viewModel.currentStrokePoints.clear()
                        }
                    )
                }
        ) {
            val scale = size.width / 1024f
            val strokeWidth = 16f * scale

            // 1. Background guide (all strokes faintly)
            data.medians.forEach { strokeMedians ->
                val path = Path()
                strokeMedians.forEachIndexed { index, point ->
                    val x = point[0].toFloat() * scale
                    val y = point[1].toFloat() * scale
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, guideColor, style = Stroke(width = strokeWidth * 1.5f, cap = StrokeCap.Round))
            }

            // 2. Completed correct strokes
            viewModel.completedStrokes.forEach { points ->
                val path = Path()
                points.forEachIndexed { index, offset ->
                    if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
                }
                drawPath(path, userColor.copy(alpha = 0.7f), style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            }

            // 3. Current active stroke
            if (viewModel.currentStrokePoints.isNotEmpty()) {
                val path = Path()
                viewModel.currentStrokePoints.forEachIndexed { index, offset ->
                    if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
                }
                drawPath(path, userColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
            }
        }
    }
}
