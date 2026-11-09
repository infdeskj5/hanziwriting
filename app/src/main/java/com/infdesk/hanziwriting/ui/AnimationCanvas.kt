package com.infdesk.hanziwriting.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure as AndroidPathMeasure
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlin.math.ceil
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
    val penWidth: Float,
    val durationMs: Int,
    val pauseMs: Int,
    val isDot: Boolean,
    val startX: Float,
    val startY: Float,
    val layerLeft: Float,
    val layerTop: Float,
    val layerRight: Float,
    val layerBottom: Float
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
        path.quadTo(current.x, current.y, (current.x + next.x) / 2f, (current.y + next.y) / 2f)
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
            total += abs(atan2(dx1 * dy2 - dy1 * dx2, dx1 * dx2 + dy1 * dy2))
        }
    }
    return (total / PI.toFloat()).coerceIn(0f, 2f)
}

private fun setBoundsFromPoints(points: List<Offset>, out: RectF) {
    if (points.isEmpty()) { out.set(0f, 0f, 0f, 0f); return }
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    for (p in points) {
        if (p.x < minX) minX = p.x; if (p.y < minY) minY = p.y
        if (p.x > maxX) maxX = p.x; if (p.y > maxY) maxY = p.y
    }
    out.set(minX, minY, maxX, maxY)
}

