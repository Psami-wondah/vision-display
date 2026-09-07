package com.psami.visiondisplay.data

data class ControllerPreferences(
    val leftHandedMode: Boolean = false,
    val pointerSensitivity: Float = 1.8f,
    val viewportSensitivity: Float = 1.0f,
    val spokenFeedbackEnabled: Boolean = true
) {
    fun normalized(): ControllerPreferences =
        copy(
            pointerSensitivity =
                pointerSensitivity.coerceIn(
                    MIN_POINTER_SENSITIVITY,
                    MAX_POINTER_SENSITIVITY
                ),

            viewportSensitivity =
                viewportSensitivity.coerceIn(
                    MIN_VIEWPORT_SENSITIVITY,
                    MAX_VIEWPORT_SENSITIVITY
                )
        )

    companion object {
        const val MIN_POINTER_SENSITIVITY = 0.5f
        const val MAX_POINTER_SENSITIVITY = 3f

        const val MIN_VIEWPORT_SENSITIVITY = 0.5f
        const val MAX_VIEWPORT_SENSITIVITY = 2f
    }
}