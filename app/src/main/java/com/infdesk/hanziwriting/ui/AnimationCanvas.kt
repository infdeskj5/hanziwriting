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
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import com.infdesk.hanziwriting.utils.HanziTransform
import kotlinx.coroutines.delay

private data class PreparedStroke(val measure: PathMeasure, val length: Float)

@Composable
fun AnimationCanvas(
    medians: List<List<List<Double>>>,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    loop: Boolean = false,
    onComplete: () -> Unit = {}
) {
    // FIX #1: track the REAL pixel size so the drawing always fills the given box
    var sizePx by remember { mutableFloatStateOf(0f) }

    val prepared = remember(medians, sizePx) {
        if (sizePx <= 0f) emptyList() else {
            val scale = sizePx / HanziTransform.GRID_WIDTH
            val offsetY = (sizePx - HanziTransform.GRID_HEIGHT * scale) / 2f
            medians.map { pts ->
                val path = Path()
                pts.forEachIndexed { i, pt ->
                    val cp = HanziTransform.toCanvas(pt, scale)
                    if (i == 0) path.moveTo(cp.x, cp.y + offsetY) else path.lineTo(cp.x, cp.y + offsetY)
                }
                val m = PathMeasure()
                m.setPath(path, false)
                PreparedStroke(m, m.length)
            }
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
                    progress.animateTo(1f, tween(durationMillis = 450, easing = LinearEasing))
                    delay(120)
                }
                if (!loop) { onComplete(); break }
                delay(500)
            }
        }
    }

    val strokeColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = modifier
            .onSizeChanged { sizePx = it.width.toFloat() }
            .background(surfaceColor)
    ) {
        val strokeWidth = size.width * 0.09f
        for (i in 0 until currentStroke) {
            if (i < prepared.size) {
                val full = Path()
                prepared[i].measure.getSegment(0f, prepared[i].length, full, true)
                drawPath(full, strokeColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        if (currentStroke < prepared.size) {
            val p = prepared[currentStroke]
            val seg = Path()
            p.measure.getSegment(0f, p.length * progress.value, seg, true)
            drawPath(seg, strokeColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
