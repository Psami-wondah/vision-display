package com.psami.visiondisplay.ui

import androidx.camera.view.PreviewView
import android.widget.FrameLayout

data class GlassesRenderTarget(
    val previewView: PreviewView,
    val edgeOverlayView: EdgeOverlayView,
    val interactionLayer: FrameLayout,
    val cursorOverlayView: CursorOverlayView,
)
