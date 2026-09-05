package com.psami.visiondisplay.ui

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.doOnAttach
import com.psami.visiondisplay.data.CalibrationState
import kotlin.math.roundToInt
import android.widget.LinearLayout

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
    private val onRenderTargetReady: (GlassesRenderTarget) -> Unit,
    private val onDisplayDiagnosticsChanged: (String) -> Unit,
    private val onToggleEdgeEnhancement: () -> Unit,
    private val onCycleCamera: () -> Unit,
    private val onRecenterCursor: () -> Unit,
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

        val controls =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
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

        controls.addView(edgesButton)
        controls.addView(cameraButton)
        controls.addView(recenterButton)

        val previewView = PreviewView(context).apply {
            // TextureView mode keeps scaling and the edge overlay synchronized while calibrating.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            // Preserve the full camera frame instead of cropping away the wide-angle edges.
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
        val overlayView = EdgeOverlayView(context)
        val cursorOverlayView =
            CursorOverlayView(context)

        val interactionLayer =
            FrameLayout(context)

        val buttonMargin =
            (48 * resources.displayMetrics.density)
                .roundToInt()

        interactionLayer.addView(
            controls,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity =
                    Gravity.BOTTOM or
                            Gravity.CENTER_HORIZONTAL

                bottomMargin =
                    buttonMargin
            }
        )

        content.addView(previewView, matchParentLayoutParams())
        content.addView(overlayView, matchParentLayoutParams())
        content.addView(
            interactionLayer,
            matchParentLayoutParams()
        )
        content.addView(
            cursorOverlayView,
            matchParentLayoutParams()
        )
        root.addView(content, matchParentLayoutParams())
        setContentView(root)

        rootContainer = root
        contentContainer = content
        edgeOverlayView = overlayView
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyCalibration() }

        root.post {
            publishDisplayDiagnostics()
        }

        updateState(currentState)
        previewView.doOnAttach {
            cursorOverlayView.post {
                cursorOverlayView.centerCursor()
            }
            onRenderTargetReady(
                GlassesRenderTarget(
                    previewView,
                    overlayView,
                    interactionLayer,
                    cursorOverlayView
                )
            )
        }
    }

    fun updateState(newState: CalibrationState) {
        currentState = newState.normalized()
        edgeOverlayView?.setEdgeEnhancementEnabled(currentState.isEdgeEnhancementEnabled)
        applyCalibration()
        rootContainer?.post {
            publishDisplayDiagnostics()
        }
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

    private fun publishDisplayDiagnostics() {
        val root = rootContainer ?: return
        val content = contentContainer ?: return

        val mode = display.mode

        onDisplayDiagnosticsChanged(
            buildString {
                appendLine(
                    "Display mode: ${mode.physicalWidth} × ${mode.physicalHeight}"
                )

                appendLine(
                    "Refresh rate: ${"%.1f".format(mode.refreshRate)} Hz"
                )

                appendLine(
                    "Rotation: ${display.rotation}"
                )

                appendLine(
                    "Root: ${root.width} × ${root.height}"
                )

                appendLine(
                    "Content: ${content.width} × ${content.height}"
                )

                append(
                    "Compression: ${"%.2f".format(currentState.compressionScale)}"
                )
            }
        )
    }

    private fun matchParentLayoutParams() = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
}
