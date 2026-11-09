package com.infdesk.hanziwriting.ui

import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure as AndroidPathMeasure
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.PI
import kotlin.math.sqrt

private data class KeyFrame(
    val fraction: Float,
    val pauseMs: Int
)

private data class PreparedStroke(
    val svgPath: ComposePath?,
    val fallbackPath: ComposePath,
    val measure: AndroidPathMeasure,
    val length: Float,
    val points: List<Offset>,
    val cumulative: FloatArray,
    val cornerBoost: FloatArray,
    val cornerDelay: FloatArray,
    val keyFrames: List<KeyFrame>,
    val maskWidth: Float,
    val startX: Float,
    val startY: Float,
    val isDot: Boolean
)

private fun len(dx: Float, dy: Float): Float {
    return sqrt(dx * dx + dy * dy)
}

private fun buildAndroidPath(points: List<Offset>): AndroidPath {
    val path = AndroidPath()
    if (points.isEmpty()) return path

    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        path.lineTo(points[i].x, points[i].y)
    }
    return path
}

private fun buildComposePath(points: List<Offset>): ComposePath {
    val path = ComposePath()
    if (points.isEmpty()) return path

    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        path.lineTo(points[i].x, points[i].y)
    }
    return path
}

private fun buildCumulative(points: List<Offset>): FloatArray {
    val cumulative = FloatArray(points.size)
    for (i in 1 until points.size) {
        val dx = points[i].x - points[i - 1].x
        val dy = points[i].y - points[i - 1].y
        cumulative[i] = cumulative[i - 1] + len(dx, dy)
    }
    return cumulative
}

private fun angleAt(points: List<Offset>, index: Int): Float {
    if (index <= 0 || index >= points.size - 1) return 0f

    val dx1 = points[index].x - points[index - 1].x
    val dy1 = points[index].y - points[index - 1].y
    val dx2 = points[index + 1].x - points[index].x
    val dy2 = points[index + 1].y - points[index].y

    val l1 = len(dx1, dy1)
    val l2 = len(dx2, dy2)

    if (l1 < 0.5f || l2 < 0.5f) return 0f

    val dot = (dx1 * dx2 + dy1 * dy2) / (l1 * l2)
    val cross = (dx1 * dy2 - dy1 * dx2) / (l1 * l2)

    return abs(atan2(cross, dot))
}

private fun buildCornerBoost(points: List<Offset>, maskWidth: Float): FloatArray {
    val boost = FloatArray(points.size)

    if (points.isEmpty()) return boost

    if (points.size == 1) {
        boost[0] = maskWidth * 0.95f
        return boost
    }

    boost[0] = maskWidth * 0.75f
    boost[points.size - 1] = maskWidth * 0.85f

    for (i in 1 until points.size - 1) {
        val turn = angleAt(points, i) / PI.toFloat()
        boost[i] = maskWidth * (0.58f + turn * 0.75f)
    }

    return boost
}

private fun buildCornerDelay(points: List<Offset>): FloatArray {
    val delay = FloatArray(points.size)

    for (i in 1 until points.size - 1) {
        val turn = angleAt(points, i) / PI.toFloat()

        delay[i] = if (turn > 0.14f) {
            (turn * 150f).coerceIn(0f, 115f)
        } else {
            0f
        }
    }

    return delay
}

private fun buildKeyFrames(
    cumulative: FloatArray,
    cornerDelay: FloatArray,
    length: Float
): List<KeyFrame> {
    val frames = ArrayList<KeyFrame>()
    frames.add(KeyFrame(0f, 0))

    if (length <= 0f || cumulative.size <= 2) {
        frames.add(KeyFrame(1f, 0))
        return frames
    }

    var lastFraction = 0f

    for (i in 1 until cumulative.size - 1) {
        val fraction = (cumulative[i] / length).coerceIn(0f, 1f)

        if (fraction <= 0.008f || fraction >= 0.992f) continue

        val pause = cornerDelay[i].toInt().coerceIn(0, 140)

        if (fraction - lastFraction < 0.028f) {
            if (frames.size > 1) {
                val previous = frames.removeAt(frames.size - 1)
                frames.add(
                    KeyFrame(
                        fraction = fraction,
                        pauseMs = max(previous.pauseMs, pause)
                    )
                )
                lastFraction = fraction
            } else {
                frames.add(KeyFrame(fraction, pause))
                lastFraction = fraction
            }
        } else {
            frames.add(KeyFrame(fraction, pause))
            lastFraction = fraction
        }
    }

    frames.add(KeyFrame(1f, 0))
    return frames
}

