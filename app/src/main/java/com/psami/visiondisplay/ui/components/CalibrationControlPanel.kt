package com.psami.visiondisplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.psami.visiondisplay.data.CalibrationState
import com.psami.visiondisplay.data.CameraOption
import androidx.compose.foundation.clickable
import com.psami.visiondisplay.data.ViewportShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationControlPanel(
    state: CalibrationState,
    hasCameraPermission: Boolean,
    isExternalDisplayConnected: Boolean,
    cameraDiagnostics: String,
    displayDiagnostics: String,
    runtimeError: String?,
    cameraPermissionActionLabel: String,
    onCameraPermissionAction: () -> Unit,
    onRetry: () -> Unit,
    cameraOptions: List<CameraOption>,
    selectedCameraId: String?,
    onCameraSelectionChange: (String?) -> Unit,
    onStateChange: (CalibrationState) -> Unit,
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
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Vision Assist Calibration") }) }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Glasses pointer",
                        style =
                            MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text =
                            "Drag here to move the pointer on the glasses.",
                        style =
                            MaterialTheme.typography.bodySmall
                    )

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .surfaceVariant,
                                    shape =
                                        RoundedCornerShape(
                                            12.dp
                                        )
                                )
                                .pointerInput(Unit) {

                                    awaitEachGesture {

                                        val down =
                                            awaitFirstDown(
                                                requireUnconsumed = false
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
                                                        it.id == down.id
                                                    }
                                                    ?: break

                                            /*
                                             * Finger released.
                                             */
                                            if (!change.pressed) {

                                                if (!isDragging) {
                                                    onCursorClick()
                                                }

                                                break
                                            }

                                            val movement =
                                                change.position -
                                                        previousPosition

                                            totalMovement +=
                                                movement

                                            /*
                                             * Don't accidentally move the pointer
                                             * when the user merely taps.
                                             */
                                            if (
                                                !isDragging &&
                                                totalMovement.getDistance() >
                                                viewConfiguration.touchSlop
                                            ) {
                                                isDragging =
                                                    true
                                            }

                                            if (isDragging) {

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
                        Text(
                            text = "Trackpad",
                            modifier =
                                Modifier.align(
                                    Alignment.Center
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium
                        )
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("System status", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (hasCameraPermission) {
                            "Camera permission: ready"
                        } else {
                            "Camera permission: required"
                        }
                    )
                    Text(
                        if (isExternalDisplayConnected) {
                            "Glasses display: connected"
                        } else {
                            "Glasses display: waiting for a presentation display"
                        }
                    )

                    if (!hasCameraPermission) {
                        Button(onClick = onCameraPermissionAction) {
                            Text(cameraPermissionActionLabel)
                        }
                    }

                    if (runtimeError != null) {
                        Text(
                            text = runtimeError,
                            color = MaterialTheme.colorScheme.error
                        )
                        OutlinedButton(onClick = onRetry) {
                            Text("Retry setup")
                        }
                    }

                    Text(
                        "External display diagnostics",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Text(
                        text = displayDiagnostics,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Camera",
                        style = MaterialTheme.typography.titleMedium
                    )

                    /*
                     * AUTO
                     */
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedCameraId == null,
                            onClick = {
                                onCameraSelectionChange(null)
                            }
                        )

                        Column {
                            Text("Auto")

                            Text(
                                text = "Prefer the widest available rear camera",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    /*
                     * DISCOVERED LENSES
                     */
                    cameraOptions.forEach { camera ->

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected =
                                    selectedCameraId == camera.id,
                                onClick = {
                                    onCameraSelectionChange(
                                        camera.id
                                    )
                                }
                            )

                            Column {
                                Text(camera.label)

                                Text(
                                    text =
                                        if (camera.isPhysicalCamera) {
                                            "Physical camera"
                                        } else {
                                            "CameraX camera"
                                        },
                                    style =
                                        MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    if (cameraOptions.isEmpty()) {
                        Text(
                            text =
                                "No individual rear lenses were exposed. " +
                                        "Auto will use Android's default rear camera.",
                            style =
                                MaterialTheme.typography.bodySmall
                        )
                    }

                    Text(
                        "CameraX diagnostics",
                        style =
                            MaterialTheme.typography.titleSmall
                    )

                    Text(
                        text = cameraDiagnostics,
                        style =
                            MaterialTheme.typography.bodySmall,
                        fontFamily =
                            FontFamily.Monospace
                    )
                }
            }

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(16.dp),

                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Text(
                        text = "Viewport shape",
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Text(
                        text =
                            "Choose the shape shown on the glasses.",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )

                    val shapes =
                        listOf(
                            ViewportShape.CIRCLE
                                    to "Circle",

                            ViewportShape.WIDE_ELLIPSE
                                    to "Wide ellipse",

                            ViewportShape.RECTANGLE
                                    to "Rectangle"
                        )

                    shapes.forEach {
                            (shape, label) ->

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onStateChange(
                                            state.copy(
                                                viewportShape =
                                                    shape
                                            )
                                        )
                                    }
                                    .padding(
                                        vertical = 4.dp
                                    ),

                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            RadioButton(
                                selected =
                                    state.viewportShape ==
                                            shape,

                                onClick = {
                                    onStateChange(
                                        state.copy(
                                            viewportShape =
                                                shape
                                        )
                                    )
                                }
                            )

                            Text(
                                text = label
                            )
                        }
                    }
                }
            }

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Camera viewport",
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Text(
                        text =
                            "Drag to move the camera view. " +
                                    "Pinch to resize it.",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )

                    Text(
                        text =
                            "Size: " +
                                    "${(state.compressionScale * 100).toInt()}%  " +
                                    "X: ${"%.2f".format(state.offsetX)}  " +
                                    "Y: ${"%.2f".format(state.offsetY)}",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .surfaceVariant,

                                    shape =
                                        RoundedCornerShape(
                                            12.dp
                                        )
                                )
                                .pointerInput(Unit) {

                                    detectTransformGestures(
                                        panZoomLock = true
                                    ) { _,
                                        pan,
                                        zoom,
                                        _ ->

                                        /*
                                         * Convert movement on the
                                         * phone pad into -1..+1
                                         * viewport coordinates.
                                         */
                                        if (
                                            size.width > 0 &&
                                            size.height > 0
                                        ) {
                                            onViewportPan(
                                                (pan.x /
                                                        size.width) *
                                                        2f,

                                                (pan.y /
                                                        size.height) *
                                                        2f
                                            )
                                        }

                                        /*
                                         * Pinch apart:
                                         * viewport gets larger.
                                         *
                                         * Pinch together:
                                         * viewport gets smaller.
                                         */
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
                        Text(
                            text =
                                "Move / pinch",
                            modifier =
                                Modifier.align(
                                    Alignment.Center
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium
                        )
                    }

                    OutlinedButton(
                        onClick =
                            onViewportReset
                    ) {
                        Text(
                            "Reset viewport"
                        )
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "High-contrast outlines",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Overlay detected edges on the live camera image.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = state.isEdgeEnhancementEnabled,
                        onCheckedChange = {
                            onStateChange(state.copy(isEdgeEnhancementEnabled = it))
                        }
                    )
                }
            }
        }
    }
}
