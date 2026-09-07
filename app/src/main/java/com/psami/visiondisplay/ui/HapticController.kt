package com.psami.visiondisplay.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticController(
    context: Context
) {

    private val vibrator: Vibrator =
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            context
                .getSystemService(
                    VibratorManager::class.java
                )
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(
                Vibrator::class.java
            )
        }

    fun click() {
        vibrate(
            longArrayOf(
                0,
                25
            )
        )
    }

    fun confirm() {
        vibrate(
            longArrayOf(
                0,
                35,
                45,
                35
            )
        )
    }

    fun reset() {
        vibrate(
            longArrayOf(
                0,
                70
            )
        )
    }

    private fun vibrate(
        pattern: LongArray
    ) {
        if (
            !vibrator.hasVibrator()
        ) {
            return
        }

        vibrator.vibrate(
            VibrationEffect.createWaveform(
                pattern,
                -1
            )
        )
    }
}