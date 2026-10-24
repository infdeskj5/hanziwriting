package com.infdesk.hanziwriting.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.graphics.PathParser
import com.infdesk.hanziwriting.utils.HanziTransform
import kotlinx.coroutines.delay

private data class PreparedStroke(
    val svgPath: Path,
    val medianPath: Path,
    val medianLength: Float
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
    var sizePx by remember { mutableFloatStateOf(0f) }

    val prepared = remember(strokes, medians, sizePx) {
        if (sizePx <= 0f || strokes.isEmpty()) emptyList() else {
            val scale = sizePx / HanziTransform.GRID_WIDTH
            strokes.mapIndexedNotNull { i, svg ->
                try {
                    val androidSvgPath = PathParser.createPathFromPathData(svg).apply {
                        transform(HanziTransform.matrix(scale))
                    }
                    val svgPath = androidSvgPath.asComposePath()

                    val medianPath = Path()
                    val medianPoints = medians.getOrNull(i) ?: emptyList()
                    medianPoints.forEachIndexed { idx, pt ->
                        val cp = HanziTransform.toCanvas(pt, scale)
                        if (idx == 0) medianPath.moveTo(cp.x, cp.y) else medianPath.lineTo(cp.x, cp.y)
                    }

                    val measure = PathMeasure()
                    measure.setPath(medianPath, false)

                    PreparedStroke(svgPath, medianPath, measure.length)
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
                    progress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
                    )
                    delay(200) // pause between strokes so each one reads clearly
                }
                if (!loop) { onComplete(); break }
                delay(900) // pause before looping
            }
        }
    }

    val strokeColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .onSizeChanged { sizePx = it.width.toFloat() }
            .background(surfaceColor)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val measure = PathMeasure()

            // Fully completed strokes
            for (i in 0 until currentStroke) {
                if (i < prepared.size) {
                    drawPath(prepared[i].svgPath, strokeColor)
                }
            }

            // Currently animating stroke
            if (currentStroke < prepared.size) {
                val st = prepared[currentStroke]
                measure.setPath(st.medianPath, false)

                val segmentPath = Path()
                measure.getSegment(0f, st.medianLength * progress.value, segmentPath, true)

                drawIntoCanvas { canvas ->
                    val bounds = Rect(0f, 0f, size.width, size.height)
                    canvas.saveLayer(bounds, Paint())

                    // Destination: the filled calligraphic shape in the correct color
                    drawPath(st.svgPath, strokeColor)

                    // Source mask: a brush-sized stroke travelling along the median.
                    // FIX: width is now ~35% of the box (a real stroke's thickness).
                    // Previously 150% made the round cap reveal the whole stroke instantly.
                    val maskPaint = Paint().apply {
                        color = Color.Black
                        style = PaintingStyle.Stroke
                        strokeWidth = size.width * 0.35f
                        strokeCap = StrokeCap.Round
                        strokeJoin = StrokeJoin.Round
                        blendMode = BlendMode.DstIn
                    }
                    canvas.drawPath(segmentPath, maskPaint)

                    canvas.restore()
                }
            }
        }
    }
}
