package com.example.androidautoselfheadunit.aap.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionDisplayConfigTest {
    @Test
    fun `wide viewport is represented by symmetric vertical margins`() {
        val profile = ProjectionDisplayConfig.forViewport(2560, 1096)

        assertEquals(0, profile.marginWidthPx)
        assertEquals(258, profile.marginHeightPx)
        assertEquals(1920, profile.contentWidthPx)
        assertEquals(822, profile.contentHeightPx)
        assertTrue(profile.widthDp >= 1280f)
    }

    @Test
    fun `sixteen by nine viewport needs no margins`() {
        val profile = ProjectionDisplayConfig.forViewport(1920, 1080)

        assertEquals(0, profile.marginWidthPx)
        assertEquals(0, profile.marginHeightPx)
        assertEquals(10000, profile.pixelAspectRatioE4)
        assertEquals(
            16f / 9f,
            profile.widthPx.toFloat() / profile.heightPx,
            0.0001f,
        )
    }

    @Test
    fun `tall viewport is represented by symmetric horizontal margins`() {
        val profile = ProjectionDisplayConfig.forViewport(1000, 1000)

        assertEquals(840, profile.marginWidthPx)
        assertEquals(0, profile.marginHeightPx)
        assertEquals(1080, profile.contentWidthPx)
        assertEquals(1080, profile.contentHeightPx)
    }
}
