package com.infdesk.hanziwriting.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.utils.HanziTransform
import kotlinx.coroutines.delay

@Composable
fun AnimationCanvas(
    medians: List<List<List<Double>>>,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    onComplete: () -> Unit = {}
) {
    val density = LocalDensity.current
    val canvasSizePx = with(density) { 100.dp.toPx() } // Default size, scales to modifier
    val scale = canvasSizePx / HanziTransform.GRID_WIDTH

    var currentStroke by remember { mutableIntStateOf(0) }
    var strokeProgress by remember { mutableFloatStateOf(0f) }

    val animatedProgress by animateFloatAsState(
        targetValue = strokeProgress,
        animationSpec = tween(durationMillis = 400, easing = androidx.compose.animation.core.LinearEasing),
        finishedListener = {
            if (currentStroke < medians.lastIndex) {
                currentStroke++
                strokeProgress = 0f
            } else {
                onComplete()
            }
        }
    )

    LaunchedEffect(autoPlay) {
        if (autoPlay && medians.isNotEmpty()) {
            strokeProgress = 0f
            delay(100)
            strokeProgress = 1f
        }
    }

    LaunchedEffect(currentStroke) {
        if (currentStroke < medians.size && currentStroke > 0) {
            strokeProgress = 0f
            delay(150) // Pause between strokes
            strokeProgress = 1f
        }
    }

    val strokeColor = MaterialTheme.colorScheme.primary
    val strokeWidth = canvasSizePx * 0.08f

    Canvas(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        val actualScale = size.width / HanziTransform.GRID_WIDTH
        
        // Draw completed strokes
        for (i in 0 until currentStroke) {
            drawMedianPath(medians[i], actualScale, 1f, strokeColor, strokeWidth)
        }
        
        // Draw current stroke partially
        if (currentStroke < medians.size) {
            drawMedianPath(medians[currentStroke], actualScale, animatedProgress, strokeColor, strokeWidth)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMedianPath(
    points: List<List<Double>>,
    scale: Float,
    progress: Float,
    color: androidx.compose.ui.graphics.Color,
    width: Float
) {
    if (points.isEmpty()) return
    val path = Path()
    val totalPoints = points.size
    val pointsToDraw = (totalPoints * progress).toInt().coerceAtLeast(1)
    
    points.take(pointsToDraw).forEachIndexed { i, pt ->
        val cp = HanziTransform.toCanvas(pt, scale)
        if (i == 0) path.moveTo(cp.x, cp.y) else path.lineTo(cp.x, cp.y)
    }
    
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
