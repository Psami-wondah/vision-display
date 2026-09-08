package com.psami.visiondisplay.ui

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class FaceEmbeddingProcessor(
    context: Context
) : AutoCloseable {

    private val modelBuffer:
            ByteBuffer

    private val interpreter:
            Interpreter

    init {
        val modelBytes =
            context.assets
                .open(MODEL_FILE)
                .use {
                    it.readBytes()
                }

        modelBuffer =
            ByteBuffer
                .allocateDirect(
                    modelBytes.size
                )
                .order(
                    ByteOrder.nativeOrder()
                )
                .apply {
                    put(
                        modelBytes
                    )

                    rewind()
                }

        interpreter =
            Interpreter(
                modelBuffer,
                Interpreter.Options()
                    .apply {
                        setNumThreads(
                            4
                        )
                    }
            )

        validateModel()
    }

    @Synchronized
    fun embed(
        faceBitmap: Bitmap
    ): FloatArray {

        val scaled =
            Bitmap.createScaledBitmap(
                faceBitmap,
                INPUT_SIZE,
                INPUT_SIZE,
                true
            )

        try {
            val pixels =
                IntArray(
                    INPUT_SIZE *
                            INPUT_SIZE
                )

            scaled.getPixels(
                pixels,
                0,
                INPUT_SIZE,
                0,
                0,
                INPUT_SIZE,
                INPUT_SIZE
            )

            val input =
                ByteBuffer
                    .allocateDirect(
                        INPUT_SIZE *
                                INPUT_SIZE *
                                3 *
                                Float.SIZE_BYTES
                    )
                    .order(
                        ByteOrder.nativeOrder()
                    )

            pixels.forEach {
                    pixel ->

                val red =
                    (
                            pixel shr 16
                            ) and 0xFF

                val green =
                    (
                            pixel shr 8
                            ) and 0xFF

                val blue =
                    pixel and 0xFF

                input.putFloat(
                    red / 128f -
                            1f
                )

                input.putFloat(
                    green / 128f -
                            1f
                )

                input.putFloat(
                    blue / 128f -
                            1f
                )
            }

            input.rewind()

            val output =
                Array(1) {
                    FloatArray(
                        EMBEDDING_SIZE
                    )
                }

            interpreter.run(
                input,
                output
            )

            return normalise(
                output[0]
            )

        } finally {
            if (
                scaled !==
                faceBitmap
            ) {
                scaled.recycle()
            }
        }
    }

    private fun validateModel() {

        val inputShape =
            interpreter
                .getInputTensor(0)
                .shape()

        val outputShape =
            interpreter
                .getOutputTensor(0)
                .shape()

        require(
            inputShape.contentEquals(
                intArrayOf(
                    1,
                    INPUT_SIZE,
                    INPUT_SIZE,
                    3
                )
            )
        ) {
            "Unexpected face model input: " +
                    inputShape.contentToString()
        }

        require(
            outputShape.last() ==
                    EMBEDDING_SIZE
        ) {
            "Unexpected face model output: " +
                    outputShape.contentToString()
        }
    }

    private fun normalise(
        embedding: FloatArray
    ): FloatArray {

        var sum =
            0.0

        embedding.forEach {
                value ->

            sum +=
                value *
                        value
        }

        val magnitude =
            sqrt(sum)
                .toFloat()

        if (
            magnitude <=
            1e-6f
        ) {
            return embedding
        }

        return FloatArray(
            embedding.size
        ) {
                index ->

            embedding[index] /
                    magnitude
        }
    }

    override fun close() {
        interpreter.close()
    }

    private companion object {
        const val MODEL_FILE =
            "mobile_face_net.tflite"

        const val INPUT_SIZE =
            112

        const val EMBEDDING_SIZE =
            192
    }
}