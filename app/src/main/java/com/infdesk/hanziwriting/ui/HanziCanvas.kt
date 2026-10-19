package com.infdesk.hanziwriting.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.infdesk.hanziwriting.utils.HanziTransform

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
        val scale = canvasSizePx / HanziTransform.GRID_WIDTH

        // Read all theme colors OUTSIDE the draw lambda
        val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
        val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
        val userColor = MaterialTheme.colorScheme.primary

        val guideAlpha = when (viewModel.guideLevel) {
            0 -> 0.25f
            1 -> 0.12f
            else -> 0f
        }
        val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = guideAlpha)

        // Parse & transform the SVG stroke shapes once (cached across recompositions)
        val svgPaths: List<Path> = remember(data, canvasSizePx) {
            data.strokes.mapNotNull { svg ->
                try {
                    val androidPath = PathParser.createPathFromPathData(svg)
                    androidPath.transform(HanziTransform.matrix(scale))
                    androidPath.asComposePath()
                } catch (e: Exception) {
                    null
                }
            }
        }

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
                                    val last = viewModel.currentStrokePoints.last()
                                    viewModel.addPointToCurrentStroke(
                                        Offset(last.x + dragAmount.x, last.y + dragAmount.y)
                                    )
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
            // 1. Practice grid (米字格) — subtle
            drawLine(gridColor, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 2f)
            drawLine(gridColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2f)
            drawLine(gridColor, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 1.5f)
            drawLine(gridColor, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.5f)

            // 2. Guide (depends on guide level)
            if (guideAlpha > 0f) {
                if (svgPaths.isNotEmpty()) {
                    // Beautiful calligraphic stroke shapes
                    svgPaths.forEach { drawPath(it, guideColor, style = Fill) }
                } else {
                    // Fallback: thick, rounded median lines
                    val guideWidth = size.width * 0.09f
                    data.medians.forEach { strokeMedians ->
                        val path = Path()
                        strokeMedians.forEachIndexed { i, pt ->
                            val cp = HanziTransform.toCanvas(pt, scale)
                            if (i == 0) path.moveTo(cp.x, cp.y) else path.lineTo(cp.x, cp.y)
                        }
                        drawPath(path, guideColor, style = Stroke(width = guideWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }

            // 3. Completed correct strokes (rounded, smooth)
            val userStrokeWidth = size.width * 0.055f
            viewModel.completedStrokes.forEach { points ->
                val path = Path()
                points.forEachIndexed { i, off ->
                    if (i == 0) path.moveTo(off.x, off.y) else path.lineTo(off.x, off.y)
                }
                drawPath(path, userColor.copy(alpha = 0.75f), style = Stroke(width = userStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            // 4. Current active stroke
            if (viewModel.currentStrokePoints.isNotEmpty()) {
                val path = Path()
                viewModel.currentStrokePoints.forEachIndexed { i, off ->
                    if (i == 0) path.moveTo(off.x, off.y) else path.lineTo(off.x, off.y)
                }
                drawPath(path, userColor, style = Stroke(width = userStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
