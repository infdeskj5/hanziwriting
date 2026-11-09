package com.infdesk.hanziwriting.ui

import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure as AndroidPathMeasure
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.infdesk.hanziwriting.utils.HanziTransform
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

private class PreparedStroke(
    val svgPath: ComposePath?,
    val fallbackPath: ComposePath,
    val medianPath: AndroidPath,
    val measure: AndroidPathMeasure,
    val length: Float,
    val maskWidth: Float,
    val durationMs: Int,
    val isDot: Boolean
)

private fun dist(dx: Float, dy: Float): Float {
    return sqrt(dx * dx + dy * dy)
}

private fun buildSmoothAndroidPath(points: List<Offset>): AndroidPath {
    val path = AndroidPath()

    if (points.isEmpty()) return path

    path.moveTo(points[0].x, points[0].y)

    if (points.size == 1) return path

    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }

    for (i in 1 until points.size - 1) {
        val current = points[i]
        val next = points[i + 1]

        val midX = (current.x + next.x) / 2f
        val midY = (current.y + next.y) / 2f

        path.quadTo(current.x, current.y, midX, midY)
    }

    path.lineTo(points.last().x, points.last().y)
    return path
}

private fun buildSmoothComposePath(points: List<Offset>): ComposePath {
    val path = ComposePath()

    if (points.isEmpty()) return path

    path.moveTo(points[0].x, points[0].y)

    if (points.size == 1) return path

    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }

    for (i in 1 until points.size - 1) {
        val current = points[i]
        val next = points[i + 1]

        val midX = (current.x + next.x) / 2f
        val midY = (current.y + next.y) / 2f

        path.quadraticBezierTo(current.x, current.y, midX, midY)
    }

    path.lineTo(points.last().x, points.last().y)
    return path
}

private fun estimateMaskWidth(
    svgPath: AndroidPath?,
    points: List<Offset>,
    canvasSizePx: Float
): Float {
    val bounds = RectF()
    var hasBounds = false

    if (svgPath != null) {
        try {
            svgPath.computeBounds(bounds, true)
            hasBounds = bounds.width() > 0.5f && bounds.height() > 0.5f
        } catch (_: Throwable) {
            hasBounds = false
        }
    }

    if (!hasBounds) {
        if (points.isEmpty()) {
            return canvasSizePx * 0.16f
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }

        bounds.set(minX, minY, maxX, maxY)
    }

    var dx = 1f
    var dy = 0f
    var bestSegment = 0f

    for (i in 0 until points.size - 1) {
        val sx = points[i + 1].x - points[i].x
        val sy = points[i + 1].y - points[i].y
        val segment = dist(sx, sy)

        if (segment > bestSegment) {
            bestSegment = segment
            dx = sx
            dy = sy
        }
    }

    val directionLength = dist(dx, dy)

    if (directionLength > 0.5f) {
        dx /= directionLength
        dy /= directionLength
    } else {
        dx = 1f
        dy = 0f
    }

    val perpX = -dy
    val perpY = dx

    val w = bounds.width()
    val h = bounds.height()

    val perpendicularExtent = abs(w * perpX) + abs(h * perpY)
    val diagonal = dist(w, h)

    val suggested = max(
        perpendicularExtent * 1.75f + canvasSizePx * 0.035f,
        diagonal * 0.13f + canvasSizePx * 0.055f
    )

    return suggested.coerceIn(
        canvasSizePx * 0.115f,
        canvasSizePx * 0.26f
    )
}

