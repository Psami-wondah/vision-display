package com.psami.visiondisplay.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import android.view.Surface
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil

class CameraXController(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)
    private val edgeEnhancementEnabled = AtomicBoolean(false)

    private var bindingGeneration = 0
    private var currentTarget: GlassesRenderTarget? = null
    private var currentLifecycleOwner: LifecycleOwner? = null
    private var currentPreview: Preview? = null
    private var currentAnalysis: ImageAnalysis? = null
    private var isBinding = false
    private var isBound = false
    private var isClosed = false

    fun start(
        lifecycleOwner: LifecycleOwner,
        target: GlassesRenderTarget,
        edgeEnhancementEnabled: Boolean,
        onError: (String) -> Unit
    ) {
        check(!isClosed) { "CameraXController has already been closed" }
        setEdgeEnhancementEnabled(edgeEnhancementEnabled)

        val isSameTarget = currentTarget?.previewView === target.previewView
        if (isSameTarget && currentLifecycleOwner === lifecycleOwner && (isBinding || isBound)) {
            updateTargetRotation(target)
            return
        }

        stop()
        currentTarget = target
        currentLifecycleOwner = lifecycleOwner
        isBinding = true
        val requestedGeneration = ++bindingGeneration

        cameraProviderFuture.addListener({
            if (isClosed || requestedGeneration != bindingGeneration) return@addListener

            try {
                val cameraProvider = cameraProviderFuture.get()
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

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selectCamera(cameraProvider),
                    preview,
                    analysis
                )

                if (requestedGeneration != bindingGeneration) {
                    cameraProvider.unbindAll()
                    analysis.clearAnalyzer()
                    return@addListener
                }

                currentPreview = preview
                currentAnalysis = analysis
                isBinding = false
                isBound = true
            } catch (exception: Exception) {
                isBinding = false
                isBound = false
                currentPreview = null
                currentAnalysis?.clearAnalyzer()
                currentAnalysis = null
                onError(
                    exception.cause?.message
                        ?: exception.message
                        ?: "Unable to start the camera"
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
        isBinding = false
        isBound = false
        currentPreview = null
        currentAnalysis?.clearAnalyzer()
        currentAnalysis = null
        currentTarget?.edgeOverlayView?.clear()
        currentTarget = null
        currentLifecycleOwner = null

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

    private fun selectCamera(cameraProvider: ProcessCameraProvider): CameraSelector {
        val ultraWideSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .addCameraFilter { cameraInfos ->
                cameraInfos
                    .minByOrNull(CameraInfo::getIntrinsicZoomRatio)
                    ?.takeIf { it.intrinsicZoomRatio < 1f }
                    ?.let(::listOf)
                    .orEmpty()
            }
            .build()

        return try {
            if (cameraProvider.hasCamera(ultraWideSelector)) {
                ultraWideSelector
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
        } catch (_: Exception) {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    private fun updateTargetRotation(target: GlassesRenderTarget) {
        val rotation = target.previewView.display?.rotation ?: return
        currentPreview?.targetRotation = rotation
        currentAnalysis?.targetRotation = rotation
    }
}

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
