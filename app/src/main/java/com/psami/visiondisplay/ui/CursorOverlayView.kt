package com.psami.visiondisplay.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class CursorOverlayView(
    context: Context
) : View(context) {

    data class CursorPosition(
        val x: Float,
        val y: Float
    )

    private val cursorPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }

    private val borderPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

    private var cursorX = 0f
    private var cursorY = 0f

    private var hasPosition = false

    fun moveBy(
        deltaX: Float,
        deltaY: Float
    ) {
        if (width == 0 || height == 0) {
            return
        }

        if (!hasPosition) {
            cursorX = width / 2f
            cursorY = height / 2f
            hasPosition = true
        }

        cursorX =
            (cursorX + deltaX)
                .coerceIn(
                    CURSOR_RADIUS,
                    width - CURSOR_RADIUS
                )

        cursorY =
            (cursorY + deltaY)
                .coerceIn(
                    CURSOR_RADIUS,
                    height - CURSOR_RADIUS
                )

        invalidate()
    }

    fun centerCursor() {
        if (width == 0 || height == 0) {
            post(::centerCursor)
            return
        }

        cursorX = width / 2f
        cursorY = height / 2f

        hasPosition = true

        invalidate()
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)

        if (!hasPosition) {
            return
        }

        canvas.drawCircle(
            cursorX,
            cursorY,
            CURSOR_RADIUS,
            cursorPaint
        )

        canvas.drawCircle(
            cursorX,
            cursorY,
            CURSOR_RADIUS,
            borderPaint
        )
    }

    fun currentPosition(): CursorPosition? {
        if (!hasPosition) {
            return null
        }

        return CursorPosition(
            x = cursorX,
            y = cursorY
        )
    }

    private companion object {
        const val CURSOR_RADIUS = 10f
    }
}