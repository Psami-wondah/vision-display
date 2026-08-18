package com.psami.visiondisplay.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.Camera
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
import com.psami.visiondisplay.data.CameraMode
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil

class CameraXController(
    context: Context,
    private val onCameraDiagnosticsChanged: (String) -> Unit
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)
    private val edgeEnhancementEnabled = AtomicBoolean(false)

    private var bindingGeneration = 0
    private var diagnosticsGeneration = 0
    private var currentTarget: GlassesRenderTarget? = null
    private var currentLifecycleOwner: LifecycleOwner? = null
    private var currentCameraMode: CameraMode? = null
    private var currentPreview: Preview? = null
    private var currentAnalysis: ImageAnalysis? = null
    private var lastDiagnostics: String? = null
    private var isBinding = false
    private var isBound = false
    private var isClosed = false

    fun start(
        lifecycleOwner: LifecycleOwner,
        target: GlassesRenderTarget,
        edgeEnhancementEnabled: Boolean,
        cameraMode: CameraMode,
        onError: (String) -> Unit
    ) {
        check(!isClosed) { "CameraXController has already been closed" }
        setEdgeEnhancementEnabled(edgeEnhancementEnabled)

        val isSameTarget = currentTarget?.previewView === target.previewView
        if (
            isSameTarget &&
            currentLifecycleOwner === lifecycleOwner &&
            currentCameraMode == cameraMode &&
            (isBinding || isBound)
        ) {
            updateTargetRotation(target)
            return
        }

        stop()
        currentTarget = target
        currentLifecycleOwner = lifecycleOwner
        currentCameraMode = cameraMode
        target.edgeOverlayView.setEdgeEnhancementEnabled(edgeEnhancementEnabled)
        isBinding = true
        val requestedGeneration = ++bindingGeneration
        val requestedDiagnosticsGeneration = ++diagnosticsGeneration

        cameraProviderFuture.addListener({
            if (isClosed || requestedGeneration != bindingGeneration) return@addListener
            var backCameras = emptyList<BackCameraDetails>()

            try {
                val cameraProvider = cameraProviderFuture.get()
                backCameras = collectBackCameraDetails(cameraProvider)
                val requestedChoice = chooseCamera(cameraMode, backCameras)
                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(
                        AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                    )
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 360),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    )
                    .build()
                val targetRotation = target.previewView.display?.rotation ?: Surface.ROTATION_0
                val preview = Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setTargetRotation(targetRotation)
                    .build()
                    .also { it.surfaceProvider = target.previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(resolutionSelector)
                    .setTargetRotation(targetRotation)
                    .build()
                    .also {
                        it.setAnalyzer(
                            analysisExecutor,
                            EdgeAnalyzer(
                                overlayView = target.edgeOverlayView,
                                enabled = this.edgeEnhancementEnabled,
                                mainExecutor = mainExecutor,
                                onError = { message ->
                                    if (!isClosed && requestedGeneration == bindingGeneration) {
                                        onError(message)
                                    }
                                }
                            )
                        )
                    }

                var finalChoice = requestedChoice
                var fellBackToDefault = requestedChoice.fellBackToDefault
                var fallbackReason = requestedChoice.fallbackReason
                var boundCamera: Camera

                try {
                    cameraProvider.unbindAll()
                    boundCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        requestedChoice.selector,
                        preview,
                        analysis
                    )
                } catch (selectionException: Exception) {
                    if (requestedChoice.usesDefaultBackSelector) throw selectionException

                    finalChoice = defaultBackChoice(
                        backCameras = backCameras,
                        selectionReason = "Preferred selector could not be bound; using default back",
                        fellBackToDefault = true,
                        fallbackReason = selectionException.diagnosticMessage()
                    )
                    fellBackToDefault = true
                    fallbackReason = finalChoice.fallbackReason
                    cameraProvider.unbindAll()
                    boundCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        finalChoice.selector,
                        preview,
                        analysis
                    )
                }

                if (requestedGeneration != bindingGeneration) {
                    cameraProvider.unbindAll()
                    analysis.clearAnalyzer()
                    return@addListener
                }

                currentPreview = preview
                currentAnalysis = analysis
                isBinding = false
                isBound = true

                if (requestedDiagnosticsGeneration == diagnosticsGeneration) {
                    publishDiagnostics(
                        buildDiagnostics(
                            cameraMode = cameraMode,
                            backCameras = collectBackCameraDetails(cameraProvider),
                            requestedChoice = requestedChoice,
                            finalSelector = finalChoice.selectorDescription,
                            fellBackToDefault = fellBackToDefault,
                            fallbackReason = fallbackReason,
                            bindingStatus = "Bound (${describeBoundCamera(boundCamera, backCameras)})"
                        )
                    )
                }
            } catch (exception: Exception) {
                isBinding = false
                isBound = false
                currentPreview = null
                currentAnalysis?.clearAnalyzer()
                currentAnalysis = null
                if (requestedDiagnosticsGeneration == diagnosticsGeneration) {
                    publishDiagnostics(
                        buildFailureDiagnostics(
                            cameraMode = cameraMode,
                            backCameras = backCameras,
                            message = exception.diagnosticMessage()
                        )
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

    fun refreshDiagnostics(cameraMode: CameraMode) {
        if (isClosed) return
        val requestedDiagnosticsGeneration = ++diagnosticsGeneration
        cameraProviderFuture.addListener({
            if (isClosed || requestedDiagnosticsGeneration != diagnosticsGeneration) {
                return@addListener
            }

            try {
                val cameraProvider = cameraProviderFuture.get()
                val backCameras = collectBackCameraDetails(cameraProvider)
                val requestedChoice = chooseCamera(cameraMode, backCameras)
                publishDiagnostics(
                    buildDiagnostics(
                        cameraMode = cameraMode,
                        backCameras = backCameras,
                        requestedChoice = requestedChoice,
                        finalSelector = "Not bound; planned ${requestedChoice.selectorDescription}",
                        fellBackToDefault = requestedChoice.fellBackToDefault,
                        fallbackReason = requestedChoice.fallbackReason,
                        bindingStatus = "Waiting for camera permission and an external display"
                    )
                )
            } catch (exception: Exception) {
                publishDiagnostics(
                    buildFailureDiagnostics(
                        cameraMode = cameraMode,
                        backCameras = emptyList(),
                        message = exception.diagnosticMessage()
                    )
                )
            }
        }, mainExecutor)
    }

    fun setEdgeEnhancementEnabled(enabled: Boolean) {
        edgeEnhancementEnabled.set(enabled)
        currentTarget?.edgeOverlayView?.setEdgeEnhancementEnabled(enabled)
    }

    fun stop() {
        bindingGeneration++
        diagnosticsGeneration++
        isBinding = false
        isBound = false
        currentPreview = null
        currentAnalysis?.clearAnalyzer()
        currentAnalysis = null
        currentTarget?.edgeOverlayView?.clear()
        currentTarget = null
        currentLifecycleOwner = null
        currentCameraMode = null

        if (cameraProviderFuture.isDone) {
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
                // A failed provider has no bound use cases to release.
            }
        }
    }

    override fun close() {
        if (isClosed) return
        stop()
        isClosed = true
        analysisExecutor.shutdownNow()
    }

    private fun collectBackCameraDetails(
        cameraProvider: ProcessCameraProvider
    ): List<BackCameraDetails> {
        var backCameraIndex = 0
        return cameraProvider.availableCameraInfos.mapIndexedNotNull { availableIndex, cameraInfo ->
            val isBackCamera = runCatching {
                cameraInfo.lensFacing == CameraSelector.LENS_FACING_BACK
            }.getOrDefault(false)
            if (!isBackCamera) return@mapIndexedNotNull null

            val zoomState = runCatching { cameraInfo.zoomState.value }.getOrNull()
            BackCameraDetails(
                backCameraIndex = backCameraIndex++,
                availableCameraIndex = availableIndex,
                cameraInfo = cameraInfo,
                intrinsicZoomRatio = runCatching { cameraInfo.intrinsicZoomRatio }
                    .getOrNull()
                    ?.takeIf { it.isFinite() && it > 0f },
                minZoomRatio = zoomState?.minZoomRatio,
                maxZoomRatio = zoomState?.maxZoomRatio
            )
        }
    }

    private fun chooseCamera(
        cameraMode: CameraMode,
        backCameras: List<BackCameraDetails>
    ): CameraChoice {
        val widestCamera = backCameras
            .filter { it.intrinsicZoomRatio != null }
            .minByOrNull { it.intrinsicZoomRatio!! }

        return when (cameraMode) {
            CameraMode.DEFAULT_BACK -> defaultBackChoice(
                backCameras = backCameras,
                selectionReason = "DEFAULT_BACK always requests CameraSelector.DEFAULT_BACK_CAMERA"
            )

            CameraMode.WIDEST_BACK -> {
                if (widestCamera == null) {
                    defaultBackChoice(
                        backCameras = backCameras,
                        selectionReason = "WIDEST_BACK found no usable intrinsic zoom data",
                        fellBackToDefault = true,
                        fallbackReason = "No back camera exposed a usable intrinsicZoomRatio"
                    )
                } else {
                    exactCameraChoice(
                        camera = widestCamera,
                        selectionReason = "WIDEST_BACK chose the lowest intrinsicZoomRatio"
                    )
                }
            }

            CameraMode.AUTO -> {
                if (widestCamera?.intrinsicZoomRatio?.let { it < ULTRA_WIDE_THRESHOLD } == true) {
                    exactCameraChoice(
                        camera = widestCamera,
                        selectionReason = "AUTO found intrinsicZoomRatio < 1.00"
                    )
                } else {
                    defaultBackChoice(
                        backCameras = backCameras,
                        selectionReason = "AUTO found no intrinsicZoomRatio < 1.00"
                    )
                }
            }
        }
    }

    private fun exactCameraChoice(
        camera: BackCameraDetails,
        selectionReason: String
    ): CameraChoice = try {
        CameraChoice(
            selector = camera.cameraInfo.cameraSelector,
            selectorDescription = "CameraInfo selector for ${camera.shortDescription()}",
            selectionReason = selectionReason,
            usesDefaultBackSelector = false
        )
    } catch (exception: Exception) {
        defaultBackChoice(
            backCameras = listOf(camera),
            selectionReason = "$selectionReason, but its CameraInfo selector was unavailable",
            fellBackToDefault = true,
            fallbackReason = exception.diagnosticMessage()
        )
    }

    private fun defaultBackChoice(
        backCameras: List<BackCameraDetails>,
        selectionReason: String,
        fellBackToDefault: Boolean = false,
        fallbackReason: String? = null
    ): CameraChoice {
        val defaultCameraInfo = runCatching {
            CameraSelector.DEFAULT_BACK_CAMERA
                .filter(backCameras.map(BackCameraDetails::cameraInfo))
                .firstOrNull()
        }.getOrNull()
        val defaultCamera = backCameras.firstOrNull { it.cameraInfo == defaultCameraInfo }
        val targetDescription = defaultCamera?.shortDescription() ?: "CameraX default back camera"

        return CameraChoice(
            selector = CameraSelector.DEFAULT_BACK_CAMERA,
            selectorDescription = "CameraSelector.DEFAULT_BACK_CAMERA -> $targetDescription",
            selectionReason = selectionReason,
            usesDefaultBackSelector = true,
            fellBackToDefault = fellBackToDefault,
            fallbackReason = fallbackReason
        )
    }

    private fun buildDiagnostics(
        cameraMode: CameraMode,
        backCameras: List<BackCameraDetails>,
        requestedChoice: CameraChoice,
        finalSelector: String,
        fellBackToDefault: Boolean,
        fallbackReason: String?,
        bindingStatus: String
    ): String = buildString {
        appendLine("Selected camera mode: ${cameraMode.name}")
        appendBackCameraDiagnostics(backCameras)
        appendLine("Selection decision: ${requestedChoice.selectionReason}")
        appendLine("Final camera selector: $finalSelector")
        appendLine("Fell back to default back camera: ${if (fellBackToDefault) "yes" else "no"}")
        fallbackReason?.let { appendLine("Fallback reason: $it") }
        append("Binding status: $bindingStatus")
    }

    private fun buildFailureDiagnostics(
        cameraMode: CameraMode,
        backCameras: List<BackCameraDetails>,
        message: String
    ): String =
        buildString {
            appendLine("Selected camera mode: ${cameraMode.name}")
            appendBackCameraDiagnostics(backCameras)
            appendLine("CameraX diagnostics/binding failed: $message")
            appendLine("Final camera selector: unavailable")
            append("Fell back to default back camera: unknown")
        }

    private fun StringBuilder.appendBackCameraDiagnostics(
        backCameras: List<BackCameraDetails>
    ) {
        appendLine("CameraX back cameras: ${backCameras.size}")
        if (backCameras.isEmpty()) {
            appendLine("  None detected")
            return
        }

        backCameras.forEach { camera ->
            appendLine(
                "  ${camera.shortDescription()}: " +
                    "intrinsic=${camera.intrinsicZoomRatio.diagnosticValue()}, " +
                    "zoom=${camera.zoomRangeDescription()}, " +
                    "app class=${camera.appClassification()}"
            )
        }
    }

    private fun describeBoundCamera(
        camera: Camera,
        backCameras: List<BackCameraDetails>
    ): String = backCameras
        .firstOrNull { it.cameraInfo == camera.cameraInfo }
        ?.shortDescription()
        ?: "CameraX-reported camera"

    private fun publishDiagnostics(diagnostics: String) {
        Log.d(TAG, diagnostics)
        if (diagnostics != lastDiagnostics) {
            lastDiagnostics = diagnostics
            onCameraDiagnosticsChanged(diagnostics)
        }
    }

    private fun updateTargetRotation(target: GlassesRenderTarget) {
        val rotation = target.previewView.display?.rotation ?: return
        currentPreview?.targetRotation = rotation
        currentAnalysis?.targetRotation = rotation
    }

    private data class BackCameraDetails(
        val backCameraIndex: Int,
        val availableCameraIndex: Int,
        val cameraInfo: CameraInfo,
        val intrinsicZoomRatio: Float?,
        val minZoomRatio: Float?,
        val maxZoomRatio: Float?
    ) {
        fun shortDescription(): String =
            "Back[$backCameraIndex] (CameraX order $availableCameraIndex)"

        fun zoomRangeDescription(): String = if (minZoomRatio != null && maxZoomRatio != null) {
            "${minZoomRatio.diagnosticValue()}..${maxZoomRatio.diagnosticValue()}"
        } else {
            "unavailable"
        }

        fun appClassification(): String = when {
            intrinsicZoomRatio == null -> "UNKNOWN (not treated as ultra-wide)"
            intrinsicZoomRatio < ULTRA_WIDE_THRESHOLD -> "ULTRA-WIDE"
            intrinsicZoomRatio > 1f -> "TELEPHOTO (not ultra-wide)"
            else -> "WIDE/DEFAULT"
        }
    }

    private data class CameraChoice(
        val selector: CameraSelector,
        val selectorDescription: String,
        val selectionReason: String,
        val usesDefaultBackSelector: Boolean,
        val fellBackToDefault: Boolean = false,
        val fallbackReason: String? = null
    )

    private companion object {
        const val TAG = "CameraXController"
        const val ULTRA_WIDE_THRESHOLD = 1f
    }
}

private fun Float?.diagnosticValue(): String =
    this?.let { String.format(Locale.US, "%.2f", it) } ?: "unavailable"

private fun Exception.diagnosticMessage(): String =
    cause?.message ?: message ?: javaClass.simpleName

private class EdgeAnalyzer(
    private val overlayView: EdgeOverlayView,
    private val enabled: AtomicBoolean,
    private val mainExecutor: java.util.concurrent.Executor,
    private val onError: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private var hasReportedFailure = false

    override fun analyze(image: ImageProxy) {
        if (!enabled.get()) {
            image.close()
            return
        }

        try {
            val crop = image.cropRect
            val sampleStep = ceil(crop.width() / MAX_OUTPUT_WIDTH.toFloat()).toInt().coerceAtLeast(1)
            val outputWidth = (crop.width() / sampleStep).coerceAtLeast(1)
            val outputHeight = (crop.height() / sampleStep).coerceAtLeast(1)
            val plane = image.planes.first()
            val buffer = plane.buffer
            val bufferStart = buffer.position()
            val luminance = IntArray(outputWidth * outputHeight)

            for (outputY in 0 until outputHeight) {
                val sourceY = crop.top + outputY * sampleStep
                val rowOffset = sourceY * plane.rowStride
                for (outputX in 0 until outputWidth) {
                    val sourceX = crop.left + outputX * sampleStep
                    val sourceIndex = bufferStart + rowOffset + sourceX * plane.pixelStride
                    luminance[outputY * outputWidth + outputX] =
                        buffer.get(sourceIndex).toInt() and 0xFF
                }
            }

            val edgePixels = SobelEdgeDetector.detect(luminance, outputWidth, outputHeight)
            val unrotatedBitmap = Bitmap.createBitmap(
                edgePixels,
                outputWidth,
                outputHeight,
                Bitmap.Config.ARGB_8888
            )
            val outputBitmap = rotate(unrotatedBitmap, image.imageInfo.rotationDegrees)
            overlayView.submit(outputBitmap)
        } catch (exception: Exception) {
            if (!hasReportedFailure) {
                hasReportedFailure = true
                mainExecutor.execute {
                    onError(exception.message ?: "Edge enhancement failed")
                }
            }
        } finally {
            image.close()
        }
    }

    private fun rotate(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private companion object {
        const val MAX_OUTPUT_WIDTH = 320
    }
}
