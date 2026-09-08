package com.psami.visiondisplay.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraFilter
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.psami.visiondisplay.data.CameraOption
import com.psami.visiondisplay.data.KnownPerson
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan
import kotlin.math.ceil

class CameraXController(
    context: Context,
    private val onCameraDiagnosticsChanged: (String) -> Unit,
    private val onCameraOptionsChanged: (List<CameraOption>) -> Unit,
    private val onTextRecognized:
        (OcrCapture) -> Unit,
    private val onTextRecognitionError:
        (String) -> Unit,
    private val onFacesDetected:
        (FaceDetectionResult) -> Unit,
    private val onFaceDetectionError:
        (String) -> Unit,
    private val faceRecognitionRepository:
    FaceRecognitionRepository,

    private val onFaceEnrollmentStatus:
        (String) -> Unit,

    private val onFaceEnrollmentCompleted:
        (KnownPerson) -> Unit,
) : AutoCloseable {

    private data class LogicalCameraDetails(
        val cameraId: String,
        val characteristics: CameraCharacteristics
    )

    private data class CameraOptics(
        val focalLengthMm: Float,
        val sensorWidthMm: Float,
        val horizontalFovRadians: Double
    ) {
        val horizontalFovDegrees: Double
            get() = Math.toDegrees(horizontalFovRadians)
    }

    private val appContext = context.applicationContext

    private val cameraManager =
        appContext.getSystemService(CameraManager::class.java)

    private val mainExecutor =
        ContextCompat.getMainExecutor(appContext)

    private val analysisExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private var lastActivePhysicalCameraId: String? = null

    private val cameraProviderFuture =
        ProcessCameraProvider.getInstance(appContext)

    private val edgeEnhancementEnabled =
        AtomicBoolean(false)

    private var bindingGeneration = 0
    private var diagnosticsGeneration = 0

    private var currentTarget: GlassesRenderTarget? = null
    private var currentLifecycleOwner: LifecycleOwner? = null
    private var currentSelectedCameraId: String? = null

    private var currentPreview: Preview? = null
    private var currentAnalysis: ImageAnalysis? = null

    private var lastDiagnostics: String? = null

    private var currentDiagnosticsBase =
        ""
    private var lastCameraOptions: List<CameraOption> = emptyList()

    private var isBinding = false
    private var isBound = false
    private var isClosed = false


    private val textRecognitionProcessor =
        TextRecognitionProcessor()

    private val ocrRequested =
        AtomicBoolean(false)

    private val ocrInProgress =
        AtomicBoolean(false)


    private val faceDetectionProcessor =
        FaceDetectionProcessor()

    private val faceDetectionEnabled =
        AtomicBoolean(
            true
        )

    private val faceDetectionInProgress =
        AtomicBoolean(
            false
        )


    private val faceRecognitionExecutor =
        Executors
            .newSingleThreadExecutor()

    private val enrollmentLock =
        Any()

    private var pendingEnrollmentName:
            String? = null

    private var enrollmentTrackingId:
            Int? = null

    private val enrollmentSamples =
        mutableListOf<FloatArray>()

    private var lastEnrollmentSampleTime =
        0L


    fun requestTextRecognition():
            Boolean {

        if (
            isClosed ||
            !isBound ||
            currentAnalysis == null
        ) {
            return false
        }

        /*
         * We don't perform OCR here.
         *
         * We simply tell the existing analyzer
         * to use the next available frame.
         */
        ocrRequested.set(
            true
        )

        return true
    }

    fun startFaceEnrollment(
        name: String
    ): Boolean {

        val cleanName =
            name.trim()

        if (
            cleanName.isBlank() ||
            !isBound ||
            isClosed
        ) {
            return false
        }

        synchronized(
            enrollmentLock
        ) {

            pendingEnrollmentName =
                cleanName

            enrollmentTrackingId =
                null

            enrollmentSamples
                .clear()

            lastEnrollmentSampleTime =
                0L
        }

        onFaceEnrollmentStatus(
            "Look at $cleanName and keep one face in view."
        )

        return true
    }

    private fun processFaceFrame(
        capture: FaceFrameCapture,
        generation: Int
    ) {

        faceRecognitionExecutor
            .execute {

                try {

                    if (
                        isClosed ||
                        generation !=
                        bindingGeneration
                    ) {
                        return@execute
                    }

                    val enrollmentName =
                        synchronized(
                            enrollmentLock
                        ) {
                            pendingEnrollmentName
                        }

                    val output =
                        if (
                            enrollmentName !=
                            null
                        ) {

                            processEnrollmentFrame(
                                name =
                                    enrollmentName,

                                frame =
                                    capture.frame,

                                result =
                                    capture.result
                            )

                            capture.result

                        } else {

                            faceRecognitionRepository
                                .recognize(
                                    frame =
                                        capture.frame,

                                    result =
                                        capture.result
                                )
                        }

                    mainExecutor.execute {

                        if (
                            !isClosed &&
                            generation ==
                            bindingGeneration
                        ) {
                            onFacesDetected(
                                output
                            )
                        }
                    }

                } catch (
                    exception: Exception
                ) {

                    mainExecutor.execute {

                        onFaceDetectionError(
                            exception.message
                                ?: "Face recognition failed"
                        )
                    }

                } finally {

                    if (
                        !capture
                            .frame
                            .isRecycled
                    ) {
                        capture
                            .frame
                            .recycle()
                    }

                    faceDetectionInProgress
                        .set(
                            false
                        )
                }
            }
    }

    private fun processEnrollmentFrame(
        name: String,
        frame: Bitmap,
        result: FaceDetectionResult
    ) {

        if (
            result.faces.size != 1
        ) {

            mainExecutor.execute {

                onFaceEnrollmentStatus(
                    "Keep exactly one face in view."
                )
            }

            return
        }

        val face =
            result.faces.first()

        if (
            kotlin.math.abs(
                face.eulerX
            ) > 25f ||
            kotlin.math.abs(
                face.eulerY
            ) > 30f ||
            kotlin.math.abs(
                face.eulerZ
            ) > 25f
        ) {

            mainExecutor.execute {

                onFaceEnrollmentStatus(
                    "Look more directly at the camera."
                )
            }

            return
        }

        val now =
            SystemClock
                .elapsedRealtime()

        synchronized(
            enrollmentLock
        ) {

            if (
                pendingEnrollmentName !=
                name
            ) {
                return
            }

            /*
             * Keep samples from the same tracked
             * face where ML Kit provides an ID.
             */
            if (
                enrollmentTrackingId != null &&
                face.trackingId != null &&
                enrollmentTrackingId !=
                face.trackingId
            ) {

                enrollmentSamples
                    .clear()

                enrollmentTrackingId =
                    face.trackingId

                lastEnrollmentSampleTime =
                    0L
            }

            if (
                enrollmentTrackingId ==
                null
            ) {
                enrollmentTrackingId =
                    face.trackingId
            }

            /*
             * Don't collect five practically
             * identical adjacent frames.
             */
            if (
                now -
                lastEnrollmentSampleTime <
                250L
            ) {
                return
            }
        }

        val embedding =
            faceRecognitionRepository
                .createEmbedding(
                    frame =
                        frame,

                    boundingBox =
                        face.boundingBox
                )

        var completedPerson:
                KnownPerson? = null

        var currentCount =
            0

        synchronized(
            enrollmentLock
        ) {

            if (
                pendingEnrollmentName !=
                name
            ) {
                return
            }

            enrollmentSamples.add(
                embedding
            )

            lastEnrollmentSampleTime =
                now

            currentCount =
                enrollmentSamples.size

            if (
                currentCount >=
                ENROLLMENT_SAMPLE_COUNT
            ) {

                completedPerson =
                    faceRecognitionRepository
                        .enroll(
                            name =
                                name,

                            samples =
                                enrollmentSamples
                                    .toList()
                        )

                pendingEnrollmentName =
                    null

                enrollmentTrackingId =
                    null

                enrollmentSamples
                    .clear()

                lastEnrollmentSampleTime =
                    0L
            }
        }

        val person =
            completedPerson

        mainExecutor.execute {

            if (
                person != null
            ) {

                onFaceEnrollmentCompleted(
                    person
                )

            } else {

                onFaceEnrollmentStatus(
                    "Capturing $name: " +
                            "$currentCount/" +
                            "$ENROLLMENT_SAMPLE_COUNT. " +
                            "Move slightly between samples."
                )
            }
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun start(
        lifecycleOwner: LifecycleOwner,
        target: GlassesRenderTarget,
        edgeEnhancementEnabled: Boolean,
        selectedCameraId: String?,
        onError: (String) -> Unit
    ) {

        check(!isClosed) {
            "CameraXController has already been closed"
        }


        setEdgeEnhancementEnabled(edgeEnhancementEnabled)

        val isSameTarget =
            currentTarget?.previewView === target.previewView

        if (
            isSameTarget &&
            currentLifecycleOwner === lifecycleOwner &&
            currentSelectedCameraId == selectedCameraId &&
            (isBinding || isBound)
        ) {
            updateTargetRotation()
            return
        }

        stop()
        orientationEventListener.enable()

        currentTarget = target
        currentLifecycleOwner = lifecycleOwner
        currentSelectedCameraId = selectedCameraId

        target.edgeOverlayView.setEdgeEnhancementEnabled(
            edgeEnhancementEnabled
        )

        isBinding = true

        val requestedGeneration = ++bindingGeneration
        val requestedDiagnosticsGeneration = ++diagnosticsGeneration

        cameraProviderFuture.addListener({

            if (
                isClosed ||
                requestedGeneration != bindingGeneration
            ) {
                return@addListener
            }

            try {
                val cameraProvider =
                    cameraProviderFuture.get()

                val selectableCameras =
                    discoverSelectableBackCameras(cameraProvider)

                publishCameraOptions(selectableCameras)

                val requestedChoice =
                    chooseCamera(
                        selectedCameraId = selectedCameraId,
                        cameras = selectableCameras
                    )

                val previewResolutionSelector =
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(
                            AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                        )
                        .build()

                val analysisResolutionSelector =
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(
                            AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                        )
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(
                                    1280,
                                    720
                                ),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                            )
                        )
                        .build()

                val targetRotation =
                    currentDeviceRotation

                val previewBuilder =
                    Preview.Builder()
                        .setResolutionSelector(
                            previewResolutionSelector
                        )
                        .setTargetRotation(
                            currentDeviceRotation
                        )

                requestedChoice
                    .physicalCameraId
                    ?.let { physicalCameraId ->

                        Camera2Interop.Extender(
                            previewBuilder
                        )
                            .setPhysicalCameraId(
                                physicalCameraId
                            )
                    }

                Camera2Interop.Extender(
                    previewBuilder
                )
                    .setSessionCaptureCallback(
                        object :
                            CameraCaptureSession.CaptureCallback() {

                            override fun onCaptureCompleted(
                                session:
                                CameraCaptureSession,

                                request:
                                CaptureRequest,

                                result:
                                TotalCaptureResult
                            ) {

                                val activePhysicalId =
                                    result.get(
                                        CaptureResult
                                            .LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID
                                    )

                                if (
                                    activePhysicalId != null &&
                                    activePhysicalId !=
                                    lastActivePhysicalCameraId
                                ) {
                                    lastActivePhysicalCameraId =
                                        activePhysicalId
                                    mainExecutor.execute {
                                        publishCombinedDiagnostics()
                                    }
                                    Log.d(
                                        TAG,
                                        "ACTIVE PHYSICAL CAMERA: " +
                                                activePhysicalId
                                    )
                                }
                            }
                        }
                    )

                val preview =
                    previewBuilder
                        .build()
                        .also {
                            it.surfaceProvider =
                                target.previewView.surfaceProvider
                        }


                val analysisBuilder =
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(
                            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                        )
                        .setResolutionSelector(
                            analysisResolutionSelector
                        )
                        .setTargetRotation(
                            currentDeviceRotation
                        )


                requestedChoice
                    .physicalCameraId
                    ?.let { physicalCameraId ->

                        Camera2Interop.Extender(
                            analysisBuilder
                        )
                            .setPhysicalCameraId(
                                physicalCameraId
                            )
                    }

                val analysis =
                    analysisBuilder
                        .build()
                        .also {
                            it.setAnalyzer(
                                analysisExecutor,
                                VisionAnalyzer(
                                    overlayView =
                                        target.edgeOverlayView,

                                    edgeEnabled =
                                        this.edgeEnhancementEnabled,

                                    ocrRequested =
                                        ocrRequested,

                                    ocrInProgress =
                                        ocrInProgress,

                                    textRecognitionProcessor =
                                        textRecognitionProcessor,

                                    mainExecutor =
                                        mainExecutor,

                                    onTextRecognized =
                                        onTextRecognized,

                                    onTextRecognitionError =
                                        onTextRecognitionError,

                                    onEdgeError = { message ->

                                        if (
                                            !isClosed &&
                                            requestedGeneration ==
                                            bindingGeneration
                                        ) {
                                            onError(
                                                message
                                            )
                                        }
                                    },
                                    faceDetectionEnabled =
                                        faceDetectionEnabled,

                                    faceDetectionInProgress =
                                        faceDetectionInProgress,

                                    faceDetectionProcessor =
                                        faceDetectionProcessor,


                                    onFaceDetectionError =
                                        onFaceDetectionError,
                                    onFaceFrame = { capture ->

                                        processFaceFrame(
                                            capture =
                                                capture,

                                            generation =
                                                requestedGeneration
                                        )
                                    },
                                )
                            )
                        }
                var finalChoice = requestedChoice
                var fellBackToDefault = false
                var fallbackReason: String? = null

                try {
                    cameraProvider.unbindAll()

                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        requestedChoice.selector,
                        preview,
                        analysis
                    )

                } catch (selectionException: Exception) {

                    /*
                     * Some manufacturers expose a physical camera
                     * but reject binding it directly.
                     *
                     * In that situation we fall back rather than
                     * crashing the entire glasses feed.
                     */

                    if (requestedChoice.usesDefaultBackSelector) {
                        throw selectionException
                    }

                    fellBackToDefault = true

                    fallbackReason =
                        selectionException.diagnosticMessage()

                    finalChoice =
                        defaultBackChoice(
                            reason =
                                "Requested camera could not be bound"
                        )

                    cameraProvider.unbindAll()

                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        finalChoice.selector,
                        preview,
                        analysis
                    )
                }

                if (
                    requestedGeneration != bindingGeneration
                ) {
                    cameraProvider.unbindAll()
                    analysis.clearAnalyzer()
                    return@addListener
                }

                currentPreview = preview
                currentAnalysis = analysis

                isBinding = false
                isBound = true

                if (
                    requestedDiagnosticsGeneration ==
                    diagnosticsGeneration
                ) {
                    publishDiagnostics(
                        buildDiagnostics(
                            cameras = selectableCameras,
                            requestedChoice = requestedChoice,
                            finalChoice = finalChoice,
                            fellBackToDefault =
                                fellBackToDefault,
                            fallbackReason =
                                fallbackReason,
                            bindingStatus = "Bound"
                        )
                    )
                }

            } catch (exception: Exception) {

                isBinding = false
                isBound = false

                currentPreview = null

                currentAnalysis?.clearAnalyzer()
                currentAnalysis = null

                if (
                    requestedDiagnosticsGeneration ==
                    diagnosticsGeneration
                ) {
                    publishDiagnostics(
                        "Camera binding failed:\n" +
                                exception.diagnosticMessage()
                    )
                }

                onError(
                    exception.cause?.message
                        ?: exception.message
                        ?: "Unable to start the camera"
                )
            }

        }, mainExecutor)
    }


    private var currentDeviceRotation =
        Surface.ROTATION_0

    private val orientationEventListener =
        object : OrientationEventListener(appContext) {

            override fun onOrientationChanged(
                orientation: Int
            ) {
                if (
                    orientation ==
                    ORIENTATION_UNKNOWN
                ) {
                    return
                }

                val rotation =
                    when (orientation) {
                        in 45..134 ->
                            Surface.ROTATION_270

                        in 135..224 ->
                            Surface.ROTATION_180

                        in 225..314 ->
                            Surface.ROTATION_90

                        else ->
                            Surface.ROTATION_0
                    }

                if (
                    rotation ==
                    currentDeviceRotation
                ) {
                    return
                }

                currentDeviceRotation =
                    rotation

                currentPreview?.targetRotation =
                    rotation

                currentAnalysis?.targetRotation =
                    rotation
            }
        }

    /**
     * Discover the selectable camera lenses without starting
     * the camera.
     */
    fun refreshCameraOptions() {
        if (isClosed) return

        cameraProviderFuture.addListener({

            if (isClosed) return@addListener

            try {
                val cameraProvider =
                    cameraProviderFuture.get()

                publishCameraOptions(
                    discoverSelectableBackCameras(
                        cameraProvider
                    )
                )

            } catch (exception: Exception) {

                Log.e(
                    TAG,
                    "Unable to discover cameras",
                    exception
                )

                publishCameraOptions(emptyList())
            }

        }, mainExecutor)
    }

    fun refreshDiagnostics(
        selectedCameraId: String?
    ) {
        if (isClosed) return

        val requestedGeneration =
            ++diagnosticsGeneration

        cameraProviderFuture.addListener({

            if (
                isClosed ||
                requestedGeneration != diagnosticsGeneration
            ) {
                return@addListener
            }

            try {
                val cameraProvider =
                    cameraProviderFuture.get()

                val cameras =
                    discoverSelectableBackCameras(
                        cameraProvider
                    )

                publishCameraOptions(cameras)

                val choice =
                    chooseCamera(
                        selectedCameraId,
                        cameras
                    )

                publishDiagnostics(
                    buildDiagnostics(
                        cameras = cameras,
                        requestedChoice = choice,
                        finalChoice = choice,
                        fellBackToDefault = false,
                        fallbackReason = null,
                        bindingStatus =
                            "Waiting for camera permission " +
                                    "and external display"
                    )
                )

            } catch (exception: Exception) {

                publishDiagnostics(
                    "Camera discovery failed:\n" +
                            exception.diagnosticMessage()
                )
            }

        }, mainExecutor)
    }

    fun setEdgeEnhancementEnabled(
        enabled: Boolean
    ) {
        edgeEnhancementEnabled.set(enabled)

        currentTarget
            ?.edgeOverlayView
            ?.setEdgeEnhancementEnabled(enabled)
    }

    fun stop() {
        orientationEventListener.disable()
        bindingGeneration++
        diagnosticsGeneration++

        isBinding = false
        isBound = false

        currentPreview = null

        currentAnalysis?.clearAnalyzer()
        currentAnalysis = null

        currentTarget
            ?.edgeOverlayView
            ?.clear()
        ocrRequested.set(
            false
        )

        currentTarget = null
        currentLifecycleOwner = null
        currentSelectedCameraId = null
        lastActivePhysicalCameraId =
            null

        if (cameraProviderFuture.isDone) {
            try {
                cameraProviderFuture
                    .get()
                    .unbindAll()

            } catch (_: Exception) {
                // Nothing to release.
            }
        }
    }

    override fun close() {
        if (isClosed) return

        stop()

        isClosed = true
        textRecognitionProcessor.close()
        faceDetectionProcessor.close()
        faceRecognitionExecutor
            .shutdownNow()
        analysisExecutor.shutdownNow()
    }

    /**
     * Finds the lenses CameraX exposes to us.
     *
     * We first look for physical cameras belonging to logical
     * multi-camera devices. This is where ultra-wide / main /
     * telephoto sensors often live.
     *
     * If Android exposes no physical cameras, we fall back to
     * CameraX's normal top-level camera list.
     */
    private fun discoverSelectableBackCameras(
        cameraProvider: ProcessCameraProvider
    ): List<SelectableCamera> {

        /*
         * Camera2 is now our source of truth for camera topology.
         */
        val logicalBackCameras =
            cameraManager.cameraIdList
                .mapNotNull { cameraId ->

                    val characteristics =
                        runCatching {
                            cameraManager
                                .getCameraCharacteristics(
                                    cameraId
                                )
                        }.getOrNull()
                            ?: return@mapNotNull null

                    val facing =
                        characteristics.get(
                            CameraCharacteristics.LENS_FACING
                        )

                    if (
                        facing !=
                        CameraCharacteristics.LENS_FACING_BACK
                    ) {
                        return@mapNotNull null
                    }

                    LogicalCameraDetails(
                        cameraId = cameraId,
                        characteristics =
                            characteristics
                    )
                }

        /*
         * Prefer a logical multi-camera with the largest number
         * of underlying lenses.
         *
         * On a typical phone this should be the rear camera
         * grouping containing ultra-wide/main/telephoto.
         */
        val logicalMultiCamera =
            logicalBackCameras
                .filter { logical ->

                    if (
                        Build.VERSION.SDK_INT <
                        Build.VERSION_CODES.P
                    ) {
                        false
                    } else {
                        logical.characteristics
                            .physicalCameraIds
                            .isNotEmpty()
                    }
                }
                .maxByOrNull { logical ->

                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.P
                    ) {
                        logical.characteristics
                            .physicalCameraIds
                            .size
                    } else {
                        0
                    }
                }

        if (
            logicalMultiCamera != null &&
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.P
        ) {
            val physicalIds =
                logicalMultiCamera
                    .characteristics
                    .physicalCameraIds

            /*
             * The logical camera characteristics normally describe
             * Android's default active physical camera.
             *
             * Use those optics as our 1.0x reference.
             */
            val logicalReferenceOptics =
                readCameraOptics(
                    logicalMultiCamera.cameraId
                )

            val discovered =
                physicalIds.mapNotNull { physicalId ->

                    val optics =
                        readCameraOptics(
                            physicalId
                        )

                    val relativeZoom =
                        calculateRelativeZoom(
                            reference =
                                logicalReferenceOptics,
                            camera = optics
                        )

                    val option =
                        CameraOption(
                            id =
                                "${logicalMultiCamera.cameraId}:$physicalId",

                            logicalCameraId =
                                logicalMultiCamera.cameraId,

                            physicalCameraId =
                                physicalId,

                            label =
                                cameraLabel(
                                    relativeZoom
                                ),

                            relativeZoomRatio =
                                relativeZoom,

                            focalLengthMm =
                                optics?.focalLengthMm,

                            sensorWidthMm =
                                optics?.sensorWidthMm,

                            horizontalFovDegrees =
                                optics?.horizontalFovDegrees,

                            isPhysicalCamera =
                                true
                        )

                    SelectableCamera(
                        option = option,

                        selector =
                            buildLogicalCameraSelector(
                                logicalCameraId =
                                    logicalMultiCamera.cameraId
                            ),

                        physicalCameraId =
                            physicalId
                    )
                }

            if (discovered.isNotEmpty()) {

                /*
                 * Widest -> narrowest.
                 */
                return discovered
                    .sortedBy {
                        it.option.relativeZoomRatio
                    }
                    .map { camera ->

                        camera.copy(
                            option =
                                camera.option.copy(
                                    label =
                                        classifiedCameraLabel(
                                            camera.option
                                                .relativeZoomRatio
                                        )
                                )
                        )
                    }
            }
        }

        /*
         * Fallback for phones that don't expose a logical
         * multi-camera grouping.
         *
         * Here we enumerate standalone Camera2 rear devices.
         */
        return discoverStandaloneBackCameras(
            cameraProvider =
                cameraProvider,
            logicalBackCameras =
                logicalBackCameras
        )
    }

    @OptIn(
        markerClass = [
            ExperimentalCamera2Interop::class
        ]
    )
    private fun buildLogicalCameraSelector(
        logicalCameraId: String
    ): CameraSelector {

        val logicalCameraFilter =
            CameraFilter { cameraInfos ->

                cameraInfos.filter { cameraInfo ->

                    runCatching {
                        Camera2CameraInfo
                            .from(cameraInfo)
                            .cameraId ==
                                logicalCameraId
                    }.getOrDefault(false)
                }
            }

        return CameraSelector.Builder()
            .requireLensFacing(
                CameraSelector.LENS_FACING_BACK
            )
            .addCameraFilter(
                logicalCameraFilter
            )
            .build()
    }

    @OptIn(
        markerClass = [
            ExperimentalCamera2Interop::class
        ]
    )
    private fun discoverStandaloneBackCameras(
        cameraProvider: ProcessCameraProvider,
        logicalBackCameras:
        List<LogicalCameraDetails>
    ): List<SelectableCamera> {

        if (logicalBackCameras.isEmpty()) {
            return emptyList()
        }

        val referenceCamera =
            logicalBackCameras.first()

        val referenceOptics =
            readCameraOptics(
                referenceCamera.cameraId
            )

        return logicalBackCameras
            .map { camera ->

                val optics =
                    readCameraOptics(
                        camera.cameraId
                    )

                val ratio =
                    calculateRelativeZoom(
                        reference =
                            referenceOptics,
                        camera =
                            optics
                    )

                val cameraFilter =
                    CameraFilter { cameraInfos ->

                        cameraInfos.filter { cameraInfo ->

                            runCatching {
                                Camera2CameraInfo
                                    .from(cameraInfo)
                                    .cameraId ==
                                        camera.cameraId
                            }.getOrDefault(false)
                        }
                    }

                val selector =
                    CameraSelector.Builder()
                        .requireLensFacing(
                            CameraSelector
                                .LENS_FACING_BACK
                        )
                        .addCameraFilter(
                            cameraFilter
                        )
                        .build()

                SelectableCamera(
                    option =
                        CameraOption(
                            id =
                                "camera:${camera.cameraId}",

                            logicalCameraId =
                                camera.cameraId,

                            physicalCameraId =
                                null,

                            label =
                                cameraLabel(ratio),

                            relativeZoomRatio =
                                ratio,

                            focalLengthMm =
                                optics?.focalLengthMm,

                            sensorWidthMm =
                                optics?.sensorWidthMm,

                            horizontalFovDegrees =
                                optics
                                    ?.horizontalFovDegrees,

                            isPhysicalCamera =
                                false
                        ),

                    selector =
                        selector,
                    physicalCameraId = ""
                )
            }
            .sortedBy {
                it.option.relativeZoomRatio
            }
    }

    private fun calculateRelativeZoom(
        reference: CameraOptics?,
        camera: CameraOptics?
    ): Float {
        if (reference == null || camera == null) {
            return 1f
        }

        /*
         * Compare focal length relative to sensor width.
         *
         * focalLength / sensorWidth is effectively the
         * horizontal optical magnification.
         */
        val referenceScale =
            reference.focalLengthMm /
                    reference.sensorWidthMm

        val cameraScale =
            camera.focalLengthMm /
                    camera.sensorWidthMm

        if (
            referenceScale <= 0f ||
            cameraScale <= 0f
        ) {
            return 1f
        }

        return (
                cameraScale /
                        referenceScale
                )
            .takeIf {
                it.isFinite() && it > 0f
            }
            ?: 1f
    }


    private fun readCameraOptics(
        cameraId: String
    ): CameraOptics? {
        return runCatching {
            val characteristics =
                cameraManager.getCameraCharacteristics(cameraId)

            val focalLength =
                characteristics
                    .get(
                        CameraCharacteristics
                            .LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                    )
                    ?.firstOrNull()
                    ?.takeIf {
                        it.isFinite() && it > 0f
                    }
                    ?: return@runCatching null

            val physicalSize =
                characteristics.get(
                    CameraCharacteristics
                        .SENSOR_INFO_PHYSICAL_SIZE
                )
                    ?: return@runCatching null

            val sensorWidth =
                physicalSize.width
                    .takeIf {
                        it.isFinite() && it > 0f
                    }
                    ?: return@runCatching null

            val horizontalFov =
                2.0 * atan(
                    sensorWidth.toDouble() /
                            (2.0 * focalLength.toDouble())
                )

            CameraOptics(
                focalLengthMm = focalLength,
                sensorWidthMm = sensorWidth,
                horizontalFovRadians = horizontalFov
            )
        }.getOrNull()
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun physicalCameraId(
        cameraInfo: CameraInfo
    ): String? {

        /*
         * CameraX physical CameraInfo normally carries a selector
         * containing the physical camera ID.
         */
        val selectorId =
            runCatching {
                cameraInfo
                    .cameraSelector
                    .physicalCameraId
            }.getOrNull()

        if (selectorId != null) {
            return selectorId
        }

        /*
         * Camera2 interop gives us a fallback route on devices
         * where CameraX's selector doesn't expose the ID.
         */
        return runCatching {
            Camera2CameraInfo
                .from(cameraInfo)
                .cameraId
        }.getOrNull()
    }

    /**
     * null selectedCameraId means AUTO.
     */
    private fun chooseCamera(
        selectedCameraId: String?,
        cameras: List<SelectableCamera>
    ): CameraChoice {

        if (selectedCameraId != null) {

            val selectedCamera =
                cameras.firstOrNull {
                    it.option.id ==
                            selectedCameraId
                }

            if (selectedCamera != null) {
                return CameraChoice(
                    selector = selectedCamera.selector,

                    physicalCameraId =
                        selectedCamera.physicalCameraId,

                    description =
                        selectedCamera.option.label,

                    selectionReason =
                        "User selected ${selectedCamera.option.label}",

                    usesDefaultBackSelector =
                        false
                )
            }

            return defaultBackChoice(
                reason =
                    "Previously selected camera " +
                            "is no longer available"
            )
        }

        /*
         * AUTO behaviour:
         *
         * For the low-vision application we prefer the widest
         * available rear lens if there is a genuine < 1x camera.
         */
        val widest =
            cameras.minByOrNull {
                it.option.relativeZoomRatio
            }

        if (
            widest != null
        ) {
            return CameraChoice(
                selector = widest.selector,

                physicalCameraId =
                    widest.physicalCameraId,

                description =
                    "Auto → ${widest.option.label}",

                selectionReason =
                    "Auto selected the widest discovered lens",

                usesDefaultBackSelector =
                    false
            )
        }

        return defaultBackChoice(
            reason =
                "Auto found no ultra-wide lens"
        )
    }

    private fun defaultBackChoice(
        reason: String
    ) =
        CameraChoice(
            selector =
                CameraSelector.DEFAULT_BACK_CAMERA,

            physicalCameraId =
                null,

            description =
                "Default rear camera",

            selectionReason =
                reason,

            usesDefaultBackSelector =
                true
        )

    private fun cameraLabel(
        ratio: Float
    ): String {
        return String.format(
            Locale.US,
            "%.2f×",
            ratio
        )
    }

    private fun classifiedCameraLabel(
        ratio: Float,
    ): String {

        val ratioText =
            String.format(
                Locale.US,
                "%.2f×",
                ratio
            )

        return when {

            /*
             * Optical metadata gives us the strongest clue.
             */
            ratio < 0.85f ->
                "Ultra-wide $ratioText"

            ratio > 1.35f ->
                "Telephoto $ratioText"

            else ->
                "Main $ratioText"
        }
    }

    private fun publishCameraOptions(
        cameras: List<SelectableCamera>
    ) {
        val options =
            cameras.map {
                it.option
            }

        if (options == lastCameraOptions) {
            return
        }

        lastCameraOptions = options

        onCameraOptionsChanged(options)
    }

    private fun buildDiagnostics(
        cameras: List<SelectableCamera>,
        requestedChoice: CameraChoice,
        finalChoice: CameraChoice,
        fellBackToDefault: Boolean,
        fallbackReason: String?,
        bindingStatus: String
    ): String =
        buildString {

            appendLine(
                "Selectable rear cameras: ${cameras.size}"
            )

            if (cameras.isEmpty()) {
                appendLine(
                    "  No explicit rear lenses discovered"
                )
            } else {

                cameras.forEach { camera ->

                    val option =
                        camera.option

                    appendLine(
                        "  ${option.label}"
                    )

                    appendLine(
                        "    Logical ID: " +
                                option.logicalCameraId
                    )

                    appendLine(
                        "    Physical ID: " +
                                (
                                        option.physicalCameraId
                                            ?: "none"
                                        )
                    )

                    appendLine(
                        "    Relative zoom: " +
                                String.format(
                                    Locale.US,
                                    "%.2f×",
                                    option.relativeZoomRatio
                                )
                    )

                    option.focalLengthMm?.let { focalLength ->

                        appendLine(
                            "    Focal length: " +
                                    String.format(
                                        Locale.US,
                                        "%.2f mm",
                                        focalLength
                                    )
                        )
                    }

                    option.sensorWidthMm?.let { sensorWidth ->

                        appendLine(
                            "    Sensor width: " +
                                    String.format(
                                        Locale.US,
                                        "%.2f mm",
                                        sensorWidth
                                    )
                        )
                    }

                    option.horizontalFovDegrees?.let { fov ->

                        appendLine(
                            "    Horizontal FOV: " +
                                    String.format(
                                        Locale.US,
                                        "%.1f°",
                                        fov
                                    )
                        )
                    }
                }
            }

            appendLine(
                "Requested: " +
                        requestedChoice.description
            )

            appendLine(
                "Decision: " +
                        requestedChoice.selectionReason
            )

            appendLine(
                "Final: " +
                        finalChoice.description
            )

            appendLine(
                "Fallback: " +
                        if (fellBackToDefault) {
                            "yes"
                        } else {
                            "no"
                        }
            )

            fallbackReason?.let {
                appendLine(
                    "Fallback reason: $it"
                )
            }

            append(
                "Binding status: $bindingStatus"
            )
        }

    private fun publishDiagnostics(
        diagnostics: String
    ) {
        currentDiagnosticsBase =
            diagnostics

        publishCombinedDiagnostics()
    }

    private fun publishCombinedDiagnostics() {

        val combined =
            buildString {
                append(
                    currentDiagnosticsBase
                )

                appendLine()
                appendLine()

                append(
                    "Actual active physical ID: "
                )

                append(
                    lastActivePhysicalCameraId
                        ?: "unknown"
                )
            }

        Log.d(
            TAG,
            combined
        )

        if (
            combined !=
            lastDiagnostics
        ) {
            lastDiagnostics =
                combined

            onCameraDiagnosticsChanged(
                combined
            )
        }
    }

    private fun updateTargetRotation(
    ) {
        currentPreview?.targetRotation =
            currentDeviceRotation

        currentAnalysis?.targetRotation =
            currentDeviceRotation
    }

    private data class SelectableCamera(
        val option: CameraOption,
        val selector: CameraSelector,
        val physicalCameraId: String?
    )

    private data class CameraChoice(
        val selector: CameraSelector,
        val physicalCameraId: String?,
        val description: String,
        val selectionReason: String,
        val usesDefaultBackSelector: Boolean
    )

    private companion object {
        const val TAG =
            "CameraXController"
        const val ENROLLMENT_SAMPLE_COUNT =
            5
    }
}

