package com.infdesk.hanziwriting.utils

import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

object StrokeEvaluator {
    fun evaluateStroke(userPoints: List<Offset>, expectedPoints: List<Offset>, canvasSize: Float): Boolean {
        if (userPoints.size < 3 || expectedPoints.size < 2) return false
        
        val leniency = canvasSize * 0.25f 
        
        val startMatch = userPoints.first().distanceTo(expectedPoints.first()) < leniency
        val endMatch = userPoints.last().distanceTo(expectedPoints.last()) < leniency
        
        val startMatchBackwards = userPoints.first().distanceTo(expectedPoints.last()) < leniency
        val endMatchBackwards = userPoints.last().distanceTo(expectedPoints.first()) < leniency
        
        if (!(startMatch && endMatch) && !(startMatchBackwards && endMatchBackwards)) {
            return false
        }
        
        var totalDist = 0f
        val sampleRate = maxOf(1, userPoints.size / 15)
        var sampledCount = 0
        for (i in userPoints.indices step sampleRate) {
            val u = userPoints[i]
            val minDist = expectedPoints.minOf { e -> u.distanceTo(e) }
            totalDist += minDist
            sampledCount++
        }
        val avgDist = totalDist / sampledCount
        
        return avgDist < leniency
    }
    
    private fun Offset.distanceTo(other: Offset): Float {
        val dx = this.x - other.x
        val dy = this.y - other.y
        return sqrt(dx * dx + dy * dy)
    }
}
