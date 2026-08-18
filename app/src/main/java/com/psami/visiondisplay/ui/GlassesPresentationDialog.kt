package com.psami.visiondisplay.ui

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.doOnAttach
import com.psami.visiondisplay.data.CalibrationState
import kotlin.math.roundToInt

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
    val normalizedState = state.normalized()
    val contentWidth = rootWidth * normalizedState.compressionScale
    val contentHeight = rootHeight * normalizedState.compressionScale
    val maxHorizontalTravel = rootWidth - contentWidth
    val maxVerticalTravel = rootHeight - contentHeight
    val leftMargin = maxHorizontalTravel * ((normalizedState.offsetX + 1f) / 2f)
    val topMargin = maxVerticalTravel * ((normalizedState.offsetY + 1f) / 2f)

    return CalibrationLayout(
        width = contentWidth.roundToInt(),
        height = contentHeight.roundToInt(),
        leftMargin = leftMargin.roundToInt(),
        topMargin = topMargin.roundToInt()
    )
}

class GlassesPresentationDialog(
    context: Context,
    display: Display,
    initialState: CalibrationState,
    private val onRenderTargetReady: (GlassesRenderTarget) -> Unit
) : Presentation(context, display) {
    private var currentState = initialState.normalized()
    private var rootContainer: FrameLayout? = null
    private var contentContainer: FrameLayout? = null
    private var edgeOverlayView: EdgeOverlayView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setBackgroundDrawable(Color.BLACK.toDrawable())

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
        }
        val content = FrameLayout(context)

        val previewView = PreviewView(context).apply {
            // TextureView mode keeps scaling and the edge overlay synchronized while calibrating.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            // Preserve the full camera frame instead of cropping away the wide-angle edges.
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
        val overlayView = EdgeOverlayView(context)

        content.addView(previewView, matchParentLayoutParams())
        content.addView(overlayView, matchParentLayoutParams())
        root.addView(content, matchParentLayoutParams())
        setContentView(root)

        rootContainer = root
        contentContainer = content
        edgeOverlayView = overlayView
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyCalibration() }

        updateState(currentState)
        previewView.doOnAttach {
            onRenderTargetReady(GlassesRenderTarget(previewView, overlayView))
        }
    }

    fun updateState(newState: CalibrationState) {
        currentState = newState.normalized()
        edgeOverlayView?.setEdgeEnhancementEnabled(currentState.isEdgeEnhancementEnabled)
        applyCalibration()
    }

    override fun dismiss() {
        edgeOverlayView?.clear()
        super.dismiss()
    }

    private fun applyCalibration() {
        val root = rootContainer ?: return
        val content = contentContainer ?: return
        if (root.width == 0 || root.height == 0) return

        val calibrationLayout = calculateCalibrationLayout(root.width, root.height, currentState)
        val layoutParams = content.layoutParams as FrameLayout.LayoutParams

        // The compressed viewport is laid out inside the unused black display area. At full
        // scale there is no spare area, so offsets intentionally have no visible effect. At a
        // smaller scale, -1/0/+1 place the viewport at the left/centre/right or top/centre/bottom.
        if (
            layoutParams.width != calibrationLayout.width ||
            layoutParams.height != calibrationLayout.height ||
            layoutParams.leftMargin != calibrationLayout.leftMargin ||
            layoutParams.topMargin != calibrationLayout.topMargin
        ) {
            layoutParams.width = calibrationLayout.width
            layoutParams.height = calibrationLayout.height
            layoutParams.leftMargin = calibrationLayout.leftMargin
            layoutParams.topMargin = calibrationLayout.topMargin
            content.layoutParams = layoutParams
        }
    }

    private fun matchParentLayoutParams() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
}
