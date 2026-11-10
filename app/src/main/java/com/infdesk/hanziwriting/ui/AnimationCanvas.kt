package com.infdesk.hanziwriting.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure as AndroidPathMeasure
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.infdesk.hanziwriting.utils.HanziTransform
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private class PreparedStroke(
    val svg: AndroidPath?,
    val median: AndroidPath,
    val measure: AndroidPathMeasure,
    val length: Float,
    val maskWidth: Float,
    val penWidth: Float,
    val durationMs: Int,
    val pauseMs: Int,
    val isDot: Boolean,
    val startX: Float,
    val startY: Float
)

private fun dist(dx: Float, dy: Float): Float = sqrt(dx * dx + dy * dy)

private fun easeInOutCubic(x: Float): Float =
    if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f

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
        path.quadTo(
            current.x,
            current.y,
            (current.x + next.x) / 2f,
            (current.y + next.y) / 2f
        )
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
        val length1 = dist(dx1, dy1)
        val length2 = dist(dx2, dy2)

        if (length1 > 0.5f && length2 > 0.5f) {
            total += abs(atan2(dx1 * dy2 - dy1 * dx2, dx1 * dx2 + dy1 * dy2))
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

    for (point in points) {
        minX = min(minX, point.x)
        minY = min(minY, point.y)
        maxX = max(maxX, point.x)
        maxY = max(maxY, point.y)
    }
    out.set(minX, minY, maxX, maxY)
}

private fun estimateMaskWidth(bounds: RectF, canvasSizePx: Float): Float {
    val width = bounds.width()
    val height = bounds.height()
    if (width <= 0f || height <= 0f) return canvasSizePx * 0.11f

    val maxDimension = max(width, height)
    val minDimension = min(width, height)
    val suggested = if (maxDimension < canvasSizePx * 0.20f) {
        maxDimension * 1.45f + canvasSizePx * 0.025f
    } else {
        canvasSizePx * 0.11f + minDimension * 0.12f
    }
    return suggested.coerceIn(canvasSizePx * 0.08f, canvasSizePx * 0.17f)
}

/**
 * Draws Hanzi stroke order using the display frame clock.
 *
 * frameDelayMs is retained so existing call sites continue to compile. Animation
 * timing is now synchronised to the device refresh rate instead of using delay().
 */