private fun estimateMaskWidth(bounds: RectF, canvasSizePx: Float): Float {
    val w = bounds.width(); val h = bounds.height()
    if (w <= 0f || h <= 0f) return canvasSizePx * 0.11f
    val maxDim = max(w, h); val minDim = min(w, h)
    val suggested = if (maxDim < canvasSizePx * 0.20f) maxDim * 1.45f + canvasSizePx * 0.025f
                    else canvasSizePx * 0.11f + minDim * 0.12f
    return suggested.coerceIn(canvasSizePx * 0.08f, canvasSizePx * 0.17f)
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
    val bitmapSize = ceil(canvasSizePx).toInt().coerceAtLeast(1)
    val safeFrameDelay = frameDelayMs.coerceIn(16, 80)

    val prepared = remember(medians, strokes, canvasSizePx) {
        if (medians.isEmpty()) emptyList<PreparedStroke>()
        else {
            val scale = canvasSizePx / HanziTransform.GRID_WIDTH
            val count = if (strokes.isEmpty()) medians.size else minOf(medians.size, strokes.size)
            val out = ArrayList<PreparedStroke>(count)
            for (i in 0 until count) {
                val points = medians[i].map { HanziTransform.toCanvas(it, scale) }
                if (points.isEmpty()) continue
                var svg: AndroidPath? = null
                if (i < strokes.size) {
                    try {
                        val parsed = PathParser.createPathFromPathData(strokes[i])
                        parsed.transform(HanziTransform.matrix(scale))
                        svg = parsed
                    } catch (_: Throwable) { svg = null }
                }
                val median = buildSmoothAndroidPath(points)
                val measure = AndroidPathMeasure().apply { setPath(median, false) }
                val length = measure.length
                val bounds = RectF()
                var hasBounds = false
                if (svg != null) {
                    try { svg.computeBounds(bounds, true); hasBounds = bounds.width() > 0.5f && bounds.height() > 0.5f }
                    catch (_: Throwable) { hasBounds = false }
                }
                if (!hasBounds) setBoundsFromPoints(points, bounds)
                if (bounds.width() <= 0f || bounds.height() <= 0f) bounds.set(0f, 0f, canvasSizePx, canvasSizePx)
                
                val maskWidth = estimateMaskWidth(bounds, canvasSizePx)
                val penWidth = if (svg != null) maskWidth * 0.92f else canvasSizePx * 0.075f
                val turns = turnAmount(points)
                val isDot = points.size <= 1 || length < canvasSizePx * 0.05f
                val durationMs = if (isDot) 180 else (250f + (length / canvasSizePx) * 380f + turns * 110f).toInt().coerceIn(250, 820)
                val pauseMs = if (isDot) 45 else 75
                val inset = maskWidth * 1.45f + 10f
                
                out.add(PreparedStroke(
                    svg = svg, median = median, measure = measure, length = length,
                    maskWidth = maskWidth, penWidth = penWidth, durationMs = durationMs, pauseMs = pauseMs,
                    isDot = isDot, startX = points.first().x, startY = points.first().y,
                    layerLeft = max(0f, bounds.left - inset), layerTop = max(0f, bounds.top - inset),
                    layerRight = min(canvasSizePx, bounds.right + inset), layerBottom = min(canvasSizePx, bounds.bottom + inset)
                ))
            }
            out
        }
    }

    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f).toArgb()
    val inkColor = MaterialTheme.colorScheme.primary.toArgb()

    val guideBitmap = remember(prepared, bitmapSize, guideColor) {
        val bmp = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = guideColor }
        val strokePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = guideColor }
        for (stroke in prepared) {
            if (stroke.svg != null) canvas.drawPath(stroke.svg, fillPaint)
            else if (stroke.isDot) canvas.drawCircle(stroke.startX, stroke.startY, stroke.penWidth * 0.5f, fillPaint)
            else { strokePaint.strokeWidth = stroke.penWidth * 0.85f; canvas.drawPath(stroke.median, strokePaint) }
        }
        bmp
    }

    val completedBitmap = remember(prepared, bitmapSize) {
        Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888)
    }
    val completedCanvas = remember(completedBitmap) { AndroidCanvas(completedBitmap) }

    val fillPaint = remember(inkColor) { Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = inkColor } }
    val strokePaint = remember(inkColor) { Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = inkColor } }

    // Simple lambda definition (fixes the Unit mismatch error)
    val drawCompleted = { index: Int ->
        if (index in prepared.indices) {
            val stroke = prepared[index]
            if (stroke.svg != null) completedCanvas.drawPath(stroke.svg, fillPaint)
            else if (stroke.isDot) completedCanvas.drawCircle(stroke.startX, stroke.startY, stroke.penWidth * 0.5f, fillPaint)
            else { strokePaint.strokeWidth = stroke.penWidth; completedCanvas.drawPath(stroke.median, strokePaint) }
        }
    }

    var currentStroke by remember(prepared) { mutableIntStateOf(0) }
    val progress = remember(prepared) { mutableStateOf(0f) }

    LaunchedEffect(prepared, completedBitmap) {
        completedBitmap.eraseColor(AndroidColor.TRANSPARENT)
    }

    // Removed drawCompleted from keys to prevent trailing lambda confusion
    LaunchedEffect(prepared, autoPlay, loop, safeFrameDelay, completedBitmap) {
        if (!autoPlay || prepared.isEmpty()) {
            if (!autoPlay) onComplete()
            return@LaunchedEffect
        }

        while (true) {
            completedBitmap.eraseColor(AndroidColor.TRANSPARENT)
            currentStroke = 0
            progress.value = 0f

            for (i in prepared.indices) {
                val stroke = prepared[i]
                currentStroke = i
                progress.value = 0f

                val frames = max(6, stroke.durationMs / safeFrameDelay)
                for (frame in 1..frames) {
                    progress.value = easeInOutCubic(frame.toFloat() / frames.toFloat())
                    delay(safeFrameDelay.toLong())
                }
                progress.value = 1f
                
                drawCompleted(i)
                
                currentStroke = i + 1
                delay(stroke.pauseMs.toLong())
            }

            if (!loop) {
                onComplete()
                break
            }
            delay(500L)
        }
    }

    val workPath = remember(prepared) { AndroidPath() }
    val currentInkFill = remember(inkColor) { Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = inkColor } }
    val currentInkStroke = remember(inkColor) { Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = inkColor } }
    val maskPaint = remember { Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = AndroidColor.WHITE; xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) } }

    Canvas(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        val nativeCanvas = drawContext.canvas.nativeCanvas
        if (showGuide) nativeCanvas.drawBitmap(guideBitmap, 0f, 0f, null)
        nativeCanvas.drawBitmap(completedBitmap, 0f, 0f, null)

        if (autoPlay && currentStroke < prepared.size) {
            val stroke = prepared[currentStroke]
            val t = progress.value.coerceIn(0f, 1f)
            val svg = stroke.svg

            if (svg != null && useSvgFill) {
                if (stroke.isDot) {
                    val oldAlpha = currentInkFill.alpha
                    currentInkFill.alpha = (255f * t).toInt().coerceIn(0, 255)
                    nativeCanvas.drawPath(svg, currentInkFill)
                    currentInkFill.alpha = oldAlpha
                } else if (t >= 0.985f) {
                    nativeCanvas.drawPath(svg, currentInkFill)
                } else if (t > 0.01f) {
                    workPath.reset()
                    if (stroke.measure.getSegment(0f, stroke.length * t, workPath, true)) {
                        val left = stroke.layerLeft; val top = stroke.layerTop
                        val right = stroke.layerRight; val bottom = stroke.layerBottom
                        if (right <= left || bottom <= top) {
                            nativeCanvas.drawPath(svg, currentInkFill)
                        } else {
                            nativeCanvas.saveLayer(left, top, right, bottom, null)
                            nativeCanvas.drawPath(svg, currentInkFill)
                            maskPaint.strokeWidth = stroke.maskWidth
                            nativeCanvas.drawPath(workPath, maskPaint)
                            nativeCanvas.restore()
                        }
                    } else {
                        nativeCanvas.drawPath(svg, currentInkFill)
                    }
                }
            } else {
                if (stroke.isDot) {
                    val oldAlpha = currentInkFill.alpha
                    currentInkFill.alpha = (255f * t).toInt().coerceIn(0, 255)
                    nativeCanvas.drawCircle(stroke.startX, stroke.startY, stroke.penWidth * 0.5f * (0.35f + 0.65f * t), currentInkFill)
                    currentInkFill.alpha = oldAlpha
                } else if (t >= 0.99f) {
                    if (svg != null) nativeCanvas.drawPath(svg, currentInkFill)
                    else { currentInkStroke.strokeWidth = stroke.penWidth; nativeCanvas.drawPath(stroke.median, currentInkStroke) }
                } else if (t > 0.01f) {
                    workPath.reset()
                    if (stroke.measure.getSegment(0f, stroke.length * t, workPath, true)) {
                        currentInkStroke.strokeWidth = stroke.penWidth
                        nativeCanvas.drawPath(workPath, currentInkStroke)
                    }
                }
            }
        }
    }
}
