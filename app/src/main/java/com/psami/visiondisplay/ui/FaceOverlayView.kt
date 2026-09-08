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

    private data class MappedFace(
        val face: DetectedFaceRegion,
        val rect: RectF
    )

    private val density =
        resources.displayMetrics.density

    private val boxPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.CYAN
            style = Paint.Style.STROKE
            strokeWidth = 4f * density
        }

    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.CYAN
            textSize = 18f * density
            style = Paint.Style.FILL
        }

    private val recognizedBoxPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 6f * density
        }

    private val recognizedTextPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GREEN
            textSize = 20f * density
            style = Paint.Style.FILL
        }

    private val taggingBoxPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.YELLOW
            style = Paint.Style.STROKE
            strokeWidth = 7f * density
        }

    private val taggingTextPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.YELLOW
            textSize = 20f * density
            style = Paint.Style.FILL
        }

    private var faces:
            List<DetectedFaceRegion> =
        emptyList()

    private var sourceWidth =
        0

    private var sourceHeight =
        0

    private var viewportShape =
        ViewportShape.WIDE_ELLIPSE

    private var enrollmentTargetTrackingId:
            Int? = null

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

        enrollmentTargetTrackingId =
            null

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

    fun setEnrollmentTargetTrackingId(
        trackingId: Int?
    ) {
        enrollmentTargetTrackingId =
            trackingId

        invalidate()
    }

    fun findFaceAtPosition(
        x: Float,
        y: Float,
        coordinateView: View
    ): DetectedFaceRegion? {

        if (
            width <= 0 ||
            height <= 0
        ) {
            return null
        }

        val coordinateLocation =
            IntArray(2)

        val overlayLocation =
            IntArray(2)

        coordinateView.getLocationInWindow(
            coordinateLocation
        )

        getLocationInWindow(
            overlayLocation
        )

        val localX =
            coordinateLocation[0] +
                    x -
                    overlayLocation[0]

        val localY =
            coordinateLocation[1] +
                    y -
                    overlayLocation[1]

        if (
            localX < 0f ||
            localY < 0f ||
            localX > width ||
            localY > height
        ) {
            return null
        }

        if (
            !pointIsInsideViewportShape(
                localX,
                localY
            )
        ) {
            return null
        }

        /*
         * If boxes overlap, prefer
         * the smallest face box.
         */
        return mappedFaces()
            .filter {
                it.rect.contains(
                    localX,
                    localY
                )
            }
            .minByOrNull {
                it.rect.width() *
                        it.rect.height()
            }
            ?.face
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        mappedFaces()
            .forEach {
                    mapped ->

                val face =
                    mapped.face

                val rect =
                    mapped.rect

                val isTagging =
                    face.trackingId != null &&
                            face.trackingId ==
                            enrollmentTargetTrackingId

                val isRecognized =
                    face.recognizedName !=
                            null

                val boxPaintToUse =
                    when {
                        isTagging ->
                            taggingBoxPaint

                        isRecognized ->
                            recognizedBoxPaint

                        else ->
                            boxPaint
                    }

                val textPaintToUse =
                    when {
                        isTagging ->
                            taggingTextPaint

                        isRecognized ->
                            recognizedTextPaint

                        else ->
                            textPaint
                    }

                canvas.drawRect(
                    rect,
                    boxPaintToUse
                )

                val label =
                    when {
                        isTagging ->
                            "TAGGING…"

                        face.recognizedName !=
                                null -> {

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
                        }

                        else ->
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
                                    8f * density
                            ).coerceAtLeast(
                            20f * density
                        ),
                    textPaintToUse
                )
            }
    }

    private fun mappedFaces():
            List<MappedFace> {

        if (
            width <= 0 ||
            height <= 0 ||
            sourceWidth <= 0 ||
            sourceHeight <= 0
        ) {
            return emptyList()
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

        return faces.map {
                face ->

            MappedFace(
                face =
                    face,

                rect =
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
            )
        }
    }

    private fun pointIsInsideViewportShape(
        x: Float,
        y: Float
    ): Boolean {

        if (
            viewportShape ==
            ViewportShape.RECTANGLE
        ) {
            return true
        }

        val radiusX =
            width / 2f

        val radiusY =
            height / 2f

        if (
            radiusX <= 0f ||
            radiusY <= 0f
        ) {
            return false
        }

        val normalisedX =
            (
                    x -
                            radiusX
                    ) /
                    radiusX

        val normalisedY =
            (
                    y -
                            radiusY
                    ) /
                    radiusY

        return (
                normalisedX *
                        normalisedX +
                        normalisedY *
                        normalisedY
                ) <= 1f
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