package com.psami.visiondisplay.ui

import android.widget.FrameLayout
import androidx.camera.view.PreviewView

data class GlassesRenderTarget(
    val previewView: PreviewView,
    val edgeOverlayView: EdgeOverlayView,
    val ocrOverlayView: OcrOverlayView,
    val interactionLayer: FrameLayout,
    val cursorOverlayView: CursorOverlayView
)