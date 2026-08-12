package com.psami.visiondisplay

import com.psami.visiondisplay.data.CalibrationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CalibrationStateTest {
    @Test
    fun normalized_clampsValuesToSupportedRanges() {
        val normalized = CalibrationState(
            compressionScale = -2f,
            offsetX = 8f,
            offsetY = -4f,
            isEdgeEnhancementEnabled = true
        ).normalized()

        assertEquals(CalibrationState.MIN_COMPRESSION, normalized.compressionScale, 0f)
        assertEquals(CalibrationState.MAX_OFFSET, normalized.offsetX, 0f)
        assertEquals(CalibrationState.MIN_OFFSET, normalized.offsetY, 0f)
        assertEquals(true, normalized.isEdgeEnhancementEnabled)
    }

    @Test
    fun normalized_replacesNonFiniteValuesWithSafeDefaults() {
        val normalized = CalibrationState(
            compressionScale = Float.NaN,
            offsetX = Float.POSITIVE_INFINITY,
            offsetY = Float.NEGATIVE_INFINITY
        ).normalized()

        assertFalse(normalized.compressionScale.isNaN())
        assertEquals(CalibrationState.MAX_COMPRESSION, normalized.compressionScale, 0f)
        assertEquals(0f, normalized.offsetX, 0f)
        assertEquals(0f, normalized.offsetY, 0f)
    }
}
