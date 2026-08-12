package com.psami.visiondisplay.data

data class CalibrationState(
    val compressionScale: Float = 1.0f, // 0.1 (10%) to 1.0 (100%)
    val offsetX: Float = 0f,            // -1.0 (left) to 1.0 (right)
    val offsetY: Float = 0f,            // -1.0 (top) to 1.0 (bottom)
    val isEdgeEnhancementEnabled: Boolean = false
) {
    fun normalized(): CalibrationState = copy(
        compressionScale = compressionScale
            .takeIf(Float::isFinite)
            ?.coerceIn(MIN_COMPRESSION, MAX_COMPRESSION)
            ?: MAX_COMPRESSION,
        offsetX = offsetX.takeIf(Float::isFinite)?.coerceIn(MIN_OFFSET, MAX_OFFSET) ?: 0f,
        offsetY = offsetY.takeIf(Float::isFinite)?.coerceIn(MIN_OFFSET, MAX_OFFSET) ?: 0f
    )

    companion object {
        const val MIN_COMPRESSION = 0.1f
        const val MAX_COMPRESSION = 1f
        const val MIN_OFFSET = -1f
        const val MAX_OFFSET = 1f
    }
}
