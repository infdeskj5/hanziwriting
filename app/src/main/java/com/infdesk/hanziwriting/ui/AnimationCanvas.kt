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
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.infdesk.hanziwriting.utils.HanziTransform
import kotlinx.coroutines.delay
import kotlin.math.sin

private data class PreparedStroke(val measure: PathMeasure, val length: Float)

@Composable
fun AnimationCanvas(
    strokes: List<String>,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    loop: Boolean = false,
    onComplete: () -> Unit = {}
) {
    val density = LocalDensity.current
    val canvasSizePx = with(density) { 100.dp.toPx() }

    // Precompute each stroke's Path and PathMeasure from the SVG strings
    val prepared = remember(strokes, canvasSizePx) {
        val scale = canvasSizePx / HanziTransform.GRID_WIDTH
        strokes.mapNotNull { svg ->
            try {
                val androidPath = PathParser.createPathFromPathData(svg)
                androidPath.transform(HanziTransform.matrix(scale))
                val composePath = androidPath.asComposePath()
                val m = PathMeasure()
                m.setPath(composePath, false)
                PreparedStroke(m, m.length)
            } catch (e: Exception) {
                null
            }
        }
    }

    val progress = remember { Animatable(0f) }
    var currentStroke by remember { mutableIntStateOf(0) }

    // Human-like acceleration and deceleration curve (EaseInOutCubic equivalent)
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
        // Draw completed strokes
        for (i in 0 until currentStroke) {
            if (i < prepared.size) {
                val full = Path()
                prepared[i].measure.getSegment(0f, prepared[i].length, full, true)
                drawPath(full, strokeColor, style = Stroke(width = baseStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        // Draw active animated stroke with variable pressure/width tapering
        if (currentStroke < prepared.size) {
            val p = prepared[currentStroke]
            val currentLen = p.length * progress.value
            
            if (currentLen > 0f) {
                val seg = Path()
                p.measure.getSegment(0f, currentLen, seg, true)

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
