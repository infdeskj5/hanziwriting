package com.infdesk.hanziwriting.ui

import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure as AndroidPathMeasure
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.graphics.PathParser
import com.infdesk.hanziwriting.utils.HanziTransform
import kotlinx.coroutines.delay

private data class PreparedStroke(
    val svgPath: Path,
    val medianAndroid: AndroidPath,
    val medianLength: Float
)

// Fraction of the stroke during which the brush "presses down" (width ramps 0 -> full).
private const val BRUSH_PRESS_FRACTION = 0.15f

@Composable
fun AnimationCanvas(
    medians: List<List<List<Double>>>,
    strokes: List<String>,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    loop: Boolean = false,
    onComplete: () -> Unit = {}
) {
    var sizePx by remember { mutableFloatStateOf(0f) }

    val prepared = remember(strokes, medians, sizePx) {
        if (sizePx <= 0f || strokes.isEmpty()) emptyList() else {
            val scale = sizePx / HanziTransform.GRID_WIDTH
            strokes.mapIndexedNotNull { i, svg ->
                try {
                    val androidSvg = PathParser.createPathFromPathData(svg).apply {
                        transform(HanziTransform.matrix(scale))
                    }
                    val svgPath = androidSvg.asComposePath()

                    val medianAndroid = AndroidPath()
                    val pts = medians.getOrNull(i) ?: emptyList()
                    pts.forEachIndexed { idx, pt ->
                        val cp = HanziTransform.toCanvas(pt, scale)
                        if (idx == 0) medianAndroid.moveTo(cp.x, cp.y) else medianAndroid.lineTo(cp.x, cp.y)
                    }
                    val pm = AndroidPathMeasure()
                    pm.setPath(medianAndroid, false)

                    PreparedStroke(svgPath, medianAndroid, pm.length)
                } catch (e: Exception) { null }
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
                    
                    // ORIGINAL SPEED restored: exactly 400ms per stroke, constant speed (LinearEasing)
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 650, easing = LinearEasing)
                    )
                    delay(150L) // Original pause between strokes
                }
                if (!loop) { onComplete(); break }
                delay(1000L) // Pause before looping
            }
        }
    }

    val strokeColor = MaterialTheme.colorScheme.primary
    val ghostColor = strokeColor.copy(alpha = 0.12f)
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    val brushPaint = remember {
        AndroidPaint().apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }
    }
    val segPath = remember { AndroidPath() }
    val tubePath = remember { AndroidPath() }
    val pm = remember { AndroidPathMeasure() }

    Box(
        modifier = modifier
            .onSizeChanged { sizePx = it.width.toFloat() }
            .background(surfaceColor)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            for (i in prepared.indices) {
                val st = prepared[i]
                when {
                    i < currentStroke -> drawPath(st.svgPath, strokeColor)

                    i == currentStroke -> {
                        drawPath(st.svgPath, ghostColor)

                        val p = progress.value
                        pm.setPath(st.medianAndroid, false)
                        segPath.reset()
                        pm.getSegment(0f, st.medianLength * p, segPath, true)
                        tubePath.reset()

                        // Brush width ramps from 0 during the first 15% of the stroke
                        val widthFactor = (p / BRUSH_PRESS_FRACTION).coerceIn(0f, 1f)
                        if (widthFactor > 0f && segPath.isEmpty == false) {
                            brushPaint.strokeWidth = size.width * 0.35f * widthFactor
                            brushPaint.getFillPath(segPath, tubePath)

                            val revealed = Path.combine(
                                PathOperation.Intersect,
                                st.svgPath,
                                tubePath.asComposePath()
                            )
                            drawPath(revealed, strokeColor)
                        }
                    }
                }
            }
        }
    }
}
