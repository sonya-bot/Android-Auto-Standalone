package com.example.androidautoselfheadunit.input

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchEventMapperTest {
    @Test
    fun `mapX returns correct proportion`() {
        val mapper = TouchEventMapper(projectionWidth = 800, projectionHeight = 480)
        mapper.updateScreenSize(1600, 960)

        assertEquals(400, mapper.mapX(800f))
        assertEquals(0, mapper.mapX(0f))
        assertEquals(799, mapper.mapX(1600f))
    }

    @Test
    fun `mapY returns correct proportion`() {
        val mapper = TouchEventMapper(projectionWidth = 800, projectionHeight = 480)
        mapper.updateScreenSize(1600, 960)

        assertEquals(240, mapper.mapY(480f))
        assertEquals(0, mapper.mapY(0f))
        assertEquals(479, mapper.mapY(960f))
    }

    @Test
    fun `mapX and mapY clamp to projection size`() {
        val mapper = TouchEventMapper(projectionWidth = 800, projectionHeight = 480)
        mapper.updateScreenSize(1600, 960)

        assertEquals(799, mapper.mapX(2000f))
        assertEquals(0, mapper.mapX(-10f))
        assertEquals(479, mapper.mapY(1000f))
        assertEquals(0, mapper.mapY(-10f))
    }

    @Test
    fun `updateGeometry handles cropped letterboxing vertically`() {
        // 2560x1096 viewport, 1920x822 active projection content
        // SurfaceView is 2560x1440 centered, so surfaceTop = -172
        val mapper = TouchEventMapper(projectionWidth = 1920, projectionHeight = 822)
        mapper.updateGeometry(
            viewportWidth = 2560,
            viewportHeight = 1096,
            surfaceLeft = 0,
            surfaceTop = -172,
        )

        // Top of visible screen on SurfaceView is surfaceY = 172 (viewportY = 0)
        assertEquals(0, mapper.mapY(172f))

        // Center of visible screen on SurfaceView is surfaceY = 172 + 548 = 720 (viewportY = 548)
        assertEquals(411, mapper.mapY(720f))

        // Bottom of visible screen on SurfaceView is surfaceY = 172 + 1096 = 1268 (viewportY = 1096)
        assertEquals(821, mapper.mapY(1268f))

        // Left and right edges of visible screen
        assertEquals(0, mapper.mapX(0f))
        assertEquals(1919, mapper.mapX(2560f))
    }

    @Test
    fun `updateGeometry handles cropped pillarboxing horizontally`() {
        // 1440x1080 viewport, 1440x1080 active projection content
        // SurfaceView is 1920x1080 centered, so surfaceLeft = -240
        val mapper = TouchEventMapper(projectionWidth = 1440, projectionHeight = 1080)
        mapper.updateGeometry(
            viewportWidth = 1440,
            viewportHeight = 1080,
            surfaceLeft = -240,
            surfaceTop = 0,
        )

        // Left edge of visible screen on SurfaceView is surfaceX = 240 (viewportX = 0)
        assertEquals(0, mapper.mapX(240f))

        // Right edge of visible screen on SurfaceView is surfaceX = 240 + 1440 = 1680 (viewportX = 1440)
        assertEquals(1439, mapper.mapX(1680f))
    }
}
