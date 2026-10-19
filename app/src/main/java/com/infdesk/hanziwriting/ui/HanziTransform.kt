package com.infdesk.hanziwriting.utils

import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset

object HanziTransform {
    const val GRID_WIDTH = 1024f
    const val GRID_HEIGHT = 900f

    // makemeahanzi uses a bottom-left origin (Y up); Android Canvas uses Y down.
    // Both axes are flipped here to correct the upside-down + mirrored rendering.
    fun toCanvasX(makX: Float, scale: Float): Float = (GRID_WIDTH - makX) * scale
    fun toCanvasY(makY: Float, scale: Float): Float = (GRID_HEIGHT - makY) * scale

    fun toCanvas(point: List<Double>, scale: Float): Offset =
        Offset(toCanvasX(point[0].toFloat(), scale), toCanvasY(point[1].toFloat(), scale))

    // Matrix version used to transform the SVG stroke shapes
    fun matrix(scale: Float): Matrix {
        val m = Matrix()
        m.setScale(-scale, -scale)
        m.postTranslate(GRID_WIDTH * scale, GRID_HEIGHT * scale)
        return m
    }
}
