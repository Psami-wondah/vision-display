package com.psami.visiondisplay.ui

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.camera.view.PreviewView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.doOnAttach
import com.psami.visiondisplay.data.CalibrationState
import com.psami.visiondisplay.data.ViewportShape
import kotlin.math.roundToInt
import android.graphics.Bitmap
import android.widget.ImageView

internal data class CalibrationLayout(
    val width: Int,
    val height: Int,
    val leftMargin: Int,
    val topMargin: Int
)

internal fun calculateCalibrationLayout(
    rootWidth: Int,
    rootHeight: Int,
    state: CalibrationState
): CalibrationLayout {

    val normalizedState =
        state.normalized()

    val contentWidth: Float
    val contentHeight: Float

    when (
        normalizedState.viewportShape
    ) {
        ViewportShape.CIRCLE -> {

            val diameter =
                minOf(
                    rootWidth,
                    rootHeight
                ) *
                        normalizedState
                            .compressionScale

            contentWidth =
                diameter

            contentHeight =
                diameter
        }

        ViewportShape.WIDE_ELLIPSE,
        ViewportShape.RECTANGLE -> {

            contentWidth =
                rootWidth *
                        normalizedState
                            .compressionScale

            contentHeight =
                rootHeight *
                        normalizedState
                            .compressionScale
        }
    }

    val maxHorizontalTravel =
        rootWidth -
                contentWidth

    val maxVerticalTravel =
        rootHeight -
                contentHeight

    val leftMargin =
        maxHorizontalTravel *
                (
                        (
                                normalizedState.offsetX +
                                        1f
                                ) /
                                2f
                        )

    val topMargin =
        maxVerticalTravel *
                (
                        (
                                normalizedState.offsetY +
                                        1f
                                ) /
                                2f
                        )

    return CalibrationLayout(
        width =
            contentWidth
                .roundToInt(),

        height =
            contentHeight
                .roundToInt(),

        leftMargin =
            leftMargin
                .roundToInt(),

        topMargin =
            topMargin
                .roundToInt()
    )
}