private fun Exception.diagnosticMessage(): String =
    cause?.message
        ?: message
        ?: javaClass.simpleName


private data class FaceFrameCapture(
    val result: FaceDetectionResult,
    val frame: Bitmap
)

private class VisionAnalyzer(
    private val overlayView:
    EdgeOverlayView,

    private val edgeEnabled:
    AtomicBoolean,

    private val ocrRequested:
    AtomicBoolean,

    private val ocrInProgress:
    AtomicBoolean,

    private val textRecognitionProcessor:
    TextRecognitionProcessor,

    private val mainExecutor:
    java.util.concurrent.Executor,

    private val onTextRecognized:
        (OcrCapture) -> Unit,

    private val onTextRecognitionError:
        (String) -> Unit,

    private val onEdgeError:
        (String) -> Unit,

    private val faceDetectionEnabled:
    AtomicBoolean,

    private val faceDetectionInProgress:
    AtomicBoolean,

    private val faceDetectionProcessor:
    FaceDetectionProcessor,

    private val onFaceFrame:
        (FaceFrameCapture) -> Unit,

    private val onFaceDetectionError:
        (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private var hasReportedEdgeFailure =
        false

    private var faceFrameCounter =
        0


    override fun analyze(
        image: ImageProxy
    ) {

        val shouldProcessEdges =
            edgeEnabled.get()

        /*
         * Consume exactly one pending
         * OCR request.
         */
        var shouldProcessOcr =
            ocrRequested
                .compareAndSet(
                    true,
                    false
                )


        faceFrameCounter =
            (
                    faceFrameCounter + 1
                    ) %
                    FACE_DETECTION_INTERVAL

        var shouldProcessFaces =
            false

        /*
         * OCR gets priority because it is a
         * deliberate user action.
         *
         * We don't start two asynchronous ML Kit
         * operations against the same ImageProxy.
         */
        if (
            !shouldProcessOcr &&
            faceDetectionEnabled.get() &&
            faceFrameCounter == 0
        ) {

            shouldProcessFaces =
                faceDetectionInProgress
                    .compareAndSet(
                        false,
                        true
                    )
        }

        /*
         * If an OCR task is already running,
         * put the request back for a later
         * frame rather than running two
         * recognizers concurrently.
         */
        if (
            shouldProcessOcr &&
            !ocrInProgress
                .compareAndSet(
                    false,
                    true
                )
        ) {
            ocrRequested.set(
                true
            )

            shouldProcessOcr =
                false
        }

        if (
            !shouldProcessEdges &&
            !shouldProcessOcr &&
            !shouldProcessFaces
        ) {
            image.close()
            return
        }

        /*
         * Edge processing is synchronous,
         * so it can safely happen before
         * ML Kit takes ownership of this
         * frame.
         */
        if (
            shouldProcessEdges
        ) {
            try {
                processEdges(
                    image
                )
            } catch (
                exception: Exception
            ) {

                if (
                    !hasReportedEdgeFailure
                ) {
                    hasReportedEdgeFailure =
                        true

                    mainExecutor.execute {
                        onEdgeError(
                            exception.message
                                ?: "Edge enhancement failed"
                        )
                    }
                }
            }
        }

        /*
         * ML Kit is asynchronous.
         *
         * TextRecognitionProcessor will
         * close the ImageProxy when the
         * task finishes.
         */
        if (
            shouldProcessOcr
        ) {

            val frozenFrame =
                try {
                    createFrozenFrame(
                        image
                    )
                } catch (
                    exception: Exception
                ) {

                    ocrInProgress.set(
                        false
                    )

                    image.close()

                    mainExecutor.execute {
                        onTextRecognitionError(
                            exception.message
                                ?: "Unable to capture OCR frame"
                        )
                    }

                    return
                }

            textRecognitionProcessor.process(
                imageProxy =
                    image,

                callbackExecutor =
                    mainExecutor,

                onResult = { result ->

                    onTextRecognized(
                        OcrCapture(
                            result =
                                result,

                            frozenFrame =
                                frozenFrame
                        )
                    )
                },

                onError = { message ->

                    /*
                     * Nobody needs this bitmap
                     * if OCR itself failed.
                     */
                    if (
                        !frozenFrame.isRecycled
                    ) {
                        frozenFrame.recycle()
                    }

                    onTextRecognitionError(
                        message
                    )
                },

                onComplete = {
                    ocrInProgress.set(
                        false
                    )
                }
            )

            return
        }

        if (
            shouldProcessFaces
        ) {

            val faceFrame =
                try {
                    createFrozenFrame(
                        image
                    )
                } catch (
                    exception: Exception
                ) {

                    faceDetectionInProgress
                        .set(
                            false
                        )

                    image.close()

                    mainExecutor.execute {
                        onFaceDetectionError(
                            exception.message
                                ?: "Unable to capture face frame"
                        )
                    }

                    return
                }

            faceDetectionProcessor.process(
                imageProxy =
                    image,

                callbackExecutor =
                    mainExecutor,

                onResult = { result ->

                    onFaceFrame(
                        FaceFrameCapture(
                            result =
                                result,

                            frame =
                                faceFrame
                        )
                    )
                },

                onError = { message ->

                    if (
                        !faceFrame
                            .isRecycled
                    ) {
                        faceFrame.recycle()
                    }

                    faceDetectionInProgress
                        .set(
                            false
                        )

                    onFaceDetectionError(
                        message
                    )
                },

                /*
                 * Don't clear faceDetectionInProgress
                 * here anymore.
                 *
                 * Recognition still has work to do.
                 */
                onComplete = {}
            )

            return
        }

        /*
         * No asynchronous consumer owns
         * this frame.
         */
        image.close()
    }

    private fun processEdges(
        image: ImageProxy
    ) {
        val crop =
            image.cropRect

        val sampleStep =
            ceil(
                crop.width() /
                        MAX_OUTPUT_WIDTH
                            .toFloat()
            )
                .toInt()
                .coerceAtLeast(
                    1
                )

        val outputWidth =
            (
                    crop.width() /
                            sampleStep
                    )
                .coerceAtLeast(
                    1
                )

        val outputHeight =
            (
                    crop.height() /
                            sampleStep
                    )
                .coerceAtLeast(
                    1
                )

        val plane =
            image.planes.first()

        val buffer =
            plane.buffer

        val bufferStart =
            buffer.position()

        val luminance =
            IntArray(
                outputWidth *
                        outputHeight
            )

        for (
        outputY in
        0 until outputHeight
        ) {
            val sourceY =
                crop.top +
                        outputY *
                        sampleStep

            val rowOffset =
                sourceY *
                        plane.rowStride

            for (
            outputX in
            0 until outputWidth
            ) {
                val sourceX =
                    crop.left +
                            outputX *
                            sampleStep

                val sourceIndex =
                    bufferStart +
                            rowOffset +
                            sourceX *
                            plane.pixelStride

                luminance[
                    outputY *
                            outputWidth +
                            outputX
                ] =
                    buffer.get(
                        sourceIndex
                    )
                        .toInt() and
                            0xFF
            }
        }

        val edgePixels =
            SobelEdgeDetector.detect(
                luminance,
                outputWidth,
                outputHeight
            )

        val unrotatedBitmap =
            Bitmap.createBitmap(
                edgePixels,
                outputWidth,
                outputHeight,
                Bitmap.Config.ARGB_8888
            )

        val outputBitmap =
            rotate(
                unrotatedBitmap,
                image
                    .imageInfo
                    .rotationDegrees
            )

        overlayView.submit(
            outputBitmap
        )
    }

    private fun rotate(
        bitmap: Bitmap,
        rotationDegrees: Int
    ): Bitmap {

        if (
            rotationDegrees == 0
        ) {
            return bitmap
        }

        val matrix =
            Matrix().apply {
                postRotate(
                    rotationDegrees
                        .toFloat()
                )
            }

        val rotated =
            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )

        if (
            rotated !== bitmap
        ) {
            bitmap.recycle()
        }

        return rotated
    }

    private fun createFrozenFrame(
        image: ImageProxy
    ): Bitmap {

        val bitmap =
            image.toBitmap()

        return rotate(
            bitmap,
            image.imageInfo
                .rotationDegrees
        )
    }

    private companion object {
        const val MAX_OUTPUT_WIDTH =
            320
        const val FACE_DETECTION_INTERVAL =
            3
    }
}