package com.psami.visiondisplay.data

import android.content.Context
import androidx.core.content.edit

object ControllerPreferencesStore {

    private const val PREFERENCES_NAME =
        "vision_display_controller"

    private const val KEY_LEFT_HANDED =
        "left_handed"

    private const val KEY_POINTER_SENSITIVITY =
        "pointer_sensitivity"

    private const val KEY_VIEWPORT_SENSITIVITY =
        "viewport_sensitivity"


    private const val KEY_SPOKEN_FEEDBACK =
        "spoken_feedback"

    fun load(
        context: Context
    ): ControllerPreferences {

        val preferences =
            context.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )

        return ControllerPreferences(
            leftHandedMode =
                preferences.getBoolean(
                    KEY_LEFT_HANDED,
                    false
                ),

            pointerSensitivity =
                preferences.getFloat(
                    KEY_POINTER_SENSITIVITY,
                    1.8f
                ),

            viewportSensitivity =
                preferences.getFloat(
                    KEY_VIEWPORT_SENSITIVITY,
                    1f
                ),
            spokenFeedbackEnabled =
                preferences.getBoolean(
                    KEY_SPOKEN_FEEDBACK,
                    true
                )
        ).normalized()
    }

    fun save(
        context: Context,
        state: ControllerPreferences
    ) {

        val normalized =
            state.normalized()

        context
            .getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
            .edit {

                putBoolean(
                    KEY_LEFT_HANDED,
                    normalized.leftHandedMode
                )

                putFloat(
                    KEY_POINTER_SENSITIVITY,
                    normalized.pointerSensitivity
                )

                putFloat(
                    KEY_VIEWPORT_SENSITIVITY,
                    normalized.viewportSensitivity
                )

                putBoolean(
                    KEY_SPOKEN_FEEDBACK,
                    normalized.spokenFeedbackEnabled
                )
            }
    }
}