package com.example.androidautoselfheadunit.input

class TouchEventMapper(
    private var screenWidth: Int = 0,
    private var screenHeight: Int = 0,
    private val projectionWidth: Int = PROJECTION_WIDTH,
    private val projectionHeight: Int = PROJECTION_HEIGHT,
) {
    companion object {
        private const val PROJECTION_WIDTH = 800
        private const val PROJECTION_HEIGHT = 480
    }

    fun updateScreenSize(
        width: Int,
        height: Int,
    ) {
        screenWidth = width
        screenHeight = height
    }

    fun mapX(x: Float): Int {
        if (screenWidth == 0) return 0
        val ratio = projectionWidth.toFloat() / screenWidth
        return (x * ratio).toInt().coerceIn(0, projectionWidth)
    }

    fun mapY(y: Float): Int {
        if (screenHeight == 0) return 0
        val ratio = projectionHeight.toFloat() / screenHeight
        return (y * ratio).toInt().coerceIn(0, projectionHeight)
    }
}
