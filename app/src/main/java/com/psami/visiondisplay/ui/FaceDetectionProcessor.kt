package com.psami.visiondisplay.ui

import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executor

class FaceDetectionProcessor :
    AutoCloseable {

    /*
     * FAST + tracking is appropriate for
     * our live glasses feed.
     *
     * We deliberately leave landmarks,
     * contours and classifications off.
     * Recognition doesn't need them yet
     * and they cost additional processing.
     */
    private val detector =
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(
                    FaceDetectorOptions
                        .PERFORMANCE_MODE_FAST
                )
                .setLandmarkMode(
                    FaceDetectorOptions
                        .LANDMARK_MODE_NONE
                )
                .setContourMode(
                    FaceDetectorOptions
                        .CONTOUR_MODE_NONE
                )
                .setClassificationMode(
                    FaceDetectorOptions
                        .CLASSIFICATION_MODE_NONE
                )
                .setMinFaceSize(
                    0.12f
                )
                .enableTracking()
                .build()
        )

    @OptIn(
        ExperimentalGetImage::class
    )
    fun process(
        imageProxy: ImageProxy,
        callbackExecutor: Executor,
        onResult:
            (FaceDetectionResult) -> Unit,
        onError:
            (String) -> Unit,
        onComplete:
            () -> Unit
    ) {

        val mediaImage =
            imageProxy.image

        if (
            mediaImage == null
        ) {
            imageProxy.close()

            callbackExecutor.execute {
                onError(
                    "Camera frame was unavailable"
                )

                onComplete()
            }

            return
        }

        val rotationDegrees =
            imageProxy
                .imageInfo
                .rotationDegrees

        val sourceWidth =
            if (
                rotationDegrees == 90 ||
                rotationDegrees == 270
            ) {
                imageProxy.height
            } else {
                imageProxy.width
            }

        val sourceHeight =
            if (
                rotationDegrees == 90 ||
                rotationDegrees == 270
            ) {
                imageProxy.width
            } else {
                imageProxy.height
            }

        val inputImage =
            InputImage.fromMediaImage(
                mediaImage,
                rotationDegrees
            )

        try {

            detector
                .process(
                    inputImage
                )
                .addOnSuccessListener(
                    callbackExecutor
                ) {
                        faces ->

                    val regions =
                        faces.map {
                                face ->

                            DetectedFaceRegion(
                                trackingId =
                                    face.trackingId,

                                boundingBox =
                                    Rect(
                                        face.boundingBox
                                    ),

                                eulerX =
                                    face.headEulerAngleX,

                                eulerY =
                                    face.headEulerAngleY,

                                eulerZ =
                                    face.headEulerAngleZ
                            )
                        }

                    onResult(
                        FaceDetectionResult(
                            faces =
                                regions,

                            sourceWidth =
                                sourceWidth,

                            sourceHeight =
                                sourceHeight
                        )
                    )
                }
                .addOnFailureListener(
                    callbackExecutor
                ) {
                        exception ->

                    onError(
                        exception.message
                            ?: "Face detection failed"
                    )
                }
                .addOnCompleteListener(
                    callbackExecutor
                ) {

                    imageProxy.close()

                    onComplete()
                }

        } catch (
            exception: Exception
        ) {

            imageProxy.close()

            callbackExecutor.execute {

                onError(
                    exception.message
                        ?: "Face detection failed"
                )

                onComplete()
            }
        }
    }

    override fun close() {
        detector.close()
    }
}