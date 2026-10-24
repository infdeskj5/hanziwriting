package com.infdesk.hanziwriting.ui

import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure as AndroidPathMeasure
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
                    progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
                    delay(200)
                }
                if (!loop) { onComplete(); break }
                delay(900)
            }
        }
    }

    val strokeColor = MaterialTheme.colorScheme.primary
    val ghostColor = strokeColor.copy(alpha = 0.12f)
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    // Reusable native objects (no per-frame allocation)
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
            brushPaint.strokeWidth = size.width * 0.35f

            for (i in prepared.indices) {
                val st = prepared[i]
                when {
                    // Completed strokes: full calligraphic shape
                    i < currentStroke -> drawPath(st.svgPath, strokeColor)

                    // Animating stroke: ghost preview + progressive ink
                    i == currentStroke -> {
                        drawPath(st.svgPath, ghostColor)

                        // 1. Cut the median line at the current progress point
                        pm.setPath(st.medianAndroid, false)
                        segPath.reset()
                        pm.getSegment(0f, st.medianLength * progress.value, segPath, true)

                        // 2. Convert the brushed line into a filled "tube" outline
                        tubePath.reset()
                        brushPaint.getFillPath(segPath, tubePath)

                        // 3. Intersect tube with the calligraphic shape = the inked part
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
