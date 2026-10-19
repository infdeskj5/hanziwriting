package com.infdesk.hanziwriting.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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

    if (data != null) {
        val density = LocalDensity.current
        val canvasSizePx = with(density) { 300.dp.toPx() }
        val scale = canvasSizePx / HanziTransform.GRID_WIDTH

        val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
        val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
        val userColor = MaterialTheme.colorScheme.primary
        val hintColor = MaterialTheme.colorScheme.tertiary

        val guideAlpha = when (viewModel.guideLevel) {
            0 -> 0.25f
            1 -> 0.12f
            else -> 0f
        }
        val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = guideAlpha)

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

        // Animation state for the current animating stroke
        val animatedProgress by animateFloatAsState(
            targetValue = viewModel.animationProgress,
            animationSpec = tween(durationMillis = 600)
        )

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
            // 1. Practice grid
            drawLine(gridColor, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 2f)
            drawLine(gridColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2f)
            drawLine(gridColor, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 1.5f)
            drawLine(gridColor, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 1.5f)

            // 2. Guide strokes (SVG paths)
            if (guideAlpha > 0f && svgPaths.size == data.strokes.size) {
                svgPaths.forEach { path ->
                    drawPath(path, guideColor, style = Fill)
                }
            }

            // 3. Hint (if mistakes >= 3 on current stroke)
            if (viewModel.mistakesOnCurrentStroke >= 3 && viewModel.currentStrokeIndex < svgPaths.size) {
                drawPath(svgPaths[viewModel.currentStrokeIndex], hintColor.copy(alpha = 0.6f), style = Fill)
            }

            // 4. Animated stroke
            if (viewModel.animatedStrokeIndex >= 0 && viewModel.animatedStrokeIndex < svgPaths.size) {
                val pathMeasure = androidx.compose.ui.graphics.PathMeasure()
                pathMeasure.setPath(svgPaths[viewModel.animatedStrokeIndex], false)
                val partialPath = Path()
                pathMeasure.getSegment(0f, pathMeasure.length * animatedProgress, partialPath, true)
                drawPath(partialPath, userColor, style = Fill)
            }

            // 5. Completed user strokes
            val userStrokeWidth = size.width * 0.055f
            viewModel.completedStrokes.forEach { points ->
                val path = Path()
                points.forEachIndexed { i, off ->
                    if (i == 0) path.moveTo(off.x, off.y) else path.lineTo(off.x, off.y)
                }
                drawPath(path, userColor.copy(alpha = 0.75f), style = Stroke(width = userStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            // 6. Current active stroke
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
