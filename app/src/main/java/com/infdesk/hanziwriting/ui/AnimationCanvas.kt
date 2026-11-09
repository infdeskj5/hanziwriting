package com.infdesk.hanziwriting.ui

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure as AndroidPathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.infdesk.hanziwriting.utils.HanziTransform
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sqrt

private class PreparedStroke(
    val svg: AndroidPath?,
    val median: AndroidPath,
    val measure: AndroidPathMeasure,
    val length: Float,
    val maskWidth: Float,
    val durationMs: Int,
    val pauseMs: Int,
    val isDot: Boolean,
    val layerLeft: Float,
    val layerTop: Float,
    val layerRight: Float,
    val layerBottom: Float
)

private fun dist(dx: Float, dy: Float): Float {
    return sqrt(dx * dx + dy * dy)
}

private fun easeInOutCubic(x: Float): Float {
    return if (x < 0.5f) {
        4f * x * x * x
    } else {
        val y = -2f * x + 2f
        1f - (y * y * y) / 2f
    }
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

private fun turnAmount(points: List<Offset>): Float {
    if (points.size < 3) return 0f

    var total = 0f

    for (i in 0 until points.size - 2) {
        val dx1 = points[i + 1].x - points[i].x
        val dy1 = points[i + 1].y - points[i].y

        val dx2 = points[i + 2].x - points[i + 1].x
        val dy2 = points[i + 2].y - points[i + 1].y

        val l1 = dist(dx1, dy1)
        val l2 = dist(dx2, dy2)

        if (l1 > 0.5f && l2 > 0.5f) {
            val dot = dx1 * dx2 + dy1 * dy2
            val cross = dx1 * dy2 - dy1 * dx2

            total += abs(atan2(cross, dot))
        }
    }

    return (total / PI.toFloat()).coerceIn(0f, 2f)
}

private fun setBoundsFromPoints(points: List<Offset>, out: RectF) {
    if (points.isEmpty()) {
        out.set(0f, 0f, 0f, 0f)
        return
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

    out.set(minX, minY, maxX, maxY)
}

private fun estimateMaskWidth(
    bounds: RectF,
    canvasSizePx: Float
): Float {
    val w = bounds.width()
    val h = bounds.height()

    if (w <= 0f || h <= 0f) {
        return canvasSizePx * 0.11f
    }

    val maxDim = max(w, h)
    val minDim = min(w, h)

    val isSmall = maxDim < canvasSizePx * 0.20f

    val suggested = if (isSmall) {
        maxDim * 1.45f + canvasSizePx * 0.025f
    } else {
        canvasSizePx * 0.11f + minDim * 0.12f
    }

    return suggested.coerceIn(
        canvasSizePx * 0.08f,
        canvasSizePx * 0.17f
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
    frameDelayMs: Int = 33,
    onComplete: () -> Unit = {}
) {
    val density = LocalDensity.current
    val canvasSizePx = with(density) { 100.dp.toPx() }
    val safeFrameDelay = frameDelayMs.coerceIn(16, 80)

    val prepared = remember(medians, strokes, canvasSizePx) {
        if (medians.isEmpty()) {
            emptyList<PreparedStroke>()
        } else {
            val scale = canvasSizePx / HanziTransform.GRID_WIDTH

            val count = if (strokes.isEmpty()) {
                medians.size
            } else {
                minOf(medians.size, strokes.size)
            }

            val out = ArrayList<PreparedStroke>(count)

            for (i in 0 until count) {
                val points = medians[i].map {
                    HanziTransform.toCanvas(it, scale)
                }

                if (points.isEmpty()) continue

                var svg: AndroidPath? = null

                if (i < strokes.size) {
                    try {
                        val parsed = PathParser.createPathFromPathData(strokes[i])
                        parsed.transform(HanziTransform.matrix(scale))
                        svg = parsed
                    } catch (_: Throwable) {
                        svg = null
                    }
                }

                val median = buildSmoothAndroidPath(points)

                val measure = AndroidPathMeasure()
                measure.setPath(median, false)

                val length = measure.length

                val bounds = RectF()
                var hasBounds = false

                if (svg != null) {
                    try {
                        svg.computeBounds(bounds, true)
                        hasBounds = bounds.width() > 0.5f && bounds.height() > 0.5f
                    } catch (_: Throwable) {
                        hasBounds = false
                    }
                }

                if (!hasBounds) {
                    setBoundsFromPoints(points, bounds)
                }

                if (bounds.width() <= 0f || bounds.height() <= 0f) {
                    bounds.set(0f, 0f, canvasSizePx, canvasSizePx)
                }

                val maskWidth = estimateMaskWidth(bounds, canvasSizePx)
                val turns = turnAmount(points)

                val isDot = points.size <= 1 || length < canvasSizePx * 0.05f

                val durationMs = if (isDot) {
                    180
                } else {
                    (250f + (length / canvasSizePx) * 380f + turns * 110f)
                        .toInt()
                        .coerceIn(250, 820)
                }

                val pauseMs = if (isDot) 45 else 75

                val layerInset = maskWidth * 1.45f + 10f
                bounds.inflate(layerInset, layerInset)

                val left = max(0f, bounds.left)
                val top = max(0f, bounds.top)
                val right = min(canvasSizePx, bounds.right)
                val bottom = min(canvasSizePx, bounds.bottom)

                out.add(
                    PreparedStroke(
                        svg = svg,
                        median = median,
                        measure = measure,
                        length = length,
                        maskWidth = maskWidth,
                        durationMs = durationMs,
                        pauseMs = pauseMs,
                        isDot = isDot,
                        layerLeft = left,
                        layerTop = top,
                        layerRight = right,
                        layerBottom = bottom
                    )
                )
            }

            out
        }
    }

    var currentStroke by remember(prepared) {
        mutableIntStateOf(0)
    }

    val progressState = remember(prepared) {
        mutableStateOf(0f)
    }

    LaunchedEffect(prepared, autoPlay, loop, safeFrameDelay) {
        if (!autoPlay || prepared.isEmpty()) return@LaunchedEffect

        while (true) {
            for (i in prepared.indices) {
                val stroke = prepared[i]

                currentStroke = i
                progressState.value = 0f

                val frames = max(4, stroke.durationMs / safeFrameDelay)

                for (frame in 1..frames) {
                    val linear = frame.toFloat() / frames.toFloat()
                    progressState.value = easeInOutCubic(linear)
                    delay(safeFrameDelay.toLong())
                }

                progressState.value = 1f
                currentStroke = i + 1

                delay(stroke.pauseMs.toLong())
            }

            if (!loop) {
                onComplete()
                break
            }

            currentStroke = 0
            progressState.value = 0f

            delay(500L)
        }
    }

    Box(
        modifier = modifier.background(
            MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        if (showGuide) {
            GuideLayer(
                prepared = prepared,
                canvasSizePx = canvasSizePx
            )
        }

        CompletedLayer(
            prepared = prepared,
            completedCount = currentStroke,
            canvasSizePx = canvasSizePx
        )

        CurrentLayer(
            stroke = prepared.getOrNull(currentStroke),
            progress = progressState,
            useSvgFill = useSvgFill,
            canvasSizePx = canvasSizePx
        )
    }
}

@Composable
private fun GuideLayer(
    prepared: List<PreparedStroke>,
    canvasSizePx: Float
) {
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant
        .copy(alpha = 0.22f)
        .toArgb()

    val fillPaint = remember(guideColor) {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = guideColor
        }
    }

    val strokePaint = remember(guideColor, canvasSizePx) {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = guideColor
            strokeWidth = canvasSizePx * 0.075f
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas

            for (stroke in prepared) {
                val svg = stroke.svg

                if (svg != null) {
                    nativeCanvas.drawPath(svg, fillPaint)
                } else {
                    nativeCanvas.drawPath(stroke.median, strokePaint)
                }
            }
        }
    }
}

@Composable
private fun CompletedLayer(
    prepared: List<PreparedStroke>,
    completedCount: Int,
    canvasSizePx: Float
) {
    val inkColor = MaterialTheme.colorScheme.primary.toArgb()

    val fillPaint = remember(inkColor) {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = inkColor
        }
    }

    val strokePaint = remember(inkColor, canvasSizePx) {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = inkColor
            strokeWidth = canvasSizePx * 0.082f
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas

            val count = completedCount.coerceIn(0, prepared.size)

            for (i in 0 until count) {
                val stroke = prepared[i]
                val svg = stroke.svg

                if (svg != null) {
                    nativeCanvas.drawPath(svg, fillPaint)
                } else {
                    nativeCanvas.drawPath(stroke.median, strokePaint)
                }
            }
        }
    }
}

