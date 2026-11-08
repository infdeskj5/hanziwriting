package com.infdesk.hanziwriting.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.utils.HanziTransform
import kotlinx.coroutines.delay

private data class PreparedStroke(val measure: PathMeasure, val length: Float)

/**
 * Converts a sequence of points into a smooth path using Quadratic Bézier curves.
 * This eliminates the jagged "robot" lines and mimics the natural flow of a human hand.
 */
private fun createSmoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) return path
    
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }

    // Use quadratic Bézier curves for a smooth, human-like flow
    for (i in 1 until points.size - 1) {
        val midX = (points[i].x + points[i+1].x) / 2f
        val midY = (points[i].y + points[i+1].y) / 2f
        // The current point acts as the control point, pulling the curve smoothly
        path.quadraticTo(points[i].x, points[i].y, midX, midY)
    }
    // Connect to the very last point
    path.lineTo(points.last().x, points.last().y)
    return path
}

@Composable
fun AnimationCanvas(
    medians: List<List<List<Double>>>,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    loop: Boolean = false,
    onComplete: () -> Unit = {}
) {
    val density = LocalDensity.current
    val canvasSizePx = with(density) { 100.dp.toPx() }
    
    // Precompute each stroke's smoothed path + measure ONCE
    val prepared = remember(medians, canvasSizePx) {
        val scale = canvasSizePx / HanziTransform.GRID_WIDTH
        medians.map { pts ->
            val canvasPts = pts.map { HanziTransform.toCanvas(it, scale) }
            val path = createSmoothPath(canvasPts)
            val m = PathMeasure()
            m.setPath(path, false)
            PreparedStroke(m, m.length)
        }
    }

    val progress = remember { Animatable(0f) }
    var currentStroke by remember { mutableIntStateOf(0) }

    LaunchedEffect(prepared, autoPlay, loop) {
        if (autoPlay && prepared.isNotEmpty()) {
            while (true) {
                for (i in prepared.indices) {
                    currentStroke = i
                    progress.snapTo(0f)
                    
                    // Dynamic duration: longer strokes take more time, mimicking human writing speed
                    val baseDuration = 350
                    val lengthFactor = prepared[i].length / (canvasSizePx * 0.4f) 
                    val duration = (baseDuration + lengthFactor * 250).toInt().coerceIn(350, 900)
                    
                    // FastOutSlowInEasing mimics the acceleration and deceleration of a brush/pen
                    progress.animateTo(1f, tween(durationMillis = duration, easing = FastOutSlowInEasing))
                    
                    // "Pen lift" delay before the next stroke begins
                    delay(150) 
                }
                if (!loop) { onComplete(); break }
                delay(600) // Pause before looping
            }
        }
    }

    val strokeColor = MaterialTheme.colorScheme.primary
    // Slightly thicker stroke to look more like a brush/marker rather than a digital wire
    val strokeWidth = canvasSizePx * 0.09f 

    Canvas(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        // Draw completed strokes
        for (i in 0 until currentStroke) {
            if (i < prepared.size) {
                val full = Path()
                prepared[i].measure.getSegment(0f, prepared[i].length, full, true)
                drawPath(full, strokeColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        // Draw current animating stroke
        if (currentStroke < prepared.size) {
            val p = prepared[currentStroke]
            val seg = Path()
            p.measure.getSegment(0f, p.length * progress.value, seg, true)
            drawPath(seg, strokeColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
