package com.infdesk.hanziwriting.ui

import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure as AndroidPathMeasure
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.infdesk.hanziwriting.utils.HanziTransform
import kotlinx.coroutines.delay

private data class PreparedStroke(
    val svgPath: Path,
    val medianMeasure: AndroidPathMeasure,
    val length: Float
)

@Composable
fun AnimationCanvas(
    medians: List<List<List<Double>>>,
    strokes: List<String>,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    loop: Boolean = false,
    onComplete: () -> Unit = {}
) {
    val density = LocalDensity.current
    val canvasSizePx = with(density) { 100.dp.toPx() }
    val scale = canvasSizePx / HanziTransform.GRID_WIDTH

    val partialMedian = remember { AndroidPath() }
    val thickClip = remember { AndroidPath() }
    
    // FIX 1A: Increased thickness to 300f to ensure the mask fully covers 
    // the outer edges of the SVG shape during sharp turns (like in 幺).
    val maskPaint = remember(scale) {
        Paint().apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = scale * 300f 
        }
    }

    val prepared = remember(strokes, medians, canvasSizePx) {
        val safeMedians = medians.take(strokes.size)
        strokes.mapIndexedNotNull { index, svg ->
            try {
                val androidSvgPath = PathParser.createPathFromPathData(svg)
                androidSvgPath.transform(HanziTransform.matrix(scale))
                val composeSvgPath = androidSvgPath.asComposePath()
                
                val pts = safeMedians.getOrElse(index) { emptyList() }
                val androidMedianPath = AndroidPath()
                pts.forEachIndexed { i, pt ->
                    val cp = HanziTransform.toCanvas(pt, scale)
                    if (i == 0) androidMedianPath.moveTo(cp.x, cp.y) else androidMedianPath.lineTo(cp.x, cp.y)
                }
                
                val measure = AndroidPathMeasure(androidMedianPath, false)
                PreparedStroke(composeSvgPath, measure, measure.length)
            } catch (e: Exception) {
                null
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
                    
                    val baseDuration = 350
                    val lengthFactor = prepared[i].length / (canvasSizePx * 0.4f) 
                    val duration = (baseDuration + lengthFactor * 250).toInt().coerceIn(350, 900)
                    
                    progress.animateTo(1f, tween(durationMillis = duration, easing = FastOutSlowInEasing))
                    delay(150) 
                }
                if (!loop) { onComplete(); break }
                delay(600) 
            }
        }
    }

    val strokeColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        // 1. Draw completed strokes (fully filled SVG shapes)
        for (i in 0 until currentStroke) {
            drawPath(prepared[i].svgPath, strokeColor, style = Fill)
        }
        
        // 2. Draw current animating stroke
        if (currentStroke < prepared.size) {
            val p = prepared[currentStroke]
            
            // FIX 1B: When the animation reaches 100%, bypass the mask and draw the full SVG shape.
            // This prevents the "cut-off tip" caused by PathMeasure missing the absolute last pixel 
            // during the 150ms pen-lift delay.
            if (progress.value >= 1.0f) {
                drawPath(p.svgPath, strokeColor, style = Fill)
            } else {
                partialMedian.reset()
                p.medianMeasure.getSegment(0f, p.length * progress.value, partialMedian, true)
                
                thickClip.reset()
                maskPaint.getFillPath(partialMedian, thickClip)
                val composeClip = thickClip.asComposePath()
                
                clipPath(composeClip) {
                    drawPath(p.svgPath, strokeColor, style = Fill)
                }
            }
        }
    }
}
