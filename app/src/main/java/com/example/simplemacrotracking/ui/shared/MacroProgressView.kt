package com.example.simplemacrotracking.ui.shared

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.simplemacrotracking.util.ColorUtil

/**
 * A simple horizontal progress bar whose fill color interpolates
 * red→amber→green→red based on the consumed/goal ratio.
 */
class MacroProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E1E1E.toInt() // color_border — visible on dark bg
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    /** When true, uses the one-sided protein scale (green at/above goal). */
    var isProteinMode: Boolean = false

    var ratio: Float = 0f
        set(value) {
            field = value.coerceAtLeast(0f)
            fillPaint.color = if (isProteinMode)
                ColorUtil.getProteinRatioColor(value)
            else
                ColorUtil.getRatioColor(value)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = h / 2f

        // Track
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, trackPaint)

        // Fill — capped at width
        val fillWidth = (ratio.coerceIn(0f, 1f) * w)
        if (fillWidth > 0f) {
            rect.set(0f, 0f, fillWidth, h)
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
        }
    }
}

