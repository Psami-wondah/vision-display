package com.psami.visiondisplay.ui

import com.psami.visiondisplay.data.CalibrationState
import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationTransformTest {
    @Test
    fun fullScale_hasNoTranslationBecauseThereIsNoUnusedDisplayArea() {
        val transform = calculateCalibrationTransform(
            width = 1920,
            height = 1080,
            state = CalibrationState(compressionScale = 1f, offsetX = 1f, offsetY = -1f)
        )

        assertEquals(1f, transform.scale, 0f)
        assertEquals(0f, transform.translationX, 0f)
        assertEquals(0f, transform.translationY, 0f)
    }

    @Test
    fun compressedContent_movesToRequestedEdgesWithoutLeavingTheDisplay() {
        val transform = calculateCalibrationTransform(
            width = 1000,
            height = 600,
            state = CalibrationState(compressionScale = 0.5f, offsetX = 1f, offsetY = -1f)
        )

        assertEquals(0.5f, transform.scale, 0f)
        assertEquals(250f, transform.translationX, 0f)
        assertEquals(-150f, transform.translationY, 0f)
    }
}
