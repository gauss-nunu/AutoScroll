package com.autoscroll.app

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Core engine that generates human-like swipe gestures.
 *
 * Every swipe uses:
 * 1. Gaussian-dispersed start/end coordinates
 * 2. Cubic Bézier curved path (never a straight line)
 * 3. Variable duration with slight randomization
 * 4. Gaussian-jittered inter-swipe delay
 */
object HumanLikeGestureEngine {

    private const val TAG = "AutoScroll"
    private val random = Random()

    // ---------- Public API ----------

    /**
     * Generates a complete [GestureDescription] for a single human-like upward swipe.
     *
     * @param screenWidth  Device screen width in pixels.
     * @param screenHeight Device screen height in pixels.
     * @return A ready-to-dispatch [GestureDescription].
     */
    fun generateSwipe(screenWidth: Int, screenHeight: Int): GestureDescription {
        // 1. Determine the general swipe area (centre of the screen)
        val centerX = screenWidth / 2f
        val swipeRegionTop = screenHeight * 0.30f
        val swipeRegionBottom = screenHeight * 0.75f
        val baseSwipeLength = swipeRegionBottom - swipeRegionTop

        // 2. Apply Gaussian dispersion to coordinates
        val startX = gaussianClamp(centerX, ScrollConfig.X_DISPERSION_SIGMA, 40f, screenWidth - 40f)
        val startY = gaussianClamp(
            swipeRegionBottom,
            ScrollConfig.Y_DISPERSION_SIGMA,
            swipeRegionTop,
            screenHeight - 20f
        )

        // Randomise the swipe length around the base
        val lengthOffset = gaussian() * ScrollConfig.swipeLengthVariance
        val swipeLength = max(screenHeight * 0.15f, baseSwipeLength + lengthOffset)

        val endX = gaussianClamp(centerX, ScrollConfig.X_DISPERSION_SIGMA, 40f, screenWidth - 40f)
        val endY = max(20f, startY - swipeLength)

        // 3. Generate Bézier control points (two for cubic curve)
        val cp1x = randomControlOffset(startX, endX, 0.33f, screenWidth)
        val cp1y = lerp(startY, endY, 0.33f) + gaussian() * 30f
        val cp2x = randomControlOffset(startX, endX, 0.66f, screenWidth)
        val cp2y = lerp(startY, endY, 0.66f) + gaussian() * 30f

        // 4. Build the curved path
        val path = Path().apply {
            moveTo(startX, startY)
            cubicTo(cp1x, cp1y, cp2x, cp2y, endX, endY)
        }

        // 5. Calculate duration with slight randomization
        val baseDuration = ScrollConfig.swipeDurationMs
        val durationJitter = gaussian() * baseDuration * 0.15f          // ±15 %
        val duration = max(
            ScrollConfig.MIN_SWIPE_DURATION_MS,
            (baseDuration + durationJitter).toLong()
        )

        // 6. Build the GestureDescription
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        // 7. Log everything for UAT verification
        Log.d(TAG, "──────── Swipe Generated ────────")
        Log.d(TAG, "Start      : (${f(startX)}, ${f(startY)})")
        Log.d(TAG, "End        : (${f(endX)}, ${f(endY)})")
        Log.d(TAG, "CP1        : (${f(cp1x)}, ${f(cp1y)})")
        Log.d(TAG, "CP2        : (${f(cp2x)}, ${f(cp2y)})")
        Log.d(TAG, "Length     : ${f(swipeLength)} px")
        Log.d(TAG, "Duration   : $duration ms")

        return gesture
    }

    /**
     * Calculates the next inter-swipe delay based on Gaussian jitter.
     *
     * Delay = baseDelay + jitterMultiplier × baseDelay × N(0,1)
     * Clamped to [MIN_DELAY_MS, baseDelay * 3].
     */
    fun nextDelayMs(): Long {
        val base = ScrollConfig.baseDelayMs
        val jitter = ScrollConfig.jitterMultiplier * base * gaussian()
        val raw = base + jitter
        val clamped = max(
            ScrollConfig.MIN_DELAY_MS.toFloat(),
            min(raw, base * 3f)
        ).toLong()

        Log.d(TAG, "Next delay : $clamped ms  (base=${base.toInt()}, jitter=${f(jitter)})")
        return clamped
    }

    // ---------- Internal helpers ----------

    /**
     * Generates a random offset for a Bézier control point at a given fraction along the path.
     */
    private fun randomControlOffset(
        startX: Float, endX: Float, fraction: Float, screenWidth: Int
    ): Float {
        val baseX = lerp(startX, endX, fraction)
        val offset = gaussian() * ScrollConfig.BEZIER_CONTROL_OFFSET_MAX
        return clamp(baseX + offset, 20f, screenWidth - 20f)
    }

    /** Samples from a Gaussian distribution centred on [mean] with given [sigma], clamped. */
    private fun gaussianClamp(mean: Float, sigma: Float, low: Float, high: Float): Float {
        val sample = mean + gaussian() * sigma
        return clamp(sample, low, high)
    }

    /** Returns a single sample from N(0, 1). */
    private fun gaussian(): Float = random.nextGaussian().toFloat()

    /** Linear interpolation. */
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /** Clamps a value between [lo] and [hi]. */
    private fun clamp(v: Float, lo: Float, hi: Float): Float = max(lo, min(v, hi))

    /** Formats a float to 1 decimal place for log readability. */
    private fun f(v: Float): String = "%.1f".format(v)
}
