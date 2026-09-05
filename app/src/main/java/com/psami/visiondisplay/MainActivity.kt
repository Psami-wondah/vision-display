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
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.psami.visiondisplay.data.CalibrationState
import com.psami.visiondisplay.data.CalibrationStore
import com.psami.visiondisplay.data.CameraOption
import com.psami.visiondisplay.ui.CameraXController
import com.psami.visiondisplay.ui.GlassesPresentationDialog
import com.psami.visiondisplay.ui.GlassesRenderTarget
import com.psami.visiondisplay.ui.components.CalibrationControlPanel
import com.psami.visiondisplay.ui.theme.VisionDisplayTheme
import android.os.SystemClock
import android.view.MotionEvent

class MainActivity : ComponentActivity() {
    private lateinit var displayManager: DisplayManager
    private lateinit var cameraController: CameraXController

    private var glassesPresentation: GlassesPresentationDialog? = null
    private var renderTarget: GlassesRenderTarget? = null
    private var latestCalibrationState by
    mutableStateOf(CalibrationState())
    private var isDisplayListenerRegistered = false

    private var hasCameraPermission by mutableStateOf(false)
    private var permissionRequestAttempted by mutableStateOf(false)
    private var isExternalDisplayConnected by mutableStateOf(false)
    private var cameraOptions by mutableStateOf<List<CameraOption>>(emptyList())

    /*
     * null = Auto
     */
    private var selectedCameraId by mutableStateOf<String?>(null)
    private var cameraDiagnostics by mutableStateOf("CameraX diagnostics are loading…")

    private var displayDiagnostics by mutableStateOf(
        "Waiting for external display…"
    )
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
            cameraController.refreshDiagnostics(
                selectedCameraId
            )
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = synchronizePresentation()

        override fun onDisplayRemoved(displayId: Int) = synchronizePresentation()

