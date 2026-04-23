package com.example.simplemacrotracking.ui.weight

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Transparent overlay drawn on top of the LineChart to show the drag-selection
 * highlight: a semi-transparent filled rect + two vertical stroke lines.
 */
class ChartSelectionOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 144, 238, 144)   // light green, very transparent
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 144, 238, 144)  // light green, mostly opaque
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    /** Pixel X where the drag started; NaN = no active selection */
    var startX: Float = Float.NaN
    /** Pixel X of the current drag position */
    var endX:   Float = Float.NaN

    fun clearSelection() {
        startX = Float.NaN
        endX   = Float.NaN
        invalidate()
    }

    fun updateSelection(sx: Float, ex: Float) {
        startX = sx
        endX   = ex
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (startX.isNaN() || endX.isNaN()) return
        val left  = minOf(startX, endX)
        val right = maxOf(startX, endX)
        val top   = 0f
        val bot   = height.toFloat()

        // Filled highlight
        canvas.drawRect(left, top, right, bot, fillPaint)
        // Left boundary line
        canvas.drawLine(left, top, left, bot, linePaint)
        // Right boundary line
        canvas.drawLine(right, top, right, bot, linePaint)
    }
}

