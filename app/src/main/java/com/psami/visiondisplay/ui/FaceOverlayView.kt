package com.psami.visiondisplay.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import com.psami.visiondisplay.data.ViewportShape
import kotlin.math.max
import kotlin.math.min

class FaceOverlayView(
    context: Context
) : View(context) {

    private val density =
        resources
            .displayMetrics
            .density

    private val boxPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color =
                Color.CYAN

            style =
                Paint.Style.STROKE

            strokeWidth =
                4f * density
        }

    private val textPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color =
                Color.CYAN

            textSize =
                18f * density

            style =
                Paint.Style.FILL
        }

    private var faces:
            List<DetectedFaceRegion> =
        emptyList()

    private val recognizedBoxPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            color =
                Color.GREEN

            style =
                Paint.Style.STROKE

            strokeWidth =
                6f * density
        }

    private val recognizedTextPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            color =
                Color.GREEN

            textSize =
                20f * density

            style =
                Paint.Style.FILL
        }

    private var sourceWidth =
        0

    private var sourceHeight =
        0

    private var viewportShape =
        ViewportShape.WIDE_ELLIPSE

    init {
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun submit(
        result: FaceDetectionResult
    ) {
        faces =
            result.faces

        sourceWidth =
            result.sourceWidth

        sourceHeight =
            result.sourceHeight

        invalidate()
    }

    fun clear() {

        faces =
            emptyList()

        sourceWidth =
            0

        sourceHeight =
            0

        invalidate()
    }

    fun setViewportShape(
        shape: ViewportShape
    ) {
        if (
            shape == viewportShape
        ) {
            return
        }

        viewportShape =
            shape

        invalidate()
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(
            canvas
        )

        if (
            width <= 0 ||
            height <= 0 ||
            sourceWidth <= 0 ||
            sourceHeight <= 0
        ) {
            return
        }

        val horizontalScale =
            width.toFloat() /
                    sourceWidth

        val verticalScale =
            height.toFloat() /
                    sourceHeight

        val scale =
            if (
                viewportShape ==
                ViewportShape.CIRCLE
            ) {
                max(
                    horizontalScale,
                    verticalScale
                )
            } else {
                min(
                    horizontalScale,
                    verticalScale
                )
            }

        val renderedWidth =
            sourceWidth *
                    scale

        val renderedHeight =
            sourceHeight *
                    scale

        val offsetX =
            (
                    width -
                            renderedWidth
                    ) / 2f

        val offsetY =
            (
                    height -
                            renderedHeight
                    ) / 2f

        faces.forEach {
                face ->

            val rect =
                mapRect(
                    source =
                        face.boundingBox,

                    scale =
                        scale,

                    offsetX =
                        offsetX,

                    offsetY =
                        offsetY
                )


            val isRecognized =
                face.recognizedName !=
                        null

            val boxPaintToUse =
                if (
                    isRecognized
                ) {
                    recognizedBoxPaint
                } else {
                    boxPaint
                }

            val textPaintToUse =
                if (
                    isRecognized
                ) {
                    recognizedTextPaint
                } else {
                    textPaint
                }

            canvas.drawRect(
                rect,
                boxPaintToUse
            )

            val label =
                if (
                    face.recognizedName !=
                    null
                ) {

                    val similarity =
                        face.similarity

                    if (
                        similarity != null
                    ) {
                        "${face.recognizedName} " +
                                "%.2f".format(
                                    similarity
                                )
                    } else {
                        face.recognizedName
                    }

                } else {

                    face.trackingId
                        ?.let {
                            "FACE #$it"
                        }
                        ?: "FACE"
                }

            canvas.drawText(
                label,
                rect.left,
                (
                        rect.top -
                                8f *
                                density
                        )
                    .coerceAtLeast(
                        20f *
                                density
                    ),
                textPaintToUse
            )
        }
    }

    private fun mapRect(
        source: Rect,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ): RectF =
        RectF(
            offsetX +
                    source.left *
                    scale,

            offsetY +
                    source.top *
                    scale,

            offsetX +
                    source.right *
                    scale,

            offsetY +
                    source.bottom *
                    scale
        )
}