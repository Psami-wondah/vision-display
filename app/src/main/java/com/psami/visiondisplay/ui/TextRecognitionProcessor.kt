package com.psami.visiondisplay.ui

import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor

class TextRecognitionProcessor :
    AutoCloseable {

    private val recognizer =
        TextRecognition.getClient(
            TextRecognizerOptions
                .DEFAULT_OPTIONS
        )

    @OptIn(ExperimentalGetImage::class)
    fun process(
        imageProxy: ImageProxy,
        callbackExecutor: Executor,
        onResult: (OcrResult) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        val mediaImage =
            imageProxy.image

        if (mediaImage == null) {
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

        /*
         * ML Kit receives the rotation metadata,
         * so its recognised coordinates correspond
         * to the correctly oriented image.
         */
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
            recognizer
                .process(
                    inputImage
                )
                .addOnSuccessListener(
                    callbackExecutor
                ) { result ->

                    var nextRegionId =
                        0

                    val regions =
                        result.textBlocks
                            .flatMap {
                                    block ->

                                block.lines
                            }
                            .mapNotNull {
                                    line ->

                                val text =
                                    line.text
                                        .trim()

                                val box =
                                    line.boundingBox

                                if (
                                    text.isBlank() ||
                                    box == null
                                ) {
                                    null
                                } else {
                                    OcrTextRegion(
                                        id =
                                            nextRegionId++,

                                        text =
                                            text,

                                        /*
                                         * Own our copy instead
                                         * of retaining ML Kit's
                                         * Rect instance.
                                         */
                                        boundingBox =
                                            Rect(box)
                                    )
                                }
                            }

                    onResult(
                        OcrResult(
                            fullText =
                                result.text
                                    .trim(),

                            regions =
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
                            ?: "Text recognition failed"
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
                        ?: "Text recognition failed"
                )

                onComplete()
            }
        }
    }

    override fun close() {
        recognizer.close()
    }
}