@Composable
fun AnimationCanvas(
    medians: List<List<List<Double>>>,
    strokes: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    loop: Boolean = false,
    showGuide: Boolean = true,
    useSvgFill: Boolean = true,
    onComplete: () -> Unit = {}
) {
    val density = LocalDensity.current
    val canvasSizePx = with(density) { 100.dp.toPx() }

    val prepared = remember(medians, strokes, canvasSizePx) {
        if (medians.isEmpty()) {
            emptyList()
        } else {
            val scale = canvasSizePx / HanziTransform.GRID_WIDTH

            val count = if (strokes.isEmpty()) {
                medians.size
            } else {
                minOf(medians.size, strokes.size)
            }

            val result = ArrayList<PreparedStroke>(count)

            for (i in 0 until count) {
                val points = medians[i].map { HanziTransform.toCanvas(it, scale) }
                if (points.isEmpty()) continue

                var svgAndroid: AndroidPath? = null
                var svgCompose: ComposePath? = null

                if (i < strokes.size) {
                    try {
                        val parsed = PathParser.createPathFromPathData(strokes[i])
                        parsed.transform(HanziTransform.matrix(scale))
                        svgAndroid = parsed
                        svgCompose = parsed.asComposePath()
                    } catch (_: Throwable) {
                        svgAndroid = null
                        svgCompose = null
                    }
                }

                val medianPath = buildSmoothAndroidPath(points)
                val measure = AndroidPathMeasure()
                measure.setPath(medianPath, false)

                val length = measure.length
                val maskWidth = estimateMaskWidth(svgAndroid, points, canvasSizePx)
                val isDot = points.size <= 1 || length < canvasSizePx * 0.045f

                val durationMs = if (isDot) {
                    210
                } else {
                    val normalizedLength = (length / canvasSizePx).coerceIn(0f, 1.5f)
                    (260 + normalizedLength * 420).toInt().coerceIn(260, 760)
                }

                result.add(
                    PreparedStroke(
                        svgPath = svgCompose,
                        fallbackPath = buildSmoothComposePath(points),
                        medianPath = medianPath,
                        measure = measure,
                        length = length,
                        maskWidth = maskWidth,
                        durationMs = durationMs,
                        isDot = isDot
                    )
                )
            }

            result
        }
    }

    var activeIndex by remember(prepared) {
        mutableIntStateOf(0)
    }

    val progress = remember(prepared) {
        Animatable(0f)
    }

    LaunchedEffect(prepared, autoPlay, loop, useSvgFill) {
        if (!autoPlay || prepared.isEmpty()) return@LaunchedEffect

        val easing = CubicBezierEasing(0.25f, 0.10f, 0.25f, 1.0f)

        while (true) {
            for (i in prepared.indices) {
                val stroke = prepared[i]

                activeIndex = i
                progress.snapTo(0f)

                progress.animateTo(
                    1f,
                    tween(
                        durationMillis = stroke.durationMs,
                        easing = easing
                    )
                )

                activeIndex = i + 1

                delay(if (stroke.isDot) 55L else 85L)
            }

            if (!loop) {
                onComplete()
                break
            }

            activeIndex = 0
            progress.snapTo(0f)
            delay(500L)
        }
    }

    Box(
        modifier = modifier.background(
            MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        if (showGuide) {
            GuideLayer(prepared = prepared)
        }

        CompletedLayer(
            prepared = prepared,
            completedCount = activeIndex,
            useSvgFill = useSvgFill
        )

        CurrentLayer(
            stroke = prepared.getOrNull(activeIndex),
            progress = progress,
            useSvgFill = useSvgFill
        )
    }
}

@Composable
private fun GuideLayer(
    prepared: List<PreparedStroke>
) {
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
        alpha = 0.10f
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val fallbackWidth = size.width * 0.07f

        for (stroke in prepared) {
            val svg = stroke.svgPath

            if (svg != null) {
                drawPath(
                    svg,
                    guideColor,
                    style = Fill
                )
            } else {
                drawPath(
                    stroke.fallbackPath,
                    guideColor,
                    style = Stroke(
                        width = fallbackWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

@Composable
private fun CompletedLayer(
    prepared: List<PreparedStroke>,
    completedCount: Int,
    useSvgFill: Boolean
) {
    val inkColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val count = completedCount.coerceIn(0, prepared.size)
        val fallbackWidth = size.width * 0.078f

        for (i in 0 until count) {
            val stroke = prepared[i]
            val svg = stroke.svgPath

            if (useSvgFill && svg != null) {
                drawPath(
                    svg,
                    inkColor,
                    style = Fill
                )
            } else {
                drawPath(
                    stroke.fallbackPath,
                    inkColor,
                    style = Stroke(
                        width = fallbackWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

@Composable
private fun CurrentLayer(
    stroke: PreparedStroke?,
    progress: Animatable<Float, AnimationVector1D>,
    useSvgFill: Boolean
) {
    if (stroke == null) return

    val t = progress.value
    val inkColor = MaterialTheme.colorScheme.primary

    val partial = remember(stroke) {
        AndroidPath()
    }

    val maskFill = remember(stroke) {
        AndroidPath()
    }

    val maskPaint = remember {
        Paint().apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val fallbackWidth = size.width * 0.078f
        val svg = stroke.svgPath

        if (useSvgFill && svg != null) {
            if (stroke.isDot) {
                val alpha = (0.15f + 0.85f * t).coerceIn(0f, 1f)

                drawPath(
                    svg,
                    inkColor.copy(alpha = alpha),
                    style = Fill
                )
            } else if (t >= 0.985f) {
                drawPath(
                    svg,
                    inkColor,
                    style = Fill
                )
            } else {
                val distance = stroke.length * t

                partial.reset()

                val ok = stroke.measure.getSegment(
                    0f,
                    distance,
                    partial,
                    true
                )

                if (!ok) {
                    drawPath(
                        svg,
                        inkColor,
                        style = Fill
                    )
                } else {
                    maskPaint.strokeWidth = stroke.maskWidth

                    maskFill.reset()
                    maskPaint.getFillPath(partial, maskFill)

                    clipPath(maskFill.asComposePath()) {
                        drawPath(
                            svg,
                            inkColor,
                            style = Fill
                        )
                    }
                }
            }
        } else {
            if (stroke.isDot) {
                val alpha = (0.15f + 0.85f * t).coerceIn(0f, 1f)

                drawPath(
                    stroke.fallbackPath,
                    inkColor.copy(alpha = alpha),
                    style = Stroke(
                        width = fallbackWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            } else if (t >= 0.99f) {
                drawPath(
                    stroke.fallbackPath,
                    inkColor,
                    style = Stroke(
                        width = fallbackWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            } else {
                partial.reset()

                val ok = stroke.measure.getSegment(
                    0f,
                    stroke.length * t,
                    partial,
                    true
                )

                if (ok) {
                    drawPath(
                        partial.asComposePath(),
                        inkColor,
                        style = Stroke(
                            width = fallbackWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}
