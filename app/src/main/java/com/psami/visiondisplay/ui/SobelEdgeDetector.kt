package com.psami.visiondisplay.ui

import kotlin.math.abs

internal object SobelEdgeDetector {
    private const val EDGE_COLOR = 0xE6FFFFFF.toInt()
    private const val TRANSPARENT = 0x00000000

    fun detect(
        luminance: IntArray,
        width: Int,
        height: Int,
        threshold: Int = 180
    ): IntArray {
        require(width > 0 && height > 0)
        require(luminance.size == width * height)

        val result = IntArray(luminance.size)
        if (width < 3 || height < 3) return result

        for (y in 1 until height - 1) {
            val previousRow = (y - 1) * width
            val currentRow = y * width
            val nextRow = (y + 1) * width

            for (x in 1 until width - 1) {
                val gradientX =
                    -luminance[previousRow + x - 1] + luminance[previousRow + x + 1] +
                        -2 * luminance[currentRow + x - 1] + 2 * luminance[currentRow + x + 1] +
                        -luminance[nextRow + x - 1] + luminance[nextRow + x + 1]
                val gradientY =
                    -luminance[previousRow + x - 1] - 2 * luminance[previousRow + x] -
                        luminance[previousRow + x + 1] + luminance[nextRow + x - 1] +
                        2 * luminance[nextRow + x] + luminance[nextRow + x + 1]

                result[currentRow + x] = if (abs(gradientX) + abs(gradientY) >= threshold) {
                    EDGE_COLOR
                } else {
                    TRANSPARENT
                }
            }
        }

        return result
    }
}
