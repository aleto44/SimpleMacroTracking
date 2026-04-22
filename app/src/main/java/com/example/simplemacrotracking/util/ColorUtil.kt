package com.example.simplemacrotracking.util

import android.graphics.Color

object ColorUtil {
    /**
     * Returns an HSV-interpolated color for a macro progress ratio.
     *
     * Color scale is centred on the goal (ratio = 1.0 = pure green).
     * The transition band is ±25% of the goal:
     *   ≤ 0.75  → fully red   (hue = 0°)
     *   0.75–1.0 → red → green (hue 0° → 120°)
     *   1.0      → fully green (hue = 120°)
     *   1.0–1.25 → green → red (hue 120° → 0°)
     *   ≥ 1.25  → fully red   (hue = 0°)
     */
    /**
     * Symmetric scale for calories, carbs, and fat.
     * Green at goal, fades to red ±25% away in either direction.
     */
    fun getRatioColor(ratio: Float): Int {
        val distanceFromGoal = Math.abs(ratio - 1f)
        val t = (distanceFromGoal / 0.25f).coerceIn(0f, 1f)
        val hue = (1f - t) * 120f
        return Color.HSVToColor(floatArrayOf(hue, 0.80f, 0.85f))
    }

    /**
     * One-sided scale for protein.
     * Green at goal or above, fades to red only when 25%+ under goal.
     */
    fun getProteinRatioColor(ratio: Float): Int {
        if (ratio >= 1f) return Color.HSVToColor(floatArrayOf(120f, 0.80f, 0.85f))
        val t = ((1f - ratio) / 0.25f).coerceIn(0f, 1f)
        val hue = (1f - t) * 120f
        return Color.HSVToColor(floatArrayOf(hue, 0.80f, 0.85f))
    }
}

