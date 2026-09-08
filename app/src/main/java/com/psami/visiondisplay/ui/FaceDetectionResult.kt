package com.psami.visiondisplay.ui

import android.graphics.Rect

data class DetectedFaceRegion(
    val trackingId: Int?,
    val boundingBox: Rect,

    /*
     * Keep orientation information now.
     *
     * We will use this during enrollment
     * to reject poor / extreme face angles.
     */
    val eulerX: Float,
    val eulerY: Float,
    val eulerZ: Float
)

data class FaceDetectionResult(
    val faces: List<DetectedFaceRegion>,
    val sourceWidth: Int,
    val sourceHeight: Int
)