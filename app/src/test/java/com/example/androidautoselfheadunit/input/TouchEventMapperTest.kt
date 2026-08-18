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
        assertEquals(800, mapper.mapX(1600f))
    }

    @Test
    fun `mapY returns correct proportion`() {
        val mapper = TouchEventMapper(projectionWidth = 800, projectionHeight = 480)
        mapper.updateScreenSize(1600, 960)

        assertEquals(240, mapper.mapY(480f))
        assertEquals(0, mapper.mapY(0f))
        assertEquals(480, mapper.mapY(960f))
    }

    @Test
    fun `mapX and mapY clamp to projection size`() {
        val mapper = TouchEventMapper(projectionWidth = 800, projectionHeight = 480)
        mapper.updateScreenSize(1600, 960)

        assertEquals(800, mapper.mapX(2000f))
        assertEquals(0, mapper.mapX(-10f))
        assertEquals(480, mapper.mapY(1000f))
        assertEquals(0, mapper.mapY(-10f))
    }
}
