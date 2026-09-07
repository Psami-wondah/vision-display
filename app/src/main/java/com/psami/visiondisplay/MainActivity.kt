package com.psami.visiondisplay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.psami.visiondisplay.data.CalibrationState
import com.psami.visiondisplay.data.CalibrationStore
import com.psami.visiondisplay.data.CameraOption
import com.psami.visiondisplay.data.ControllerPreferences
import com.psami.visiondisplay.data.ControllerPreferencesStore
import com.psami.visiondisplay.data.ViewportShape
import com.psami.visiondisplay.ui.CameraXController
import com.psami.visiondisplay.ui.GlassesPresentationDialog
import com.psami.visiondisplay.ui.GlassesRenderTarget
import com.psami.visiondisplay.ui.HapticController
import com.psami.visiondisplay.ui.SpeechController
import com.psami.visiondisplay.ui.components.CalibrationControlPanel
import com.psami.visiondisplay.ui.components.EyesFreeControlPanel
import com.psami.visiondisplay.ui.theme.VisionDisplayTheme

class MainActivity : ComponentActivity() {
    private lateinit var displayManager: DisplayManager
    private lateinit var cameraController: CameraXController
    private lateinit var hapticController: HapticController
    private lateinit var speechController:
            SpeechController

    private var glassesPresentation: GlassesPresentationDialog? = null
    private var renderTarget: GlassesRenderTarget? = null
    private var latestCalibrationState by
    mutableStateOf(CalibrationState())
    private var isDisplayListenerRegistered = false

    private var hasCameraPermission by mutableStateOf(false)
    private var permissionRequestAttempted by mutableStateOf(false)
    private var isExternalDisplayConnected by mutableStateOf(false)
    private var cameraOptions by mutableStateOf<List<CameraOption>>(emptyList())
    private var controllerPreferences by mutableStateOf(
        ControllerPreferences()
    )
    private var isTextRecognitionInProgress by
    mutableStateOf(false)

    private var isOcrInspectionActive by
    mutableStateOf(false)

    private var isSpeechActive by
    mutableStateOf(false)
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
        hapticController.confirm()
        announceSelectedCamera()
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
        val sensitivity =
            controllerPreferences
                .viewportSensitivity

