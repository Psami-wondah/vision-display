package com.psami.visiondisplay.ui

import com.psami.visiondisplay.data.CalibrationState
import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationTransformTest {
    @Test
    fun fullScale_fillsRootBecauseThereIsNoUnusedDisplayArea() {
        val layout = calculateCalibrationLayout(
            rootWidth = 1920,
            rootHeight = 1080,
            state = CalibrationState(compressionScale = 1f, offsetX = 1f, offsetY = -1f)
        )

        assertEquals(1920, layout.width)
        assertEquals(1080, layout.height)
        assertEquals(0, layout.leftMargin)
        assertEquals(0, layout.topMargin)
    }

    @Test
    fun compressedContent_movesToRequestedEdgesWithoutLeavingTheDisplay() {
        val layout = calculateCalibrationLayout(
            rootWidth = 1000,
            rootHeight = 600,
            state = CalibrationState(compressionScale = 0.5f, offsetX = 1f, offsetY = -1f)
        )

        assertEquals(500, layout.width)
        assertEquals(300, layout.height)
        assertEquals(500, layout.leftMargin)
        assertEquals(0, layout.topMargin)
    }

    @Test
    fun compressedContent_centresWithinTheUnusedDisplayArea() {
        val layout = calculateCalibrationLayout(
            rootWidth = 1000,
            rootHeight = 600,
            state = CalibrationState(compressionScale = 0.5f, offsetX = 0f, offsetY = 0f)
        )

        assertEquals(500, layout.width)
        assertEquals(300, layout.height)
        assertEquals(250, layout.leftMargin)
        assertEquals(150, layout.topMargin)
    }
}
