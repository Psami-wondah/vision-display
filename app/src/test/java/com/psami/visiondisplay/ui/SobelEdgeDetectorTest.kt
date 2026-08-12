package com.psami.visiondisplay.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SobelEdgeDetectorTest {
    @Test
    fun uniformImage_producesNoEdges() {
        val pixels = SobelEdgeDetector.detect(
            luminance = IntArray(25) { 128 },
            width = 5,
            height = 5
        )

        assertEquals(0, pixels.count { it != 0 })
    }

    @Test
    fun verticalContrastBoundary_producesAnOutline() {
        val width = 7
        val height = 5
        val luminance = IntArray(width * height) { index ->
            if (index % width < 3) 0 else 255
        }

        val pixels = SobelEdgeDetector.detect(luminance, width, height)

        assertNotEquals(0, pixels[2 + 2 * width])
        assertNotEquals(0, pixels[3 + 2 * width])
        assertEquals(0, pixels[5 + 2 * width])
    }
}
