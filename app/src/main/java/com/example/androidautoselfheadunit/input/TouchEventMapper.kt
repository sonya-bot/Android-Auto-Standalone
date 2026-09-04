package com.example.androidautoselfheadunit.input

class TouchEventMapper(
    private val projectionWidth: Int,
    private val projectionHeight: Int,
    private var viewportWidth: Int = 0,
    private var viewportHeight: Int = 0,
    private var surfaceLeft: Int = 0,
    private var surfaceTop: Int = 0,
) {
    fun updateGeometry(
        viewportWidth: Int,
        viewportHeight: Int,
        surfaceLeft: Int = 0,
        surfaceTop: Int = 0,
    ) {
        this.viewportWidth = viewportWidth
        this.viewportHeight = viewportHeight
        this.surfaceLeft = surfaceLeft
        this.surfaceTop = surfaceTop
    }

    fun updateScreenSize(
        width: Int,
        height: Int,
    ) {
        updateGeometry(width, height, 0, 0)
    }

    fun mapX(x: Float): Int {
        if (viewportWidth <= 0) return 0
        val viewportX = x + surfaceLeft
        val ratio = projectionWidth.toFloat() / viewportWidth
        return (viewportX * ratio).toInt().coerceIn(0, (projectionWidth - 1).coerceAtLeast(0))
    }

    fun mapY(y: Float): Int {
        if (viewportHeight <= 0) return 0
        val viewportY = y + surfaceTop
        val ratio = projectionHeight.toFloat() / viewportHeight
        return (viewportY * ratio).toInt().coerceIn(0, (projectionHeight - 1).coerceAtLeast(0))
    }
}
