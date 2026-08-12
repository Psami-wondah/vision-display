package com.psami.visiondisplay.data

import android.content.Context
import androidx.core.content.edit

object CalibrationStore {
    private const val PREFERENCES_NAME = "vision_display_calibration"
    private const val KEY_COMPRESSION = "compression_scale"
    private const val KEY_OFFSET_X = "offset_x"
    private const val KEY_OFFSET_Y = "offset_y"
    private const val KEY_EDGE_ENHANCEMENT = "edge_enhancement"

    fun load(context: Context): CalibrationState {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return CalibrationState(
            compressionScale = preferences.getFloat(
                KEY_COMPRESSION,
                CalibrationState.MAX_COMPRESSION
            ),
            offsetX = preferences.getFloat(KEY_OFFSET_X, 0f),
            offsetY = preferences.getFloat(KEY_OFFSET_Y, 0f),
            isEdgeEnhancementEnabled = preferences.getBoolean(KEY_EDGE_ENHANCEMENT, false)
        ).normalized()
    }

    fun save(context: Context, state: CalibrationState) {
        val normalizedState = state.normalized()
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
            putFloat(KEY_COMPRESSION, normalizedState.compressionScale)
            putFloat(KEY_OFFSET_X, normalizedState.offsetX)
            putFloat(KEY_OFFSET_Y, normalizedState.offsetY)
            putBoolean(KEY_EDGE_ENHANCEMENT, normalizedState.isEdgeEnhancementEnabled)
        }
    }
}
