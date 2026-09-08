package com.psami.visiondisplay.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.psami.visiondisplay.data.CalibrationState
import com.psami.visiondisplay.data.CameraOption
import com.psami.visiondisplay.data.ViewportShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Slider
import com.psami.visiondisplay.data.ControllerPreferences
import com.psami.visiondisplay.data.KnownPerson
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

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
    onBackToControl: () -> Unit,
    controllerPreferences:
    ControllerPreferences,

    onControllerPreferencesChange:
        (ControllerPreferences) -> Unit,

    knownPeople:
    List<KnownPerson>,

    faceEnrollmentStatus:
    String?,

    onEnrollKnownPerson:
        (String) -> Unit,

    onDeleteKnownPerson:
        (String) -> Unit,
    modifier: Modifier = Modifier
) {

    var newPersonName by
    rememberSaveable {
        mutableStateOf(
            ""
        )
    }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Vision Assist Settings"
                    )
                },

                navigationIcon = {
                    Button(
                        onClick =
                            onBackToControl
                    ) {
                        Text(
                            "Control"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(
                            16.dp
                        ),

                    verticalArrangement =
                        Arrangement.spacedBy(
                            12.dp
                        )
                ) {

                    Text(
                        text =
                            "Controller",

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween,

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            "Left-handed mode"
                        )

                        Switch(
                            checked =
                                controllerPreferences
                                    .leftHandedMode,

                            onCheckedChange = {
                                    enabled ->

                                onControllerPreferencesChange(
                                    controllerPreferences.copy(
                                        leftHandedMode =
                                            enabled
                                    )
                                )
                            }
                        )
                    }

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween,

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Column(
                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                text =
                                    "Spoken feedback"
                            )

                            Text(
                                text =
                                    "Announce important control changes.",
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )
                        }

                        Switch(
                            checked =
                                controllerPreferences
                                    .spokenFeedbackEnabled,

                            onCheckedChange = {
                                    enabled ->

                                onControllerPreferencesChange(
                                    controllerPreferences.copy(
                                        spokenFeedbackEnabled =
                                            enabled
                                    )
                                )
                            }
                        )
                    }

                    Text(
                        text =
                            "Pointer sensitivity: " +
                                    "${
                                        "%.1f".format(
                                            controllerPreferences
                                                .pointerSensitivity
                                        )
                                    }×"
                    )

                    Slider(
                        value =
                            controllerPreferences
                                .pointerSensitivity,

                        onValueChange = {
                                sensitivity ->

                            onControllerPreferencesChange(
                                controllerPreferences.copy(
                                    pointerSensitivity =
                                        sensitivity
                                )
                            )
                        },

                        valueRange =
                            ControllerPreferences
                                .MIN_POINTER_SENSITIVITY..
                                    ControllerPreferences
                                        .MAX_POINTER_SENSITIVITY
                    )

                    Text(
                        text =
                            "Viewport sensitivity: " +
                                    "${
                                        "%.1f".format(
                                            controllerPreferences
                                                .viewportSensitivity
                                        )
                                    }×"
                    )

                    Slider(
                        value =
                            controllerPreferences
                                .viewportSensitivity,

                        onValueChange = {
                                sensitivity ->

                            onControllerPreferencesChange(
                                controllerPreferences.copy(
                                    viewportSensitivity =
                                        sensitivity
                                )
                            )
                        },

                        valueRange =
                            ControllerPreferences
                                .MIN_VIEWPORT_SENSITIVITY..
                                    ControllerPreferences
                                        .MAX_VIEWPORT_SENSITIVITY
                    )
                }
            }

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(
                            16.dp
                        ),

                    verticalArrangement =
                        Arrangement.spacedBy(
                            12.dp
                        )
                ) {

                    Text(
                        text =
                            "Known people",

                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Text(
                        text =
                            "Face templates stay on this device.",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )

                    OutlinedTextField(
                        value =
                            newPersonName,

                        onValueChange = {
                            newPersonName =
                                it
                        },

                        label = {
                            Text(
                                "Person name"
                            )
                        },

                        singleLine =
                            true,

                        modifier =
                            Modifier.fillMaxWidth()
                    )

                    Button(
                        enabled =
                            newPersonName
                                .isNotBlank() &&
                                    isExternalDisplayConnected,

                        onClick = {

                            onEnrollKnownPerson(
                                newPersonName
                                    .trim()
                            )
                        }
                    ) {

                        Text(
                            "Enroll visible face"
                        )
                    }

                    faceEnrollmentStatus
                        ?.let {
                                status ->

                            Text(
                                text =
                                    status,

                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )
                        }

                    knownPeople.forEach {
                            person ->

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth(),

                            horizontalArrangement =
                                Arrangement
                                    .SpaceBetween,

                            verticalAlignment =
                                Alignment
                                    .CenterVertically
                        ) {

                            Text(
                                text =
                                    person.name,

                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            )

                            OutlinedButton(
                                onClick = {

                                    onDeleteKnownPerson(
                                        person.id
                                    )
                                }
                            ) {

                                Text(
                                    "Delete"
                                )
                            }
                        }
                    }

                    if (
                        knownPeople.isEmpty()
                    ) {
                        Text(
                            text =
                                "No people enrolled yet.",
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
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

                    shapes.forEach { (shape, label) ->

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
