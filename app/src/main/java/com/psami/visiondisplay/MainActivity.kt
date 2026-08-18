package com.psami.visiondisplay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.psami.visiondisplay.data.CalibrationState
import com.psami.visiondisplay.data.CalibrationStore
import com.psami.visiondisplay.data.CameraMode
import com.psami.visiondisplay.ui.CameraXController
import com.psami.visiondisplay.ui.GlassesPresentationDialog
import com.psami.visiondisplay.ui.GlassesRenderTarget
import com.psami.visiondisplay.ui.components.CalibrationControlPanel
import com.psami.visiondisplay.ui.theme.VisionDisplayTheme

class MainActivity : ComponentActivity() {
    private lateinit var displayManager: DisplayManager
    private lateinit var cameraController: CameraXController

    private var glassesPresentation: GlassesPresentationDialog? = null
    private var renderTarget: GlassesRenderTarget? = null
    private var latestCalibrationState = CalibrationState()
    private var isDisplayListenerRegistered = false

    private var hasCameraPermission by mutableStateOf(false)
    private var permissionRequestAttempted by mutableStateOf(false)
    private var isExternalDisplayConnected by mutableStateOf(false)
    private var selectedCameraMode by mutableStateOf(CameraMode.AUTO)
    private var cameraDiagnostics by mutableStateOf("CameraX diagnostics are loading…")
    private var runtimeError by mutableStateOf<String?>(null)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRequestAttempted = true
        hasCameraPermission = granted
        if (granted) {
            runtimeError = null
            synchronizeCamera()
        } else {
            cameraController.stop()
            cameraController.refreshDiagnostics(selectedCameraMode)
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = synchronizePresentation()

        override fun onDisplayRemoved(displayId: Int) = synchronizePresentation()

        override fun onDisplayChanged(displayId: Int) = synchronizePresentation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayManager = getSystemService(DisplayManager::class.java)
        cameraController = CameraXController(applicationContext) { diagnostics ->
            cameraDiagnostics = diagnostics
        }
        cameraController.refreshDiagnostics(selectedCameraMode)
        latestCalibrationState = CalibrationStore.load(this)
        permissionRequestAttempted = savedInstanceState?.getBoolean(KEY_PERMISSION_ATTEMPTED) == true
        hasCameraPermission = cameraPermissionIsGranted()

        setContent {
            var calibrationState by remember { mutableStateOf(latestCalibrationState) }
            val shouldOpenSettings = !hasCameraPermission &&
                permissionRequestAttempted &&
                !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)

            LaunchedEffect(Unit) {
                if (!hasCameraPermission && !permissionRequestAttempted) {
                    requestCameraPermission()
                }
            }

            VisionDisplayTheme {
                CalibrationControlPanel(
                    state = calibrationState,
                    hasCameraPermission = hasCameraPermission,
                    isExternalDisplayConnected = isExternalDisplayConnected,
                    cameraMode = selectedCameraMode,
                    cameraDiagnostics = cameraDiagnostics,
                    runtimeError = runtimeError,
                    cameraPermissionActionLabel = if (shouldOpenSettings) {
                        "Open app settings"
                    } else {
                        "Grant camera permission"
                    },
                    onCameraPermissionAction = {
                        if (shouldOpenSettings) openAppSettings() else requestCameraPermission()
                    },
                    onRetry = {
                        runtimeError = null
                        cameraController.stop()
                        synchronizePresentation()
                        synchronizeCamera()
                    },
                    onCameraModeChange = { cameraMode ->
                        if (cameraMode != selectedCameraMode) {
                            selectedCameraMode = cameraMode
                            runtimeError = null
                            synchronizeCamera()
                        }
                    },
                    onStateChange = { requestedState ->
                        val newState = requestedState.normalized()
                        calibrationState = newState
                        latestCalibrationState = newState
                        CalibrationStore.save(this, newState)
                        glassesPresentation?.updateState(newState)
                        cameraController.setEdgeEnhancementEnabled(
                            newState.isEdgeEnhancementEnabled
                        )
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isDisplayListenerRegistered) {
            displayManager.registerDisplayListener(displayListener, null)
            isDisplayListenerRegistered = true
        }
        synchronizePresentation()
    }

    override fun onResume() {
        super.onResume()
        val permissionGrantedNow = cameraPermissionIsGranted()
        if (hasCameraPermission != permissionGrantedNow) {
            hasCameraPermission = permissionGrantedNow
        }
        synchronizeCamera()
    }

    override fun onStop() {
        if (isDisplayListenerRegistered) {
            displayManager.unregisterDisplayListener(displayListener)
            isDisplayListenerRegistered = false
        }
        dismissPresentation()
        super.onStop()
    }

    override fun onDestroy() {
        dismissPresentation()
        cameraController.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_PERMISSION_ATTEMPTED, permissionRequestAttempted)
        super.onSaveInstanceState(outState)
    }

    private fun synchronizePresentation() {
        val availableDisplays = displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .filter { it.isValid && it.state != Display.STATE_OFF }
        val currentDisplayId = glassesPresentation?.display?.displayId
        val targetDisplay = availableDisplays.firstOrNull { it.displayId == currentDisplayId }
            ?: availableDisplays.firstOrNull()

        if (
            targetDisplay != null &&
            glassesPresentation?.display?.displayId == targetDisplay.displayId &&
            glassesPresentation?.isShowing == true
        ) {
            isExternalDisplayConnected = true
            synchronizeCamera()
            return
        }

        dismissPresentation()
        if (targetDisplay == null) return

        lateinit var presentation: GlassesPresentationDialog
        presentation = GlassesPresentationDialog(
            context = this,
            display = targetDisplay,
            initialState = latestCalibrationState,
            onRenderTargetReady = { target ->
                if (glassesPresentation === presentation) {
                    renderTarget = target
                    synchronizeCamera()
                }
            }
        )
        presentation.setOnDismissListener {
            if (glassesPresentation === presentation) {
                glassesPresentation = null
                renderTarget = null
                isExternalDisplayConnected = false
                cameraController.stop()
                cameraController.refreshDiagnostics(selectedCameraMode)
            }
        }
        glassesPresentation = presentation

        try {
            runtimeError = null
            presentation.show()
            presentation.updateState(latestCalibrationState)
            isExternalDisplayConnected = true
        } catch (_: WindowManager.InvalidDisplayException) {
            glassesPresentation = null
            renderTarget = null
            isExternalDisplayConnected = false
            cameraController.stop()
            runtimeError = "The external display disconnected before it could be opened."
        }
    }

    private fun dismissPresentation() {
        val presentation = glassesPresentation
        glassesPresentation = null
        renderTarget = null
        isExternalDisplayConnected = false
        cameraController.stop()
        cameraController.refreshDiagnostics(selectedCameraMode)

        presentation?.setOnDismissListener(null)
        if (presentation?.isShowing == true) {
            presentation.dismiss()
        }
    }

    private fun synchronizeCamera() {
        cameraController.setEdgeEnhancementEnabled(
            latestCalibrationState.isEdgeEnhancementEnabled
        )
        val target = renderTarget
        if (
            hasCameraPermission &&
            target != null &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            cameraController.start(
                lifecycleOwner = this,
                target = target,
                edgeEnhancementEnabled = latestCalibrationState.isEdgeEnhancementEnabled,
                cameraMode = selectedCameraMode,
                onError = { message -> runtimeError = message }
            )
        } else {
            cameraController.stop()
            cameraController.refreshDiagnostics(selectedCameraMode)
        }
    }

    private fun requestCameraPermission() {
        permissionRequestAttempted = true
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun cameraPermissionIsGranted(): Boolean = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }

    private companion object {
        const val KEY_PERMISSION_ATTEMPTED = "camera_permission_attempted"
    }
}