@Composable
private fun CurrentLayer(
    stroke: PreparedStroke?,
    progress: MutableState<Float>,
    useSvgFill: Boolean,
    canvasSizePx: Float
) {
    if (stroke == null) return

    val inkColor = MaterialTheme.colorScheme.primary.toArgb()

    val fillPaint = remember(inkColor) {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = inkColor
        }
    }

    val inkStrokePaint = remember(inkColor) {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = inkColor
        }
    }

    val maskPaint = remember {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = AndroidColor.WHITE
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
    }

    val workPath = remember(stroke) {
        AndroidPath()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val t = progress.value.coerceIn(0f, 1f)
            val svg = stroke.svg

            if (svg == null || !useSvgFill) {
                workPath.reset()

                if (stroke.measure.getSegment(
                        0f,
                        stroke.length * t,
                        workPath,
                        true
                    )
                ) {
                    inkStrokePaint.strokeWidth = stroke.maskWidth * 0.95f
                    nativeCanvas.drawPath(workPath, inkStrokePaint)
                }
            } else if (stroke.isDot || stroke.length < canvasSizePx * 0.055f) {
                fillPaint.alpha = (35 + 220 * t).toInt().coerceIn(0, 255)
                nativeCanvas.drawPath(svg, fillPaint)
                fillPaint.alpha = 255
            } else if (t >= 0.985f) {
                nativeCanvas.drawPath(svg, fillPaint)
            } else {
                workPath.reset()

                val ok = stroke.measure.getSegment(
                    0f,
                    stroke.length * t,
                    workPath,
                    true
                )

                if (
                    !ok ||
                    stroke.layerRight <= stroke.layerLeft ||
                    stroke.layerBottom <= stroke.layerTop
                ) {
                    nativeCanvas.drawPath(svg, fillPaint)
                } else {
                    nativeCanvas.saveLayer(
                        stroke.layerLeft,
                        stroke.layerTop,
                        stroke.layerRight,
                        stroke.layerBottom,
                        null
                    )

                    nativeCanvas.drawPath(svg, fillPaint)

                    maskPaint.strokeWidth = stroke.maskWidth
                    nativeCanvas.drawPath(workPath, maskPaint)

                    nativeCanvas.restore()
                }
            }
        }
    }
}