private fun estimateMaskWidth(
    svg: AndroidPath?,
    points: List<Offset>,
    canvasSizePx: Float
): Float {
    val bounds = RectF()
    svg?.getBounds(bounds)

    if (bounds.isEmpty) {
        return canvasSizePx * 0.18f
    }

    var dx = 1f
    var dy = 0f
    var bestSegmentLength = 0f

    for (i in 0 until points.size - 1) {
        val sx = points[i + 1].x - points[i].x
        val sy = points[i + 1].y - points[i].y
        val segmentLength = len(sx, sy)

        if (segmentLength > bestSegmentLength) {
            bestSegmentLength = segmentLength
            dx = sx
            dy = sy
        }
    }

    val directionLength = len(dx, dy)

    if (directionLength > 0.5f) {
        dx /= directionLength
        dy /= directionLength
    } else {
        dx = 1f
        dy = 0f
    }

    val perpX = -dy
    val perpY = dx

    val perpendicularExtent =
        abs(bounds.width() * perpX) + abs(bounds.height() * perpY)

    val suggested = perpendicularExtent * 1.9f + canvasSizePx * 0.04f

    return suggested.coerceIn(
        canvasSizePx * 0.145f,
        canvasSizePx * 0.32f
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
                    } catch (_: Exception) {
                        svgAndroid = null
                        svgCompose = null
                    }
                }

                val rawPath = buildAndroidPath(points)
                val measure = AndroidPathMeasure()
                measure.setPath(rawPath, false)

                val length = measure.length
                val cumulative = buildCumulative(points)
                val maskWidth = estimateMaskWidth(svgAndroid, points, canvasSizePx)
                val cornerBoost = buildCornerBoost(points, maskWidth)
                val cornerDelay = buildCornerDelay(points)
                val keyFrames = buildKeyFrames(cumulative, cornerDelay, length)

                val isDot = points.size == 1 || length < canvasSizePx * 0.045f

                result.add(
                    PreparedStroke(
                        svgPath = svgCompose,
                        fallbackPath = buildComposePath(points),
                        measure = measure,
                        length = length,
                        points = points,
                        cumulative = cumulative,
                        cornerBoost = cornerBoost,
                        cornerDelay = cornerDelay,
                        keyFrames = keyFrames,
                        maskWidth = maskWidth,
                        startX = points.first().x,
                        startY = points.first().y,
                        isDot = isDot
                    )
                )
            }

            result
        }
    }

    val progress = remember { Animatable(0f) }

    var activeIndex by remember { mutableIntStateOf(0) }
    var completedCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(prepared, autoPlay, loop) {
        if (!autoPlay || prepared.isEmpty()) return@LaunchedEffect

        while (true) {
            for (i in prepared.indices) {
                val stroke = prepared[i]

                activeIndex = i
                completedCount = i
                progress.snapTo(0f)

                if (stroke.isDot || stroke.length <= 0f) {
                    progress.animateTo(
                        1f,
                        tween(
                            durationMillis = 260,
                            easing = CubicBezierEasing(0.18f, 0.80f, 0.30f, 1.0f)
                        )
                    )
                } else {
                    var previousFraction = 0f

                    for (frame in stroke.keyFrames.drop(1)) {
                        val targetFraction = frame.fraction.coerceIn(previousFraction, 1f)

                        if (targetFraction <= previousFraction + 0.002f) {
                            if (frame.pauseMs > 0) {
                                delay(frame.pauseMs.toLong())
                            }
                            previousFraction = targetFraction.coerceAtLeast(previousFraction)
                            continue
                        }

                        val segmentLength = (targetFraction - previousFraction) * stroke.length

                        val duration = (
                            segmentLength / (canvasSizePx * 0.00155f)
                        ).toInt().coerceIn(45, 430)

                        val easing = when {
                            previousFraction == 0f -> CubicBezierEasing(
                                0.25f,
                                0.10f,
                                0.25f,
                                1.0f
                            )

                            targetFraction >= 0.998f -> CubicBezierEasing(
                                0.42f,
                                0.0f,
                                0.58f,
                                1.0f
                            )

                            else -> LinearEasing
                        }

                        progress.animateTo(
                            targetFraction,
                            tween(
                                durationMillis = duration,
                                easing = easing
                            )
                        )

                        previousFraction = targetFraction

                        if (frame.pauseMs > 0) {
                            delay(frame.pauseMs.toLong())
                        }
                    }
                }

                // Complete this stroke immediately.
                // This prevents tiny tips from remaining incomplete during the pause.
                completedCount = i + 1

                delay(if (stroke.isDot) 80 else 110)
            }

            if (!loop) {
                onComplete()
                break
            }

            completedCount = 0
            activeIndex = 0
            progress.snapTo(0f)

            delay(650)
        }
    }

    val partial = remember { AndroidPath() }
    val strokeFill = remember { AndroidPath() }
    val clipAndroid = remember { AndroidPath() }
    val cap = remember { AndroidPath() }
    val posTan = remember { AndroidPathMeasure.PosTan() }

    val maskPaint = remember {
        Paint().apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
    }

    val inkColor = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val fallbackStrokeWidth = canvasSizePx * 0.085f

    Canvas(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // Soft full-character guide underneath.
        if (showGuide) {
            for (stroke in prepared) {
                val svg = stroke.svgPath

                if (svg != null) {
                    drawPath(svg, guideColor, style = Fill)
                } else {
                    drawPath(
                        stroke.fallbackPath,
                        guideColor,
                        style = Stroke(
                            width = fallbackStrokeWidth * 0.75f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }

        // Fully completed strokes.
        val completed = completedCount.coerceIn(0, prepared.size)

        for (i in 0 until completed) {
            val stroke = prepared[i]
            val svg = stroke.svgPath

            if (svg != null) {
                drawPath(svg, inkColor, style = Fill)
            } else {
                drawPath(
                    stroke.fallbackPath,
                    inkColor,
                    style = Stroke(
                        width = fallbackStrokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // Currently animating stroke.
        if (activeIndex < prepared.size && completedCount == activeIndex) {
            val stroke = prepared[activeIndex]
            val t = progress.value.coerceIn(0f, 1f)
            val svg = stroke.svgPath

            if (svg == null) {
                // Fallback if SVG data is missing.
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
                            width = fallbackStrokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            } else if (stroke.isDot) {
                if (t >= 0.92f) {
                    drawPath(svg, inkColor, style = Fill)
                } else {
                    val radius = stroke.maskWidth * (0.20f + 1.15f * t)

                    clipAndroid.reset()
                    clipAndroid.fillType = AndroidPath.FillType.WINDING
                    clipAndroid.addCircle(
                        stroke.startX,
                        stroke.startY,
                        radius,
                        AndroidPath.Direction.CW
                    )

                    clipPath(clipAndroid.asComposePath()) {
                        drawPath(svg, inkColor, style = Fill)
                    }
                }
            } else {
                val baseDistance = stroke.length * t

                val tail = if (t > 0.72f) {
                    ((t - 0.72f) / 0.28f) * stroke.maskWidth * 0.55f
                } else {
                    0f
                }

                val distance = (baseDistance + tail).coerceIn(0f, stroke.length)

                // Force clean completion near the end.
                if (t >= 0.95f || distance >= stroke.length - 0.75f) {
                    drawPath(svg, inkColor, style = Fill)
                } else {
                    partial.reset()

                    val ok = stroke.measure.getSegment(
                        0f,
                        distance,
                        partial,
                        true
                    )

                    if (!ok) {
                        drawPath(svg, inkColor, style = Fill)
                    } else {
                        maskPaint.strokeWidth = stroke.maskWidth

                        strokeFill.reset()
                        maskPaint.getFillPath(partial, strokeFill)

                        clipAndroid.reset()
                        clipAndroid.fillType = AndroidPath.FillType.WINDING
                        clipAndroid.addPath(strokeFill)

                        // Start cap.
                        cap.reset()
                        cap.fillType = AndroidPath.FillType.WINDING
                        cap.addCircle(
                            stroke.startX,
                            stroke.startY,
                            stroke.maskWidth * 0.70f,
                            AndroidPath.Direction.CW
                        )
                        clipAndroid.addPath(cap)

                        // Corner coverage for sharp vertices like 幺.
                        for (idx in stroke.points.indices) {
                            val boost = stroke.cornerBoost[idx]

                            if (boost > 0f && stroke.cumulative[idx] <= distance + 2f) {
                                cap.reset()
                                cap.fillType = AndroidPath.FillType.WINDING
                                cap.addCircle(
                                    stroke.points[idx].x,
                                    stroke.points[idx].y,
                                    boost,
                                    AndroidPath.Direction.CW
                                )
                                clipAndroid.addPath(cap)
                            }
                        }

                        // Moving end cap with extra tip boost.
                        if (stroke.measure.getPosTan(distance, posTan)) {
                            val tipBoost = if (t > 0.62f) {
                                ((t - 0.62f) / 0.38f) * stroke.maskWidth * 0.78f
                            } else {
                                0f
                            }

                            cap.reset()
                            cap.fillType = AndroidPath.FillType.WINDING
                            cap.addCircle(
                                posTan.x,
                                posTan.y,
                                stroke.maskWidth * 0.72f + tipBoost,
                                AndroidPath.Direction.CW
                            )
                            clipAndroid.addPath(cap)
                        }

                        clipPath(clipAndroid.asComposePath()) {
                            drawPath(svg, inkColor, style = Fill)
                        }
                    }
                }
            }
        }
    }
}
