package com.psami.visiondisplay.data

data class CameraOption(
    val id: String,

    /*
     * Camera2 IDs.
     *
     * logicalCameraId is the CameraX/Camera2 parent device.
     * physicalCameraId identifies the actual lens/sensor when available.
     */
    val logicalCameraId: String,
    val physicalCameraId: String?,

    val label: String,

    /*
     * Relative to Android's default physical camera.
     * e.g. approximately:
     *
     * 0.6 = ultra-wide
     * 1.0 = main
     * 2.0 = telephoto
     */
    val relativeZoomRatio: Float,

    val focalLengthMm: Float?,
    val sensorWidthMm: Float?,
    val horizontalFovDegrees: Double?,

    val isPhysicalCamera: Boolean
)