package com.example.androidautoselfheadunit.aap.protocol

import kotlin.math.roundToInt

data class ProjectionDisplayProfile(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val marginWidthPx: Int,
    val marginHeightPx: Int,
    val pixelAspectRatioE4: Int,
) {
    val contentWidthPx: Int
        get() = widthPx - marginWidthPx

    val contentHeightPx: Int
        get() = heightPx - marginHeightPx

    val widthDp: Float
        get() = contentWidthPx * BASE_DENSITY_DPI / densityDpi

    private companion object {
        const val BASE_DENSITY_DPI = 160f
    }
}

/** One source of truth for the projected Android Auto canvas. */
object ProjectionDisplayConfig {
    const val WIDTH_PX = 1920
    const val HEIGHT_PX = 1080
    const val DENSITY_DPI = 280
    const val PIXEL_ASPECT_RATIO_E4 = 10000
    private const val MIN_EVEN_DIMENSION = 2
    private const val EVEN_MASK = -2

    fun forViewport(
        viewportWidthPx: Int,
        viewportHeightPx: Int,
    ): ProjectionDisplayProfile {
        if (viewportWidthPx <= 0 || viewportHeightPx <= 0) {
            return profile(marginWidthPx = 0, marginHeightPx = 0)
        }

        val viewportAspectRatio = viewportWidthPx.toDouble() / viewportHeightPx
        val videoAspectRatio = WIDTH_PX.toDouble() / HEIGHT_PX

        return if (viewportAspectRatio > videoAspectRatio) {
            val contentHeight = even((WIDTH_PX / viewportAspectRatio).roundToInt())
            profile(marginWidthPx = 0, marginHeightPx = HEIGHT_PX - contentHeight)
        } else {
            val contentWidth = even((HEIGHT_PX * viewportAspectRatio).roundToInt())
            profile(marginWidthPx = WIDTH_PX - contentWidth, marginHeightPx = 0)
        }
    }

    private fun profile(
        marginWidthPx: Int,
        marginHeightPx: Int,
    ) = ProjectionDisplayProfile(
        widthPx = WIDTH_PX,
        heightPx = HEIGHT_PX,
        densityDpi = DENSITY_DPI,
        marginWidthPx = marginWidthPx,
        marginHeightPx = marginHeightPx,
        pixelAspectRatioE4 = PIXEL_ASPECT_RATIO_E4,
    )

    private fun even(value: Int): Int = value.coerceAtLeast(MIN_EVEN_DIMENSION) and EVEN_MASK
}
