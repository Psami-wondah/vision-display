package com.psami.visiondisplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun EyesFreeControlPanel(
    isExternalDisplayConnected: Boolean,
    onCursorMove: (
        deltaX: Float,
        deltaY: Float
    ) -> Unit,
    onCursorClick: () -> Unit,
    onViewportPan: (
        deltaX: Float,
        deltaY: Float
    ) -> Unit,
    onViewportScale: (
        scaleFactor: Float
    ) -> Unit,
    onViewportReset: () -> Unit,
    onOpenSettings: () -> Unit,
    leftHandedMode: Boolean,
    modifier: Modifier = Modifier
) {



    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing
                )
                .padding(12.dp)
    ) {

        /*
         * Small status/header.
         *
         * Most of the display is deliberately
         * reserved for the touchpads.
         */
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text =
                    if (
                        isExternalDisplayConnected
                    ) {
                        "● Glasses connected"
                    } else {
                        "○ Waiting for glasses"
                    },

                style =
                    MaterialTheme
                        .typography
                        .titleMedium,

                modifier =
                    Modifier.weight(1f)
            )

            OutlinedButton(
                onClick =
                    onOpenSettings
            ) {
                Text("Settings")
            }
        }

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        /*
         * The two physical halves of the phone
         * always have the same purpose.
         *
         * This is intentional for muscle memory.
         */
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),

            horizontalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {

            if (
                leftHandedMode
            ) {

                ViewportControlPad(
                    onViewportPan =
                        onViewportPan,

                    onViewportScale =
                        onViewportScale,

                    onViewportReset =
                        onViewportReset,

                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                )

                PointerControlPad(
                    onCursorMove =
                        onCursorMove,

                    onCursorClick =
                        onCursorClick,

                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                )

            } else {

                PointerControlPad(
                    onCursorMove =
                        onCursorMove,

                    onCursorClick =
                        onCursorClick,

                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                )

                ViewportControlPad(
                    onViewportPan =
                        onViewportPan,

                    onViewportScale =
                        onViewportScale,

                    onViewportReset =
                        onViewportReset,

                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun PointerControlPad(
    onCursorMove: (
        deltaX: Float,
        deltaY: Float
    ) -> Unit,
    onCursorClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier =
            modifier,

        shape =
            RoundedCornerShape(
                20.dp
            ),

        tonalElevation =
            2.dp
    ) {

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant
                    )
                    .pointerInput(Unit) {

                        awaitEachGesture {

                            val down =
                                awaitFirstDown(
                                    requireUnconsumed =
                                        false
                                )

                            var previousPosition =
                                down.position

                            var totalMovement =
                                Offset.Zero

                            var isDragging =
                                false

                            while (true) {

                                val event =
                                    awaitPointerEvent()

                                val change =
                                    event.changes
                                        .firstOrNull {
                                            it.id ==
                                                    down.id
                                        }
                                        ?: break

                                if (
                                    !change.pressed
                                ) {

                                    if (
                                        !isDragging
                                    ) {
                                        onCursorClick()
                                    }

                                    break
                                }

                                val movement =
                                    change.position -
                                            previousPosition

                                totalMovement +=
                                    movement

                                if (
                                    !isDragging &&
                                    totalMovement
                                        .getDistance() >
                                    viewConfiguration
                                        .touchSlop
                                ) {
                                    isDragging =
                                        true
                                }

                                if (
                                    isDragging
                                ) {

                                    onCursorMove(
                                        movement.x,
                                        movement.y
                                    )

                                    change.consume()
                                }

                                previousPosition =
                                    change.position
                            }
                        }
                    }
        ) {

            Column(
                modifier =
                    Modifier.align(
                        Alignment.Center
                    ),

                horizontalAlignment =
                    Alignment.CenterHorizontally,

                verticalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {

                Text(
                    text = "POINTER",
                    style =
                        MaterialTheme
                            .typography
                            .headlineMedium
                )

                Text(
                    text =
                        "Drag to move",

                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge
                )

                Text(
                    text =
                        "Tap to select",

                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge
                )
            }
        }
    }
}

@Composable
private fun ViewportControlPad(
    onViewportPan: (
        deltaX: Float,
        deltaY: Float
    ) -> Unit,
    onViewportScale: (
        scaleFactor: Float
    ) -> Unit,
    onViewportReset: () -> Unit,
    modifier: Modifier = Modifier
) {

    Surface(
        modifier =
            modifier,

        shape =
            RoundedCornerShape(
                20.dp
            ),

        tonalElevation =
            2.dp
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant
                    )
        ) {

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    onViewportReset()
                                }
                            )
                        }
                        .pointerInput(Unit) {

                            detectTransformGestures(
                                panZoomLock =
                                    true
                            ) { _,
                                pan,
                                zoom,
                                _ ->

                                if (
                                    size.width > 0 &&
                                    size.height > 0
                                ) {

                                    onViewportPan(
                                        (
                                                pan.x /
                                                        size.width
                                                ) *
                                                2f,

                                        (
                                                pan.y /
                                                        size.height
                                                ) *
                                                2f
                                    )
                                }

                                if (
                                    zoom.isFinite() &&
                                    zoom > 0f &&
                                    zoom != 1f
                                ) {

                                    onViewportScale(
                                        zoom
                                    )
                                }
                            }
                        }
            ) {

                Column(
                    modifier =
                        Modifier.align(
                            Alignment.Center
                        ),

                    horizontalAlignment =
                        Alignment.CenterHorizontally,

                    verticalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {

                    Text(
                        text = "VIEWPORT",
                        style =
                            MaterialTheme
                                .typography
                                .headlineMedium
                    )

                    Text(
                        text =
                            "Drag to move",

                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge
                    )

                    Text(
                        text =
                            "Pinch to resize",

                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge
                    )
                }
            }

            Button(
                onClick =
                    onViewportReset,

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .height(60.dp)
            ) {

                Text(
                    text =
                        "RESET VIEWPORT",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )
            }
        }
    }
}