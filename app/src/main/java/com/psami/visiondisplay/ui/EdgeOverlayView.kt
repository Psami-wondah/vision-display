package com.psami.visiondisplay.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Looper
import android.view.View
import kotlin.math.min

class EdgeOverlayView(context: Context) : View(context) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()
    private var edgeBitmap: Bitmap? = null

    init {
        visibility = GONE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setEdgeEnhancementEnabled(enabled: Boolean) {
        visibility = if (enabled) VISIBLE else GONE
        if (!enabled) clear()
    }

    fun submit(bitmap: Bitmap) {
        val submitted = post {
            if (visibility != VISIBLE) {
                bitmap.recycle()
                return@post
            }

            edgeBitmap?.recycle()
            edgeBitmap = bitmap
            invalidate()
        }

        if (!submitted) bitmap.recycle()
    }

    fun clear() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            clearImmediately()
        } else {
            post(::clearImmediately)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = edgeBitmap ?: return
        if (bitmap.isRecycled || width == 0 || height == 0) return

        val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val renderedWidth = bitmap.width * scale
        val renderedHeight = bitmap.height * scale
        val left = (width - renderedWidth) / 2f
        val top = (height - renderedHeight) / 2f
        destination.set(left, top, left + renderedWidth, top + renderedHeight)
        canvas.drawBitmap(bitmap, null, destination, bitmapPaint)
    }

    override fun onDetachedFromWindow() {
        clearImmediately()
        super.onDetachedFromWindow()
    }

    private fun clearImmediately() {
        edgeBitmap?.recycle()
        edgeBitmap = null
        invalidate()
    }
}
