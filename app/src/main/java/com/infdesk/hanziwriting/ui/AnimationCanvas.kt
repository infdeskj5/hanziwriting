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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.PathParser
import com.infdesk.hanziwriting.utils.HanziTransform
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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

    // Precompute filled SVG paths + median start/end points (for clip direction)
    val prepared = remember(strokes, medians, sizePx) {
        if (sizePx <= 0f || strokes.isEmpty()) emptyList() else {
            val scale = sizePx / HanziTransform.GRID_WIDTH
            strokes.mapIndexed { i, svg ->
                val androidPath = try {
                    PathParser.createPathFromPathData(svg).apply {
                        transform(HanziTransform.matrix(scale))
                    }
                } catch (e: Exception) { null }
                val composePath = androidPath?.asComposePath()

                val median = medians.getOrNull(i)
                val startPoint = median?.firstOrNull()?.let { HanziTransform.toCanvas(it, scale) } ?: Offset.Zero
                val endPoint = median?.lastOrNull()?.let { HanziTransform.toCanvas(it, scale) } ?: Offset(sizePx, sizePx)

                Triple(composePath, startPoint, endPoint)
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
                    progress.animateTo(1f, tween(durationMillis = 500, easing = LinearEasing))
                    delay(140)
                }
                if (!loop) { onComplete(); break }
                delay(600)
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
            // If SVG strokes are available: fill them progressively with clip rect
            if (prepared.isNotEmpty() && prepared.first().first != null) {
                for (i in 0 until currentStroke) {
                    prepared[i].first?.let { drawPath(it, strokeColor) }
                }
                if (currentStroke < prepared.size) {
                    val (path, start, end) = prepared[currentStroke]
                    if (path != null) {
                        val p = progress.value
                        val dx = end.x - start.x
                        val dy = end.y - start.y

                        // Build a clip rect that grows from stroke start toward stroke end
                        val clipRect = if (abs(dx) >= abs(dy)) {
                            // Horizontal-dominant stroke: reveal left→right or right→left
                            if (dx >= 0) {
                                val width = size.width * p
                                Rect(start.x - 20f, 0f, start.x - 20f + width + 40f, size.height)
                            } else {
                                val width = size.width * p
                                Rect(end.x - 20f + (size.width - (end.x - 20f)) * (1 - p), 0f, size.width + 20f, size.height)
                            }
                        } else {
                            // Vertical-dominant stroke: reveal top→bottom or bottom→top
                            if (dy >= 0) {
                                val height = size.height * p
                                Rect(0f, start.y - 20f, size.width, start.y - 20f + height + 40f)
                            } else {
                                val height = size.height * p
                                Rect(0f, end.y - 20f + (size.height - (end.y - 20f)) * (1 - p), size.width, size.height + 20f)
                            }
                        }

                        clipRect(
                            left = clipRect.left.coerceAtLeast(0f),
                            top = clipRect.top.coerceAtLeast(0f),
                            right = clipRect.right.coerceAtMost(size.width),
                            bottom = clipRect.bottom.coerceAtMost(size.height),
                            clipOp = ClipOp.Intersect
                        ) {
                            drawPath(path, strokeColor)
                        }
                    }
                }
            } else {
                // Fallback: median-based stroke animation
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
