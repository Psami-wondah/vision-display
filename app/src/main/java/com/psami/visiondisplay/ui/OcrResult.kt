package com.psami.visiondisplay.ui

import android.graphics.Rect

data class OcrTextRegion(
    val id: Int,
    val text: String,
    val boundingBox: Rect
)

data class OcrResult(
    val fullText: String,
    val regions: List<OcrTextRegion>,
    val sourceWidth: Int,
    val sourceHeight: Int
)