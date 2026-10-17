package com.infdesk.hanziwriting.utils

import androidx.compose.ui.geometry.Offset
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.pow
import kotlin.math.sqrt

object StrokeEvaluator {
    private val gson = Gson()

    /**
     * Evaluates a single drawn stroke against the expected median points.
     * @param userPoints Points captured from the Canvas drag gesture.
     * @param expectedMedianJson The "medians" array for this specific stroke from graphics.txt.
     * @param canvasSize The size of the canvas (e.g., 1000f) to normalize coordinates.
     * @param threshold Max allowed distance (in normalized units) to be considered "correct".
     * @return Number of error points (points that deviated too far from the median).
     */
    fun evaluateStroke(
        userPoints: List<Offset>,
        expectedMedianJson: String,
        canvasSize: Float = 1000f,
        threshold: Float = 80f
    ): Int {
        if (userPoints.size < 2) return userPoints.size // Not enough points to evaluate

        // Parse the expected median for this stroke: List<List<Double>> representing [x, y]
        val type = object : TypeToken<List<List<Double>>>() {}.type
        val expectedPoints: List<List<Double>> = gson.fromJson(expectedMedianJson, type)

        if (expectedPoints.isEmpty()) return userPoints.size

        var errorCount = 0

        // Normalize user points to 0-1000 scale based on canvas size
        val normalizedUserPoints = userPoints.map { 
            Offset((it.x / canvasSize) * 1000f, (it.y / canvasSize) * 1000f) 
        }

        // For each user point, find the distance to the closest expected point
        for (uPoint in normalizedUserPoints) {
            var minDistance = Float.MAX_VALUE
            
            for (ePoint in expectedPoints) {
                val dx = uPoint.x - ePoint[0].toFloat()
                val dy = uPoint.y - ePoint[1].toFloat()
                val distance = sqrt(dx.pow(2) + dy.pow(2))
                if (distance < minDistance) {
                    minDistance = distance
                }
            }

            // If the closest expected point is still too far, it's an error
            if (minDistance > threshold) {
                errorCount++
            }
        }

        return errorCount
    }
}
