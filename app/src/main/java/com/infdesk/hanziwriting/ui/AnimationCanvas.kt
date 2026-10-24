package com.infdesk.hanziwriting.ui

import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure as AndroidPathMeasure
import android.graphics.RectF
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
import kotlin.math.max
import kotlin.math.min

private data class PreparedStroke(
    val svgPath: Path,
    val medianAndroid: AndroidPath,
    val medianLength: Float,
    val durationMs: Int
)

// ===== SPEED KNOBS =====
private const val MS_PER_GRID_UNIT = 1.6f
private const val MIN_STROKE_MS = 600
private const val MAX_STROKE_MS = 1800
private const val PAUSE_BETWEEN_STROKES_MS = 250L
private const val PAUSE_BEFORE_LOOP_MS = 1000L
// =======================

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

                    val gridLength = pm.length / scale
                    val dur = (gridLength * MS_PER_GRID_UNIT).toInt().coerceIn(MIN_STROKE_MS, MAX_STROKE_MS)

                    PreparedStroke(svgPath, medianAndroid, pm.length, dur)
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
                        animationSpec = tween(durationMillis = prepared[i].durationMs, easing = LinearEasing)
                    )
                    delay(PAUSE_BETWEEN_STROKES_MS)
                }
                if (!loop) { onComplete(); break }
                delay(PAUSE_BEFORE_LOOP_MS)
            }
        }
    }

    val strokeColor = MaterialTheme.colorScheme.primary
    val ghostColor = strokeColor.copy(alpha = 0.12f)
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    // Reusable native objects
    val brushPaint = remember {
        AndroidPaint().apply {
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.BUTT   // FIX: flat start cap -> zero ink at progress 0
            strokeJoin = AndroidPaint.Join.ROUND
        }
    }
    val segPath = remember { AndroidPath() }
    val tubePath = remember { AndroidPath() }
    val dotPath = remember { AndroidPath() }
    val rectF = remember { RectF() }
    val posTan = remember { FloatArray(2) }
    val pm = remember { AndroidPathMeasure() }

    Box(
        modifier = modifier
            .onSizeChanged { sizePx = it.width.toFloat() }
            .background(surfaceColor)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val brushWidth = size.width * 0.35f
            val brushRadius = brushWidth / 2f
            brushPaint.strokeWidth = brushWidth

            for (i in prepared.indices) {
                val st = prepared[i]
                when {
                    i < currentStroke -> drawPath(st.svgPath, strokeColor)

                    i == currentStroke -> {
                        val p = progress.value
                        drawPath(st.svgPath, ghostColor)
                        if (p > 0f) {
                            pm.setPath(st.medianAndroid, false)

                            // 1. Body: flat-capped tube from start to current point.
                            //    At p→0 its area → 0, so the stroke truly starts empty.
                            segPath.reset()
                            pm.getSegment(0f, st.medianLength * p, segPath, true)
                            tubePath.reset()
                            brushPaint.getFillPath(segPath, tubePath)
                            drawPath(
                                Path.combine(PathOperation.Intersect, st.svgPath, tubePath.asComposePath()),
                                strokeColor
                            )

                            // 2. Leading brush tip: a dot whose radius ramps up from 0,
                            //    so ink grows from a point instead of a pre-drawn blob.
                            pm.getPosTan(st.medianLength * p, posTan, null)
                            val tipR = brushRadius * min(1f, p * 5f)
                            if (tipR > 0.5f) {
                                dotPath.reset()
                                rectF.set(posTan[0] - tipR, posTan[1] - tipR, posTan[0] + tipR, posTan[1] + tipR)
                                dotPath.addOval(rectF, AndroidPath.Direction.CW)
                                drawPath(
                                    Path.combine(PathOperation.Intersect, st.svgPath, dotPath.asComposePath()),
                                    strokeColor
                                )
                            }

                            // 3. Trailing dot near the end: smoothly reveals the stroke's
                            //    rounded start tip so nothing pops when the stroke completes.
                            val tailR = brushRadius * max(0f, (p - 0.75f) / 0.25f)
                            if (tailR > 0.5f) {
                                pm.getPosTan(0f, posTan, null)
                                dotPath.reset()
                                rectF.set(posTan[0] - tailR, posTan[1] - tailR, posTan[0] + tailR, posTan[1] + tailR)
                                dotPath.addOval(rectF, AndroidPath.Direction.CW)
                                drawPath(
                                    Path.combine(PathOperation.Intersect, st.svgPath, dotPath.asComposePath()),
                                    strokeColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
