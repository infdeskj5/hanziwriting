package com.infdesk.hanziwriting.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    val canvasSizePx = with(density) { 100.dp.toPx() }
    
    val strokeProgress = remember { Animatable(0f) }
    var currentStroke by remember { mutableIntStateOf(0) }

    LaunchedEffect(medians, autoPlay) {
        if (autoPlay && medians.isNotEmpty()) {
            currentStroke = 0
            for (i in medians.indices) {
                currentStroke = i
                strokeProgress.snapTo(0f)
                strokeProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 400, easing = LinearEasing)
                )
                delay(150)
            }
            onComplete()
        }
    }

    val strokeColor = MaterialTheme.colorScheme.primary
    val strokeWidth = canvasSizePx * 0.08f

    Canvas(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        val actualScale = size.width / HanziTransform.GRID_WIDTH
        
        for (i in 0 until currentStroke) {
            if (i < medians.size) drawMedianPath(medians[i], actualScale, 1f, strokeColor, strokeWidth)
        }
        if (currentStroke < medians.size) {
            drawMedianPath(medians[currentStroke], actualScale, strokeProgress.value, strokeColor, strokeWidth)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMedianPath(
    points: List<List<Double>>, scale: Float, progress: Float, color: androidx.compose.ui.graphics.Color, width: Float
) {
    if (points.isEmpty()) return
    val path = Path()
    val pointsToDraw = (points.size * progress).toInt().coerceAtLeast(1)
    points.take(pointsToDraw).forEachIndexed { i, pt ->
        val cp = HanziTransform.toCanvas(pt, scale)
        if (i == 0) path.moveTo(cp.x, cp.y) else path.lineTo(cp.x, cp.y)
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