        override fun onDisplayChanged(displayId: Int) = synchronizePresentation()
    }

    private fun cycleCamera() {
        if (cameraOptions.isEmpty()) {
            return
        }

        val currentIndex =
            cameraOptions.indexOfFirst {
                it.id == selectedCameraId
            }

        val nextIndex =
            if (currentIndex == -1) {
                0
            } else {
                (currentIndex + 1) %
                        cameraOptions.size
            }

        selectedCameraId =
            cameraOptions[nextIndex].id

        runtimeError = null

        synchronizeCamera()
    }

    private fun recenterGlassesCursor() {
        renderTarget
            ?.cursorOverlayView
            ?.centerCursor()
    }

    private fun scaleCameraViewport(
        scaleFactor: Float
    ) {
        if (
            !scaleFactor.isFinite() ||
            scaleFactor <= 0f
        ) {
            return
        }

        val newScale =
            (
                    latestCalibrationState
                        .compressionScale *
                            scaleFactor
                    )
                .coerceIn(
                    CalibrationState.MIN_COMPRESSION,
                    CalibrationState.MAX_COMPRESSION
                )

        applyCalibrationState(
            latestCalibrationState.copy(
                compressionScale =
                    newScale
            )
        )
    }

    private fun panCameraViewport(
        deltaX: Float,
        deltaY: Float
    ) {
        applyCalibrationState(
            latestCalibrationState.copy(
                offsetX =
                    (
                            latestCalibrationState.offsetX +
                                    deltaX
                            )
                        .coerceIn(
                            CalibrationState.MIN_OFFSET,
                            CalibrationState.MAX_OFFSET
                        ),

                offsetY =
                    (
                            latestCalibrationState.offsetY +
                                    deltaY
                            )
                        .coerceIn(
                            CalibrationState.MIN_OFFSET,
                            CalibrationState.MAX_OFFSET
                        )
            )
        )
    }

    private fun resetCameraViewport() {
        applyCalibrationState(
            latestCalibrationState.copy(
                /*
                 * 70% gives us enough spare space
                 * for the viewport trackpad to
                 * immediately move the ellipse.
                 */
                compressionScale = 0.7f,
                offsetX = 0f,
                offsetY = 0f
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayManager = getSystemService(DisplayManager::class.java)
        cameraController = CameraXController(
            context = applicationContext,
            onCameraDiagnosticsChanged = { diagnostics ->
                cameraDiagnostics = diagnostics
            },
            onCameraOptionsChanged = { options ->
                cameraOptions = options

                /*
                 * The camera configuration could change, e.g.
                 * after reconnecting hardware.
                 */
                if (
                    selectedCameraId != null &&
                    options.none {
                        it.id == selectedCameraId
                    }
                ) {
                    selectedCameraId = null
                }
            }
        )

        cameraController.refreshCameraOptions()
        cameraController.refreshDiagnostics(
            selectedCameraId
        )
        latestCalibrationState = CalibrationStore.load(this)
        permissionRequestAttempted =
            savedInstanceState?.getBoolean(KEY_PERMISSION_ATTEMPTED) == true
        hasCameraPermission = cameraPermissionIsGranted()

        setContent {

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
                    state = latestCalibrationState,
                    hasCameraPermission = hasCameraPermission,
                    isExternalDisplayConnected = isExternalDisplayConnected,
                    displayDiagnostics = displayDiagnostics,
                    cameraOptions = cameraOptions,
                    selectedCameraId = selectedCameraId,
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
                    onCameraSelectionChange = { cameraId ->
                        if (cameraId != selectedCameraId) {
                            selectedCameraId = cameraId
                            runtimeError = null

                            synchronizeCamera()
                        }
                    },
                    onStateChange = { requestedState ->
                        applyCalibrationState(requestedState)
                    },
                    onCursorMove = { deltaX, deltaY ->
                        moveGlassesCursor(
                            deltaX,
                            deltaY
                        )
                    },
                    onCursorClick = {
                        clickGlassesCursor()
                    },
                    onViewportPan = {
                            deltaX,
                            deltaY ->

                        panCameraViewport(
                            deltaX,
                            deltaY
                        )
                    },

                    onViewportScale = {
                            scaleFactor ->

                        scaleCameraViewport(
                            scaleFactor
                        )
                    },

                    onViewportReset = {
                        resetCameraViewport()
                    },
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
            onDisplayDiagnosticsChanged = { diagnostics ->
                displayDiagnostics = diagnostics
            },
            onRenderTargetReady = { target ->
                if (glassesPresentation === presentation) {
                    renderTarget = target
                    synchronizeCamera()
                }
            },
            onToggleEdgeEnhancement = {
                toggleEdgeEnhancement()
            },
            onCycleCamera = {
                cycleCamera()
            },

            onRecenterCursor = {
                recenterGlassesCursor()
            },
        )
        presentation.setOnDismissListener {
            if (glassesPresentation === presentation) {
                glassesPresentation = null
                renderTarget = null
                isExternalDisplayConnected = false
                cameraController.stop()
                cameraController.refreshDiagnostics(
                    selectedCameraId
                )
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
        cameraController.refreshDiagnostics(
            selectedCameraId
        )
        displayDiagnostics = "Waiting for external display…"
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
                edgeEnhancementEnabled =
                    latestCalibrationState.isEdgeEnhancementEnabled,
                selectedCameraId =
                    selectedCameraId,
                onError = { message ->
                    runtimeError = message
                }
            )
        } else {
            cameraController.stop()
            cameraController.refreshDiagnostics(
                selectedCameraId
            )
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

    private fun applyCalibrationState(
        requestedState: CalibrationState
    ) {
        val newState =
            requestedState.normalized()

        latestCalibrationState =
            newState

        CalibrationStore.save(
            this,
            newState
        )

        glassesPresentation
            ?.updateState(newState)

        cameraController
            .setEdgeEnhancementEnabled(
                newState.isEdgeEnhancementEnabled
            )
    }

    private fun toggleEdgeEnhancement() {
        applyCalibrationState(
            latestCalibrationState.copy(
                isEdgeEnhancementEnabled =
                    !latestCalibrationState
                        .isEdgeEnhancementEnabled
            )
        )
    }

    private fun moveGlassesCursor(
        deltaX: Float,
        deltaY: Float
    ) {
        val target =
            renderTarget
                ?: return

        val sensitivity = 1.8f

        target.cursorOverlayView.moveBy(
            deltaX = deltaX * sensitivity,
            deltaY = deltaY * sensitivity
        )
    }

    private fun clickGlassesCursor() {
        val target =
            renderTarget
                ?: return

        val position =
            target.cursorOverlayView
                .currentPosition()
                ?: return

        val eventTime =
            SystemClock.uptimeMillis()

        val down =
            MotionEvent.obtain(
                eventTime,
                eventTime,
                MotionEvent.ACTION_DOWN,
                position.x,
                position.y,
                0
            )

        val up =
            MotionEvent.obtain(
                eventTime,
                eventTime + 16,
                MotionEvent.ACTION_UP,
                position.x,
                position.y,
                0
            )

        try {
            target.interactionLayer
                .dispatchTouchEvent(down)

            target.interactionLayer
                .dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }


    private companion object {
        const val KEY_PERMISSION_ATTEMPTED = "camera_permission_attempted"
    }
}
