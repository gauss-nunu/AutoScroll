package com.autoscroll.app

/**
 * Holds all configurable parameters for the auto-scroll engine.
 * Shared across ViewModel, Engine, and Services.
 */
object ScrollConfig {

    /** Base delay between swipe actions in milliseconds. */
    @Volatile
    var baseDelayMs: Float = 1500f

    /** Multiplier for Gaussian jitter applied to the delay. */
    @Volatile
    var jitterMultiplier: Float = 0.3f

    /** Standard deviation in pixels for swipe length randomization. */
    @Volatile
    var swipeLengthVariance: Float = 100f

    /** Base duration of a single swipe gesture in milliseconds. */
    @Volatile
    var swipeDurationMs: Float = 400f

    // ---- Derived / Fixed constants ----

    /** Minimum delay to prevent excessively fast scrolling. */
    const val MIN_DELAY_MS: Long = 200L

    /** Gaussian σ for X-axis coordinate dispersion (px). */
    const val X_DISPERSION_SIGMA: Float = 30f

    /** Gaussian σ for Y-axis coordinate dispersion (px). */
    const val Y_DISPERSION_SIGMA: Float = 25f

    /** Maximum offset for Bézier control points from the straight-line midpoint (px). */
    const val BEZIER_CONTROL_OFFSET_MAX: Float = 80f

    /** Minimum swipe duration in ms to keep it realistic. */
    const val MIN_SWIPE_DURATION_MS: Long = 100L
}
