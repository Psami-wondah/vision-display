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

internal data class CalibrationTransform(
    val scale: Float,
    val translationX: Float,
    val translationY: Float
)

internal fun calculateCalibrationTransform(
    width: Int,
    height: Int,
    state: CalibrationState
): CalibrationTransform {
    val normalizedState = state.normalized()
    val remainingWidth = width * (1f - normalizedState.compressionScale)
    val remainingHeight = height * (1f - normalizedState.compressionScale)
    return CalibrationTransform(
        scale = normalizedState.compressionScale,
        translationX = remainingWidth * normalizedState.offsetX / 2f,
        translationY = remainingHeight * normalizedState.offsetY / 2f
    )
}

class GlassesPresentationDialog(
    context: Context,
    display: Display,
    initialState: CalibrationState,
    private val onRenderTargetReady: (GlassesRenderTarget) -> Unit
) : Presentation(context, display) {
    private var currentState = initialState.normalized()
    private var contentContainer: FrameLayout? = null
    private var edgeOverlayView: EdgeOverlayView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setBackgroundDrawable(Color.BLACK.toDrawable())

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
        }
        val content = FrameLayout(context)
        val matchParent = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val previewView = PreviewView(context).apply {
            // TextureView mode keeps scaling and the edge overlay synchronized while calibrating.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            // Preserve the full camera frame instead of cropping away the wide-angle edges.
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
        val overlayView = EdgeOverlayView(context)

        content.addView(previewView, matchParent)
        content.addView(overlayView, matchParent)
        root.addView(content, matchParent)
        setContentView(root)

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
        val content = contentContainer ?: return
        if (content.width == 0 || content.height == 0) return

        val transform = calculateCalibrationTransform(content.width, content.height, currentState)
        content.pivotX = content.width / 2f
        content.pivotY = content.height / 2f
        content.scaleX = transform.scale
        content.scaleY = transform.scale
        content.translationX = transform.translationX
        content.translationY = transform.translationY
    }
}