@Composable
fun AnimationCanvas(
    medians: List<List<List<Double>>>,
    strokes: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    loop: Boolean = false,
    showGuide: Boolean = true,
    useSvgFill: Boolean = true,
    @Suppress("UNUSED_PARAMETER") frameDelayMs: Int = 16,
    onComplete: () -> Unit = {}
) {
    val density = LocalDensity.current
    val canvasSizePx = with(density) { 100.dp.toPx() }
    val bitmapSize = ceil(canvasSizePx).toInt().coerceAtLeast(1)

    // Parse SVG paths and build PathMeasures once, not while drawing frames.
    val prepared = remember(medians, strokes, canvasSizePx) {
        if (medians.isEmpty()) {
            emptyList()
        } else {
            val scale = canvasSizePx / HanziTransform.GRID_WIDTH
            val count = if (strokes.isEmpty()) medians.size else minOf(medians.size, strokes.size)
            val result = ArrayList<PreparedStroke>(count)

            for (i in 0 until count) {
                val points = medians[i].map { HanziTransform.toCanvas(it, scale) }
                if (points.isEmpty()) continue

                val svg = if (i < strokes.size) {
                    try {
                        PathParser.createPathFromPathData(strokes[i]).apply {
                            transform(HanziTransform.matrix(scale))
                        }
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }

                val median = buildSmoothAndroidPath(points)
                val measure = AndroidPathMeasure().apply { setPath(median, false) }
                val length = measure.length
                val bounds = RectF()
                var hasBounds = false

                if (svg != null) {
                    try {
                        svg.computeBounds(bounds, true)
                        hasBounds = bounds.width() > 0.5f && bounds.height() > 0.5f
                    } catch (_: Exception) {
                        hasBounds = false
                    }
                }

                if (!hasBounds) setBoundsFromPoints(points, bounds)
                if (bounds.width() <= 0f || bounds.height() <= 0f) {
                    bounds.set(0f, 0f, canvasSizePx, canvasSizePx)
                }

                val maskWidth = estimateMaskWidth(bounds, canvasSizePx)
                val penWidth = if (svg != null) maskWidth * 0.92f else canvasSizePx * 0.075f
                val isDot = points.size <= 1 || length < canvasSizePx * 0.05f
                val turns = turnAmount(points)
                val durationMs = if (isDot) {
                    180
                } else {
                    (250f + (length / canvasSizePx) * 380f + turns * 110f)
                        .toInt()
                        .coerceIn(250, 820)
                }

                result.add(
                    PreparedStroke(
                        svg = svg,
                        median = median,
                        measure = measure,
                        length = length,
                        maskWidth = maskWidth,
                        penWidth = penWidth,
                        durationMs = durationMs,
                        pauseMs = if (isDot) 45 else 75,
                        isDot = isDot,
                        startX = points.first().x,
                        startY = points.first().y
                    )
                )
            }
            result
        }
    }

    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f).toArgb()
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f).toArgb()
    val inkColor = MaterialTheme.colorScheme.primary.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val tileShape = RoundedCornerShape(18.dp)

    // Static guide and grid are rasterised once. They are not redrawn path by path
    // on every animation frame.
    val guideBitmap = remember(prepared, bitmapSize, guideColor, gridColor) {
        Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = AndroidCanvas(bitmap)
            val gridPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = (canvasSizePx / 300f).coerceAtLeast(1f)
                color = gridColor
            }
            val middle = canvasSizePx / 2f
            canvas.drawLine(middle, 0f, middle, canvasSizePx, gridPaint)
            canvas.drawLine(0f, middle, canvasSizePx, middle, gridPaint)
            canvas.drawLine(0f, 0f, canvasSizePx, canvasSizePx, gridPaint)
            canvas.drawLine(canvasSizePx, 0f, 0f, canvasSizePx, gridPaint)

            val fillPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = guideColor
            }
            val strokePaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = guideColor
            }

            for (stroke in prepared) {
                when {
                    stroke.svg != null -> canvas.drawPath(stroke.svg, fillPaint)
                    stroke.isDot -> canvas.drawCircle(
                        stroke.startX,
                        stroke.startY,
                        stroke.penWidth * 0.5f,
                        fillPaint
                    )
                    else -> {
                        strokePaint.strokeWidth = stroke.penWidth * 0.85f
                        canvas.drawPath(stroke.median, strokePaint)
                    }
                }
            }
        }
    }

    val completedBitmap = remember(prepared, bitmapSize) {
        Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888)
    }
    val completedCanvas = remember(completedBitmap) { AndroidCanvas(completedBitmap) }

    val completedFillPaint = remember(inkColor) {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = inkColor
        }
    }
    val completedStrokePaint = remember(inkColor) {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = inkColor
        }
    }

    val drawCompleted: (Int) -> Unit = { index ->
        if (index in prepared.indices) {
            val stroke = prepared[index]
            when {
                stroke.svg != null -> completedCanvas.drawPath(stroke.svg, completedFillPaint)
                stroke.isDot -> completedCanvas.drawCircle(
                    stroke.startX,
                    stroke.startY,
                    stroke.penWidth * 0.5f,
                    completedFillPaint
                )
                else -> {
                    completedStrokePaint.strokeWidth = stroke.penWidth
                    completedCanvas.drawPath(stroke.median, completedStrokePaint)
                }
            }
        }
    }

    var currentStroke by remember(prepared) { mutableIntStateOf(0) }
    val progress = remember(prepared) { mutableStateOf(0f) }

    LaunchedEffect(prepared, autoPlay, loop, completedBitmap) {
        if (!autoPlay || prepared.isEmpty()) {
            if (!autoPlay) onComplete()
            return@LaunchedEffect
        }

        do {
            completedBitmap.eraseColor(AndroidColor.TRANSPARENT)
            currentStroke = 0
            progress.value = 0f

            for (index in prepared.indices) {
                val stroke = prepared[index]
                currentStroke = index
                progress.value = 0f

                // Wait for real display frames. This avoids the uneven cadence and
                // low update rate caused by delay(45/55) in the old implementation.
                val startNanos = withFrameNanos { it }
                val durationNanos = stroke.durationMs * 1_000_000L
                var fraction: Float

                do {
                    val frameNanos = withFrameNanos { it }
                    fraction = ((frameNanos - startNanos).toFloat() / durationNanos)
                        .coerceIn(0f, 1f)
                    progress.value = easeInOutCubic(fraction)
                } while (fraction < 1f)

                progress.value = 1f
                drawCompleted(index)
                currentStroke = index + 1
                delay(stroke.pauseMs.toLong())
            }

            if (!loop) {
                onComplete()
                break
            }
            delay(500L)
        } while (true)
    }

    // Reused scratch paths and paints: no per-frame object allocation.
    val workPath = remember(prepared) { AndroidPath() }
    val revealPath = remember(prepared) { AndroidPath() }
    val currentInkFill = remember(inkColor) {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = inkColor
        }
    }
    val currentInkStroke = remember(inkColor) {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = inkColor
        }
    }
    val revealPaint = remember {
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = AndroidColor.WHITE
        }
    }

    Canvas(
        modifier = modifier
            .clip(tileShape)
            .background(surfaceColor, tileShape)
    ) {
        val canvas = drawContext.canvas.nativeCanvas
        if (showGuide) canvas.drawBitmap(guideBitmap, 0f, 0f, null)
        canvas.drawBitmap(completedBitmap, 0f, 0f, null)

        if (autoPlay && currentStroke < prepared.size) {
            val stroke = prepared[currentStroke]
            val t = progress.value.coerceIn(0f, 1f)
            val svg = stroke.svg

            if (svg != null && useSvgFill) {
                if (stroke.isDot) {
                    val oldAlpha = currentInkFill.alpha
                    currentInkFill.alpha = (255f * t).toInt().coerceIn(0, 255)
                    canvas.drawPath(svg, currentInkFill)
                    currentInkFill.alpha = oldAlpha
                } else if (t >= 0.985f) {
                    canvas.drawPath(svg, currentInkFill)
                } else if (t > 0.01f) {
                    workPath.reset()
                    revealPath.reset()

                    if (stroke.measure.getSegment(0f, stroke.length * t, workPath, true)) {
                        // Convert the moving centre line to a filled, round-ended
                        // reveal shape. Clipping avoids the saveLayer + DST_IN
                        // off-screen compositing pass that was expensive on Mali.
                        revealPaint.strokeWidth = stroke.maskWidth
                        if (revealPaint.getFillPath(workPath, revealPath)) {
                            val saveCount = canvas.save()
                            canvas.clipPath(revealPath)
                            canvas.drawPath(svg, currentInkFill)
                            canvas.restoreToCount(saveCount)
                        }
                    }
                }
            } else {
                if (stroke.isDot) {
                    val oldAlpha = currentInkFill.alpha
                    currentInkFill.alpha = (255f * t).toInt().coerceIn(0, 255)
                    canvas.drawCircle(
                        stroke.startX,
                        stroke.startY,
                        stroke.penWidth * 0.5f * (0.35f + 0.65f * t),
                        currentInkFill
                    )
                    currentInkFill.alpha = oldAlpha
                } else if (t >= 0.99f) {
                    if (svg != null) {
                        canvas.drawPath(svg, currentInkFill)
                    } else {
                        currentInkStroke.strokeWidth = stroke.penWidth
                        canvas.drawPath(stroke.median, currentInkStroke)
                    }
                } else if (t > 0.01f) {
                    workPath.reset()
                    if (stroke.measure.getSegment(0f, stroke.length * t, workPath, true)) {
                        currentInkStroke.strokeWidth = stroke.penWidth
                        canvas.drawPath(workPath, currentInkStroke)
                    }
                }
            }
        }
    }
}
