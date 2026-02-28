package com.autoscroll.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*

/**
 * AccessibilityService responsible for dispatching human-like scroll gestures.
 *
 * Lifecycle:
 * - The system starts the service when the user enables it in Settings.
 * - [startScrolling] / [stopScrolling] control the scroll loop.
 * - The loop uses [HumanLikeGestureEngine] for gesture generation and delay calculation.
 */
class AutoScrollService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoScroll"

        /** Singleton reference so other components can communicate with the service. */
        @Volatile
        var instance: AutoScrollService? = null
            private set

        /** Observable scrolling state. */
        @Volatile
        var isScrolling: Boolean = false
            private set

        /** Listeners for state changes. */
        private val stateListeners = mutableListOf<(Boolean) -> Unit>()

        fun addStateListener(listener: (Boolean) -> Unit) {
            stateListeners.add(listener)
        }

        fun removeStateListener(listener: (Boolean) -> Unit) {
            stateListeners.remove(listener)
        }

        private fun notifyStateChange(running: Boolean) {
            isScrolling = running
            stateListeners.forEach { it(running) }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var scrollJob: Job? = null

    // ---------- Service lifecycle ----------

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "AccessibilityService connected")
    }

    override fun onDestroy() {
        stopScrolling()
        instance = null
        serviceScope.cancel()
        Log.i(TAG, "AccessibilityService destroyed")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used — we only dispatch gestures
    }

    override fun onInterrupt() {
        stopScrolling()
        Log.w(TAG, "AccessibilityService interrupted")
    }

    // ---------- Scroll control ----------

    fun startScrolling() {
        if (scrollJob?.isActive == true) return
        Log.i(TAG, "▶ Scrolling STARTED")
        notifyStateChange(true)

        scrollJob = serviceScope.launch {
            while (isActive) {
                performSwipe()
                val delay = HumanLikeGestureEngine.nextDelayMs()
                delay(delay)
            }
        }
    }

    fun stopScrolling() {
        scrollJob?.cancel()
        scrollJob = null
        notifyStateChange(false)
        Log.i(TAG, "⏹ Scrolling STOPPED")
    }

    fun toggleScrolling() {
        if (isScrolling) stopScrolling() else startScrolling()
    }

    // ---------- Gesture dispatch ----------

    private suspend fun performSwipe() {
        val metrics = resources.displayMetrics
        val gesture = HumanLikeGestureEngine.generateSwipe(
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        )

        // Use suspendCancellableCoroutine to bridge the callback-based API
        suspendCancellableCoroutine<Boolean> { cont ->
            val dispatched = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true) {}
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.w(TAG, "Gesture cancelled by system")
                        if (cont.isActive) cont.resume(false) {}
                    }
                },
                null
            )
            if (!dispatched) {
                Log.e(TAG, "dispatchGesture returned false — gesture not dispatched")
                if (cont.isActive) cont.resume(false) {}
            }
        }
    }
}
