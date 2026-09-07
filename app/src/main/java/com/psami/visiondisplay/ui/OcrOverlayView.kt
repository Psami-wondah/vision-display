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

class OcrOverlayView(
    context: Context
) : View(context) {

    private data class MappedRegion(
        val region: OcrTextRegion,
        val rect: RectF
    )

    private val density =
        resources
            .displayMetrics
            .density

    private val boxPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color =
                Color.YELLOW

            style =
                Paint.Style.STROKE

            strokeWidth =
                3f * density
        }

    private val selectedBoxPaint =
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

    private var regions:
            List<OcrTextRegion> =
        emptyList()

    private var sourceWidth =
        0

    private var sourceHeight =
        0

    private var viewportShape =
        ViewportShape.WIDE_ELLIPSE

    private var selectedRegionId:
            Int? = null

    init {
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun submit(
        result: OcrResult
    ) {
        sourceWidth =
            result.sourceWidth

        sourceHeight =
            result.sourceHeight

        regions =
            result.regions

        selectedRegionId =
            null

        invalidate()
    }

    fun clear() {
        regions =
            emptyList()

        sourceWidth =
            0

        sourceHeight =
            0

        selectedRegionId =
            null

        invalidate()
    }

    fun setViewportShape(
        shape: ViewportShape
    ) {
        if (
            viewportShape == shape
        ) {
            return
        }

        viewportShape =
            shape

        invalidate()
    }

    fun selectRegion(
        id: Int?
    ) {
        selectedRegionId =
            id

        invalidate()
    }

    /*
     * x/y are coordinates belonging to
     * coordinateView.
     *
     * In our case, that's CursorOverlayView,
     * whose coordinate system is the full
     * glasses root.
     */
    fun findRegionAtPosition(
        x: Float,
        y: Float,
        coordinateView: View
    ): OcrTextRegion? {

        if (
            width == 0 ||
            height == 0 ||
            sourceWidth == 0 ||
            sourceHeight == 0
        ) {
            return null
        }

        val coordinateLocation =
            IntArray(2)

        val overlayLocation =
            IntArray(2)

        coordinateView
            .getLocationInWindow(
                coordinateLocation
            )

        getLocationInWindow(
            overlayLocation
        )

        val windowX =
            coordinateLocation[0] +
                    x

        val windowY =
            coordinateLocation[1] +
                    y

        val localX =
            windowX -
                    overlayLocation[0]

        val localY =
            windowY -
                    overlayLocation[1]

        if (
            localX < 0f ||
            localY < 0f ||
            localX > width ||
            localY > height
        ) {
            return null
        }

        /*
         * Don't allow selection in parts
         * visually clipped away by the
         * circle / ellipse.
         */
        if (
            !pointIsInsideViewportShape(
                localX,
                localY
            )
        ) {
            return null
        }

        /*
         * If boxes overlap, prefer the
         * smallest one under the pointer.
         */
        return mappedRegions()
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
            ?.region
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(
            canvas
        )

        mappedRegions()
            .forEach {
                    mapped ->

                val paint =
                    if (
                        mapped.region.id ==
                        selectedRegionId
                    ) {
                        selectedBoxPaint
                    } else {
                        boxPaint
                    }

                canvas.drawRect(
                    mapped.rect,
                    paint
                )
            }
    }

    private fun mappedRegions():
            List<MappedRegion> {

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

        /*
         * GlassesPresentationDialog uses:
         *
         * Circle       -> FILL_CENTER
         * Ellipse      -> FIT_CENTER
         * Rectangle    -> FIT_CENTER
         */
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

        return regions.map {
                region ->

            MappedRegion(
                region =
                    region,

                rect =
                    mapRect(
                        region.boundingBox,
                        scale,
                        offsetX,
                        offsetY
                    )
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
}