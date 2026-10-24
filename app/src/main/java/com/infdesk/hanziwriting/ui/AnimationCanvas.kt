package com.infdesk.hanziwriting.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.graphics.PathParser
import com.infdesk.hanziwriting.utils.HanziTransform
import kotlinx.coroutines.delay
import kotlin.math.abs

private data class PreparedSvg(val path: Path, val start: Offset, val end: Offset, val bounds: Rect)

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
                    val androidPath = PathParser.createPathFromPathData(svg).apply {
                        transform(HanziTransform.matrix(scale))
                    }
                    val composePath = androidPath.asComposePath()
                    val median = medians.getOrNull(i)
                    val start = median?.firstOrNull()?.let { HanziTransform.toCanvas(it, scale) } ?: Offset.Zero
                    val end = median?.lastOrNull()?.let { HanziTransform.toCanvas(it, scale) } ?: Offset(sizePx, sizePx)
                    PreparedSvg(composePath, start, end, composePath.getBounds())
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
                    // ORIGINAL SPEED restored
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

    Box(
        modifier = modifier
            .onSizeChanged { sizePx = it.width.toFloat() }
            .background(surfaceColor)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (prepared.isNotEmpty()) {
                for (i in 0 until currentStroke) {
                    drawPath(prepared[i].path, strokeColor)
                }
                if (currentStroke < prepared.size) {
                    val st = prepared[currentStroke]
                    val p = progress.value
                    val dx = st.end.x - st.start.x
                    val dy = st.end.y - st.start.y
                    val pad = 10f
                    val l = (st.bounds.left - pad).coerceAtLeast(0f)
                    val t = (st.bounds.top - pad).coerceAtLeast(0f)
                    val r = (st.bounds.right + pad).coerceAtMost(size.width)
                    val b = (st.bounds.bottom + pad).coerceAtMost(size.height)

                    // Clip grows over the STROKE'S OWN bounding box (correct speed)
                    val clip = if (abs(dx) >= abs(dy)) {
                        val span = r - l
                        if (dx >= 0) Rect(l, t, l + span * p, b) else Rect(r - span * p, t, r, b)
                    } else {
                        val span = b - t
                        if (dy >= 0) Rect(l, t, r, t + span * p) else Rect(l, b - span * p, r, b)
                    }
                    clipRect(clip.left, clip.top, clip.right, clip.bottom, ClipOp.Intersect) {
                        drawPath(st.path, strokeColor)
                    }
                }
            } else {
                // Fallback: median trace
                val scale = size.width / HanziTransform.GRID_WIDTH
                val strokeWidth = size.width * 0.09f
                for (i in 0 until currentStroke) {
                    if (i < medians.size) {
                        val path = Path()
                        medians[i].forEachIndexed { idx, pt ->
                            val cp = HanziTransform.toCanvas(pt, scale)
                            if (idx == 0) path.moveTo(cp.x, cp.y) else path.lineTo(cp.x, cp.y)
                        }
                        drawPath(path, strokeColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                if (currentStroke < medians.size) {
                    val path = Path()
                    val points = medians[currentStroke]
                    val count = (points.size * progress.value).toInt().coerceAtLeast(1)
                    points.take(count).forEachIndexed { idx, pt ->
                        val cp = HanziTransform.toCanvas(pt, scale)
                        if (idx == 0) path.moveTo(cp.x, cp.y) else path.lineTo(cp.x, cp.y)
                    }
                    drawPath(path, strokeColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}
