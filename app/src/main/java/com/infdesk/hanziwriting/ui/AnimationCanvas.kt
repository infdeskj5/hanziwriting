package com.infdesk.hanziwriting.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.infdesk.hanziwriting.utils.HanziTransform
import kotlinx.coroutines.delay
import kotlin.math.sin

private data class PreparedStroke(val measure: PathMeasure, val length: Float)

@Composable
fun AnimationCanvas(
    medians: List<List<List<Double>>> = emptyList(),
    strokes: List<List<List<Double>>> = medians, // Supports whichever parameter name the caller uses!
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    loop: Boolean = false,
    onComplete: () -> Unit = {}
) {
    val density = LocalDensity.current
    val canvasSizePx = with(density) { 100.dp.toPx() }

    // Use whichever list was provided
    val targetData = if (strokes.isNotEmpty()) strokes else medians

    val prepared = remember(targetData, canvasSizePx) {
        val scale = canvasSizePx / HanziTransform.GRID_WIDTH
        targetData.map { pts ->
            val path = Path()
            pts.forEachIndexed { i, pt ->
                val cp = HanziTransform.toCanvas(pt, scale)
                if (i == 0) path.moveTo(cp.x, cp.y) else path.lineTo(cp.x, cp.y)
            }
            val m = PathMeasure()
            m.setPath(path, false)
            PreparedStroke(m, m.length)
        }
    }

    val progress = remember { Animatable(0f) }
    var currentStroke by remember { mutableIntStateOf(0) }

    // Human-like acceleration and deceleration curve
    val humanEasing = remember { CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f) }

    LaunchedEffect(prepared, autoPlay, loop) {
        if (autoPlay && prepared.isNotEmpty()) {
            while (true) {
                for (i in prepared.indices) {
                    currentStroke = i
                    progress.snapTo(0f)
                    
                    val strokeLength = prepared[i].length
                    val duration = (350 + (strokeLength * 0.3f)).toInt().coerceIn(300, 700)

                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = duration, easing = humanEasing)
                    )
                    delay(80)
                }
                if (!loop) { onComplete(); break }
                delay(400)
            }
        }
    }

    val strokeColor = MaterialTheme.colorScheme.primary
    val baseStrokeWidth = canvasSizePx * 0.08f

    Canvas(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        for (i in 0 until currentStroke) {
            if (i < prepared.size) {
                val full = Path()
                prepared[i].measure.getSegment(0f, prepared[i].length, full, true)
                drawPath(full, strokeColor, style = Stroke(width = baseStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        if (currentStroke < prepared.size) {
            val p = prepared[currentStroke]
            val currentLen = p.length * progress.value
            
            if (currentLen > 0f) {
                val seg = Path()
                p.measure.getSegment(0f, currentLen, seg, true)

                // Simulate subtle human width tapering/pressure
                val pressureFactor = (sin(progress.value * Math.PI)).toFloat()
                val dynamicWidth = baseStrokeWidth * (0.8f + (0.2f * pressureFactor))

                drawPath(
                    seg, 
                    strokeColor, 
                    style = Stroke(
                        width = dynamicWidth, 
                        cap = StrokeCap.Round, 
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}