        applyCalibrationState(
            latestCalibrationState.copy(
                offsetX =
                    (
                            latestCalibrationState.offsetX +
                                    deltaX * sensitivity
                            )
                        .coerceIn(
                            CalibrationState.MIN_OFFSET,
                            CalibrationState.MAX_OFFSET
                        ),

                offsetY =
                    (
                            latestCalibrationState.offsetY +
                                    deltaY * sensitivity
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

    private fun updateControllerPreferences(
        newPreferences: ControllerPreferences
    ) {
        controllerPreferences =
            newPreferences.normalized()

        ControllerPreferencesStore.save(
            this,
            controllerPreferences
        )
    }

    private fun updateScreenWakeState(
        keepAwake: Boolean
    ) {
        if (
            keepAwake
        ) {
            window.addFlags(
                WindowManager.LayoutParams
                    .FLAG_KEEP_SCREEN_ON
            )
        } else {
            window.clearFlags(
                WindowManager.LayoutParams
                    .FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun announceSelectedCamera() {
        val label =
            if (
                selectedCameraId == null
            ) {
                "Auto camera"
            } else {
                cameraOptions
                    .firstOrNull {
                        it.id ==
                                selectedCameraId
                    }
                    ?.label
                    ?: "Camera changed"
            }

        speakFeedback(
            label
        )
    }

    private fun speakFeedback(
        text: String
    ) {
        if (
            !controllerPreferences
                .spokenFeedbackEnabled
        ) {
            return
        }

        speechController.speak(
            text
        )
    }

    private fun requestTextRecognition() {

        if (
            isSpeechActive
        ) {
            speechController.stop()
        }

        if (
            isTextRecognitionInProgress
        ) {
            return
        }

        val accepted =
            cameraController
                .requestTextRecognition()

        if (
            !accepted
        ) {
            hapticController.reset()

            speakFeedback(
                "Camera not ready"
            )

            return
        }

        renderTarget
            ?.ocrOverlayView
            ?.clear()

        isTextRecognitionInProgress =
            true

        hapticController.confirm()

        speakFeedback(
            "Reading text"
        )
    }

    private fun stopReadingText() {

        speechController.stop()

        hapticController.click()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        displayManager = getSystemService(DisplayManager::class.java)
        controllerPreferences =
            ControllerPreferencesStore.load(
                this
            )


        hapticController = HapticController(this)
        speechController =
            SpeechController(
                context = this,
                onSpeakingChanged = {
                        speaking ->

                    runOnUiThread {
                        isSpeechActive =
                            speaking
                    }
                }
            )
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
            },
            onTextRecognized = {
                    capture ->

                isTextRecognitionInProgress =
                    false

                val result =
                    capture.result

                if (
                    result.fullText.isBlank()
                ) {

                    /*
                     * No inspection mode is useful
                     * if nothing was recognised.
                     */
                    if (
                        !capture
                            .frozenFrame
                            .isRecycled
                    ) {
                        capture
                            .frozenFrame
                            .recycle()
                    }

                    isOcrInspectionActive =
                        false

                    hapticController.reset()

                    speechController.speak(
                        "No text found"
                    )

                } else {

                    val presentation =
                        glassesPresentation

                    if (
                        presentation == null
                    ) {

                        if (
                            !capture
                                .frozenFrame
                                .isRecycled
                        ) {
                            capture
                                .frozenFrame
                                .recycle()
                        }

                        isOcrInspectionActive =
                            false

                    } else {

                        presentation
                            .showOcrInspection(
                                capture
                            )

                        isOcrInspectionActive =
                            true

                        hapticController.confirm()

                        speechController.speak(
                            result.fullText
                        )
                    }
                }
            },

            onTextRecognitionError = { message ->

                isTextRecognitionInProgress =
                    false

                runtimeError =
                    message

                hapticController.reset()

                speechController.speak(
                    "Unable to read text"
                )
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

            var showSettingsPanel by
            rememberSaveable {
                mutableStateOf(false)
            }
            val shouldOpenSettings = !hasCameraPermission &&
                    permissionRequestAttempted &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)

            LaunchedEffect(Unit) {
                if (!hasCameraPermission && !permissionRequestAttempted) {
                    requestCameraPermission()
                }
            }

            VisionDisplayTheme {
                if (
                    showSettingsPanel
                ) {

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

                                announceSelectedCamera()

                                synchronizeCamera()
                            }
                        },
                        onStateChange = { requestedState ->
                            applyCalibrationState(requestedState)
                        },
                        onBackToControl = {
                            showSettingsPanel =
                                false
                        },
                        controllerPreferences =
                            controllerPreferences,

                        onControllerPreferencesChange = { preferences ->

                            updateControllerPreferences(
                                preferences
                            )
                        },
                    )

                } else {

                    EyesFreeControlPanel(
                        isExternalDisplayConnected =
                            isExternalDisplayConnected,

                        onCursorMove = { deltaX,
                                         deltaY ->

                            moveGlassesCursor(
                                deltaX,
                                deltaY
                            )
                        },

                        onCursorClick = {
                            hapticController.click()
                            clickGlassesCursor()
                        },

                        onViewportPan = { deltaX,
                                          deltaY ->

                            panCameraViewport(
                                deltaX,
                                deltaY
                            )
                        },

                        onViewportScale = { scaleFactor ->

                            scaleCameraViewport(
                                scaleFactor
                            )
                        },

                        onViewportReset = {
                            hapticController.reset()
                            resetCameraViewport()
                            speakFeedback(
                                "Viewport centred"
                            )
                        },

                        onOpenSettings = {
                            showSettingsPanel =
                                true
                        },
                        leftHandedMode =
                            controllerPreferences
                                .leftHandedMode,
                        isTextRecognitionInProgress =
                            isTextRecognitionInProgress,

                        onReadText = {
                            requestTextRecognition()
                        },
                        isSpeechActive =
                            isSpeechActive,
                        onStopReading = {
                            stopReadingText()
                        },
                        isOcrInspectionActive =
                            isOcrInspectionActive,

                        onResumeCamera = {
                            resumeLiveCamera()
                        },
                    )
                }
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
        speechController.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_PERMISSION_ATTEMPTED, permissionRequestAttempted)
        super.onSaveInstanceState(outState)
    }

    private fun resumeLiveCamera() {

        speechController.stop()

        glassesPresentation
            ?.clearOcrInspection()

        isOcrInspectionActive =
            false

        hapticController.click()
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
                updateScreenWakeState(
                    false
                )
            }
        }
        glassesPresentation = presentation

        try {
            runtimeError = null
            presentation.show()
            presentation.updateState(latestCalibrationState)
            isExternalDisplayConnected = true
            updateScreenWakeState(
                true
            )
        } catch (_: WindowManager.InvalidDisplayException) {
            glassesPresentation = null
            renderTarget = null
            isExternalDisplayConnected = false
            cameraController.stop()
            runtimeError = "The external display disconnected before it could be opened."
            updateScreenWakeState(
                false
            )
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
        isOcrInspectionActive =
            false

        isTextRecognitionInProgress =
            false
        speechController.stop()
        updateScreenWakeState(
            false
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
        val previousState =
            latestCalibrationState
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

        /*
     * Only announce discrete state changes.
     * Do not announce pan/scale changes.
     */
        if (
            previousState
                .isEdgeEnhancementEnabled !=
            newState
                .isEdgeEnhancementEnabled
        ) {
            speakFeedback(
                if (
                    newState
                        .isEdgeEnhancementEnabled
                ) {
                    "Edges on"
                } else {
                    "Edges off"
                }
            )
        }

        if (
            previousState.viewportShape !=
            newState.viewportShape
        ) {
            val shapeName =
                when (
                    newState.viewportShape
                ) {
                    ViewportShape.CIRCLE ->
                        "Circle"

                    ViewportShape.WIDE_ELLIPSE ->
                        "Wide ellipse"

                    ViewportShape.RECTANGLE ->
                        "Rectangle"
                }

            speakFeedback(
                shapeName
            )
        }
    }

    private fun toggleEdgeEnhancement() {
        applyCalibrationState(
            latestCalibrationState.copy(
                isEdgeEnhancementEnabled =
                    !latestCalibrationState
                        .isEdgeEnhancementEnabled
            )
        )
        hapticController.confirm()
    }


    private fun moveGlassesCursor(
        deltaX: Float,
        deltaY: Float
    ) {
        val target =
            renderTarget
                ?: return

        val sensitivity =
            controllerPreferences
                .pointerSensitivity

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

        /*
 * OCR regions get first chance
 * to handle the click.
 */
        val textRegion =
            target
                .ocrOverlayView
                .findRegionAtPosition(
                    x =
                        position.x,

                    y =
                        position.y,

                    coordinateView =
                        target
                            .cursorOverlayView
                )

        if (
            textRegion != null
        ) {

            target
                .ocrOverlayView
                .selectRegion(
                    textRegion.id
                )

            /*
             * Interrupt whatever was
             * previously being read.
             */
            speechController.stop()

            speechController.speak(
                textRegion.text
            )

            return
        }
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
