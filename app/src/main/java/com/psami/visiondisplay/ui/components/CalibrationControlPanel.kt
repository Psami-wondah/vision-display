package com.psami.visiondisplay.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.psami.visiondisplay.data.CalibrationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationControlPanel(
    state: CalibrationState,
    hasCameraPermission: Boolean,
    isExternalDisplayConnected: Boolean,
    runtimeError: String?,
    cameraPermissionActionLabel: String,
    onCameraPermissionAction: () -> Unit,
    onRetry: () -> Unit,
    onStateChange: (CalibrationState) -> Unit,
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
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Visual compression: ${(state.compressionScale * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Slider(
                        value = state.compressionScale,
                        onValueChange = { onStateChange(state.copy(compressionScale = it)) },
                        valueRange = CalibrationState.MIN_COMPRESSION..CalibrationState.MAX_COMPRESSION
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Spatial alignment", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Horizontal shift: ${"%.2f".format(state.offsetX)}")
                    Slider(
                        value = state.offsetX,
                        onValueChange = { onStateChange(state.copy(offsetX = it)) },
                        valueRange = CalibrationState.MIN_OFFSET..CalibrationState.MAX_OFFSET
                    )

                    Text("Vertical shift: ${"%.2f".format(state.offsetY)}")
                    Slider(
                        value = state.offsetY,
                        onValueChange = { onStateChange(state.copy(offsetY = it)) },
                        valueRange = CalibrationState.MIN_OFFSET..CalibrationState.MAX_OFFSET
                    )
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