class GlassesPresentationDialog(
    context: Context,
    display: Display,
    initialState: CalibrationState,
    private val onRenderTargetReady:
        (GlassesRenderTarget) -> Unit,
    private val onDisplayDiagnosticsChanged:
        (String) -> Unit,
    private val onToggleEdgeEnhancement:
        () -> Unit,
    private val onCycleCamera:
        () -> Unit,
    private val onRecenterCursor:
        () -> Unit,
) : Presentation(
    context,
    display
) {

    private var currentState =
        initialState.normalized()

    private var rootContainer:
            FrameLayout? = null

    private var cameraViewportContainer:
            FrameLayout? = null

    private var previewView:
            PreviewView? = null

    private var edgeOverlayView:
            EdgeOverlayView? = null

    private var ocrOverlayView:
            OcrOverlayView? = null

    private var frozenFrameView:
            ImageView? = null

    private var currentFrozenBitmap:
            Bitmap? = null

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        window?.setBackgroundDrawable(
            Color.BLACK.toDrawable()
        )

        val root =
            FrameLayout(context).apply {
                setBackgroundColor(
                    Color.BLACK
                )
            }

        /*
         * Only this view gets moved,
         * resized and clipped.
         */
        val cameraViewport =
            FrameLayout(context).apply {

                outlineProvider =
                    object :
                        ViewOutlineProvider() {

                        override fun getOutline(
                            view: View,
                            outline: Outline
                        ) {
                            if (
                                view.width > 0 &&
                                view.height > 0
                            ) {
                                outline.setOval(
                                    0,
                                    0,
                                    view.width,
                                    view.height
                                )
                            }
                        }
                    }
            }

        val preview =
            PreviewView(context).apply {

                implementationMode =
                    PreviewView
                        .ImplementationMode
                        .COMPATIBLE

                scaleType =
                    PreviewView
                        .ScaleType
                        .FIT_CENTER
            }

        val overlayView =
            EdgeOverlayView(context)

        val textOverlayView =
            OcrOverlayView(context)

        val cursorOverlayView =
            CursorOverlayView(context)

        val interactionLayer =
            FrameLayout(context)

        /*
         * GLASSES CONTROLS
         */
        val controls =
            LinearLayout(context).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER
            }

        val edgesButton =
            Button(context).apply {

                text = "Edges"

                setOnClickListener {
                    onToggleEdgeEnhancement()
                }
            }

        val cameraButton =
            Button(context).apply {

                text = "Camera"

                setOnClickListener {
                    onCycleCamera()
                }
            }

        val recenterButton =
            Button(context).apply {

                text = "Recenter"

                setOnClickListener {
                    onRecenterCursor()
                }
            }

        controls.addView(
            edgesButton
        )

        controls.addView(
            cameraButton
        )

        controls.addView(
            recenterButton
        )

        val buttonMargin =
            (
                    48 *
                            resources
                                .displayMetrics
                                .density
                    )
                .roundToInt()

        interactionLayer.addView(
            controls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams
                    .WRAP_CONTENT,

                ViewGroup.LayoutParams
                    .WRAP_CONTENT
            ).apply {

                gravity =
                    Gravity.BOTTOM or
                            Gravity
                                .CENTER_HORIZONTAL

                bottomMargin =
                    buttonMargin
            }
        )


        val frozenImageView =
            ImageView(context).apply {

                visibility =
                    View.GONE

                scaleType =
                    ImageView.ScaleType.FIT_CENTER

                /*
                 * Prevent the moving preview
                 * underneath from appearing in
                 * letterboxed areas.
                 */
                setBackgroundColor(
                    Color.BLACK
                )
            }

        /*
         * CAMERA VIEWPORT
         */
        cameraViewport.addView(
            preview,
            matchParentLayoutParams()
        )

        cameraViewport.addView(
            overlayView,
            matchParentLayoutParams()
        )

        cameraViewport.addView(
            frozenImageView,
            matchParentLayoutParams()
        )


        cameraViewport.addView(
            textOverlayView,
            matchParentLayoutParams()
        )

        /*
         * ROOT LAYERS
         *
         * Camera moves independently.
         * Controls and cursor stay fixed.
         */
        root.addView(
            cameraViewport,
            matchParentLayoutParams()
        )

        root.addView(
            interactionLayer,
            matchParentLayoutParams()
        )

        root.addView(
            cursorOverlayView,
            matchParentLayoutParams()
        )

        setContentView(
            root
        )

        rootContainer =
            root

        cameraViewportContainer =
            cameraViewport

        previewView =
            preview

        frozenFrameView =
            frozenImageView

        edgeOverlayView =
            overlayView

        ocrOverlayView =
            textOverlayView

        root.addOnLayoutChangeListener { _,
                                         _,
                                         _,
                                         _,
                                         _,
                                         _,
                                         _,
                                         _,
                                         _ ->

            applyCalibration()
        }

        root.post {
            publishDisplayDiagnostics()
        }

        updateState(
            currentState
        )

        preview.doOnAttach {

            cursorOverlayView.post {
                cursorOverlayView
                    .centerCursor()
            }

            onRenderTargetReady(
                GlassesRenderTarget(
                    previewView =
                        preview,

                    edgeOverlayView =
                        overlayView,

                    ocrOverlayView =
                        textOverlayView,

                    interactionLayer =
                        interactionLayer,

                    cursorOverlayView =
                        cursorOverlayView
                )
            )
        }
    }

    fun updateState(
        newState: CalibrationState
    ) {
        currentState =
            newState.normalized()

        edgeOverlayView
            ?.setEdgeEnhancementEnabled(
                currentState
                    .isEdgeEnhancementEnabled
            )

        applyViewportAppearance()

        applyCalibration()

        rootContainer?.post {
            publishDisplayDiagnostics()
        }
    }

    override fun dismiss() {

        edgeOverlayView
            ?.clear()

        clearOcrInspection()

        super.dismiss()
    }

    private fun applyViewportAppearance() {

        val viewport =
            cameraViewportContainer
                ?: return

        val isCircle =
            currentState.viewportShape ==
                    ViewportShape.CIRCLE

        val shouldClip =
            currentState.viewportShape !=
                    ViewportShape.RECTANGLE

        /*
         * Rectangle:
         * no clipping.
         *
         * Circle / ellipse:
         * oval clipping.
         */
        viewport.clipToOutline =
            shouldClip

        /*
         * Circle needs to fill the square.
         *
         * This intentionally crops some
         * horizontal camera information.
         */
        previewView?.scaleType =
            if (isCircle) {
                PreviewView
                    .ScaleType
                    .FILL_CENTER
            } else {
                PreviewView
                    .ScaleType
                    .FIT_CENTER
            }

        /*
         * Keep Sobel overlay scaling
         * consistent with PreviewView.
         */
        edgeOverlayView
            ?.setFillCenter(
                isCircle
            )

        ocrOverlayView
            ?.setViewportShape(
                currentState.viewportShape
            )

        frozenFrameView
            ?.scaleType =
            if (
                isCircle
            ) {
                ImageView
                    .ScaleType
                    .CENTER_CROP
            } else {
                ImageView
                    .ScaleType
                    .FIT_CENTER
            }

        viewport.invalidateOutline()
    }

    private fun applyCalibration() {

        val root =
            rootContainer
                ?: return

        val viewport =
            cameraViewportContainer
                ?: return

        if (
            root.width == 0 ||
            root.height == 0
        ) {
            return
        }

        val calibrationLayout =
            calculateCalibrationLayout(
                root.width,
                root.height,
                currentState
            )

        val layoutParams =
            viewport.layoutParams
                    as FrameLayout.LayoutParams

        if (
            layoutParams.width !=
            calibrationLayout.width ||

            layoutParams.height !=
            calibrationLayout.height ||

            layoutParams.leftMargin !=
            calibrationLayout
                .leftMargin ||

            layoutParams.topMargin !=
            calibrationLayout
                .topMargin
        ) {

            layoutParams.width =
                calibrationLayout.width

            layoutParams.height =
                calibrationLayout.height

            layoutParams.leftMargin =
                calibrationLayout
                    .leftMargin

            layoutParams.topMargin =
                calibrationLayout
                    .topMargin

            viewport.layoutParams =
                layoutParams

            /*
             * Dimensions changed, so
             * recalculate the oval.
             */
            viewport.post {
                viewport
                    .invalidateOutline()
            }
        }
    }

    private fun publishDisplayDiagnostics() {

        val root =
            rootContainer
                ?: return

        val viewport =
            cameraViewportContainer
                ?: return

        val mode =
            display.mode

        onDisplayDiagnosticsChanged(
            buildString {

                appendLine(
                    "Display mode: " +
                            "${mode.physicalWidth} × " +
                            "${mode.physicalHeight}"
                )

                appendLine(
                    "Refresh rate: " +
                            "${"%.1f".format(mode.refreshRate)} Hz"
                )

                appendLine(
                    "Rotation: " +
                            display.rotation
                )

                appendLine(
                    "Root: " +
                            "${root.width} × " +
                            "${root.height}"
                )

                appendLine(
                    "Viewport: " +
                            "${viewport.width} × " +
                            "${viewport.height}"
                )

                appendLine(
                    "Viewport position: " +
                            "${viewport.left}, " +
                            "${viewport.top}"
                )

                appendLine(
                    "Shape: " +
                            currentState
                                .viewportShape
                                .name
                )

                append(
                    "Compression: " +
                            "%.2f".format(
                                currentState
                                    .compressionScale
                            )
                )
            }
        )
    }

    fun showOcrInspection(
        capture: OcrCapture
    ) {

        /*
         * Dispose any previous capture first.
         */
        clearOcrInspection()

        currentFrozenBitmap =
            capture.frozenFrame

        frozenFrameView
            ?.apply {

                setImageBitmap(
                    capture.frozenFrame
                )

                visibility =
                    View.VISIBLE
            }

        ocrOverlayView
            ?.submit(
                capture.result
            )
    }

    fun clearOcrInspection() {

        /*
         * Remove ImageView's reference
         * before recycling the Bitmap.
         */
        frozenFrameView
            ?.apply {

                setImageDrawable(
                    null
                )

                visibility =
                    View.GONE
            }

        currentFrozenBitmap
            ?.let {
                    bitmap ->

                if (
                    !bitmap.isRecycled
                ) {
                    bitmap.recycle()
                }
            }

        currentFrozenBitmap =
            null

        ocrOverlayView
            ?.clear()
    }

    private fun matchParentLayoutParams() =
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams
                .MATCH_PARENT,

            ViewGroup.LayoutParams
                .MATCH_PARENT
        )
}