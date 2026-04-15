package com.example.simplemacrotracking.util

import android.graphics.Color

object ColorUtil {
    /**
     * Returns an HSV-interpolated color for a macro progress ratio.
     *  0%  → red
     *  70% → amber
     * 100% → green
     * >100% → fades back toward red at 200%
     */
    fun getRatioColor(ratio: Float): Int {
        val clamped = ratio.coerceIn(0f, 2f)
        return if (clamped <= 1f) {
            // Red (hue=0) → Amber (hue=42) → Green (hue=120) as ratio goes 0→1
            val hue = clamped * 120f
            Color.HSVToColor(floatArrayOf(hue, 0.75f, 0.85f))
        } else {
            // Over goal: green fades back toward red as ratio approaches 2
            val hue = (2f - clamped) * 120f
            Color.HSVToColor(floatArrayOf(hue, 0.85f, 0.80f))
        }
    }
}

