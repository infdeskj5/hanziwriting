package com.infdesk.hanziwriting.utils

import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset

object HanziTransform {
    const val GRID_WIDTH = 1024f
    const val GRID_HEIGHT = 900f

    // FIX: X is normal (no flip), Y is flipped (origin at bottom in makemeahanzi)
    fun toCanvasX(makX: Float, scale: Float): Float = makX * scale
    fun toCanvasY(makY: Float, scale: Float): Float = (GRID_HEIGHT - makY) * scale

    fun toCanvas(point: List<Double>, scale: Float): Offset =
        Offset(toCanvasX(point[0].toFloat(), scale), toCanvasY(point[1].toFloat(), scale))

    fun matrix(scale: Float): Matrix {
        val m = Matrix()
        m.setScale(scale, -scale) // X normal, Y flipped
        m.postTranslate(0f, GRID_HEIGHT * scale)
        return m
    }
}
