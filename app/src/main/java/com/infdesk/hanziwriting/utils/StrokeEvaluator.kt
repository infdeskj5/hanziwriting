package com.infdesk.hanziwriting.utils

import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

object StrokeEvaluator {
    // Both point lists are in canvas pixel coordinates.
    fun evaluateStroke(userPoints: List<Offset>, expectedPoints: List<Offset>, threshold: Float): Int {
        if (userPoints.size < 2 || expectedPoints.isEmpty()) return userPoints.size
        var errorCount = 0
        for (u in userPoints) {
            var minDist = Float.MAX_VALUE
            for (e in expectedPoints) {
                val dx = u.x - e.x
                val dy = u.y - e.y
                val d = sqrt(dx * dx + dy * dy)
                if (d < minDist) minDist = d
            }
            if (minDist > threshold) errorCount++
        }
        return errorCount
    }
}
