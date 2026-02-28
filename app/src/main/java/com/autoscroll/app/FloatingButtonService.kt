package com.autoscroll.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.app.NotificationCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Foreground service that displays a draggable floating action button overlay.
 *
 * Tapping the FAB toggles the auto-scroll on/off.
 * Long-pressing + dragging repositions the FAB on screen.
 */
class FloatingButtonService : Service() {

    companion object {
        private const val TAG = "AutoScroll"
        private const val CHANNEL_ID = "autoscroll_overlay"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var fab: FloatingActionButton

    private val stateListener: (Boolean) -> Unit = { running ->
        updateFabAppearance(running)
    }

    // ---------- Service lifecycle ----------

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Create a context with the app's theme for inflating the Material component
        val context = ContextThemeWrapper(this, R.style.Theme_AutoScroll)

        // Inflate overlay using the themed context
        overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_fab, null)
        fab = overlayView.findViewById(R.id.fabOverlay)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        windowManager.addView(overlayView, params)
        setupFabBehavior(params)

        // Listen to scroll state changes
        AutoScrollService.addStateListener(stateListener)
        updateFabAppearance(AutoScrollService.isScrolling)

        Log.i(TAG, "Floating overlay service started")
    }

    override fun onDestroy() {
        AutoScrollService.removeStateListener(stateListener)
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        Log.i(TAG, "Floating overlay service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- FAB interactions ----------

    private fun setupFabBehavior(params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        fab.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > 25) { // 5px threshold
                        isDragging = true
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(overlayView, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Treat as click — toggle scrolling
                        AutoScrollService.instance?.toggleScrolling()
                            ?: Log.w(TAG, "AutoScrollService not available — is accessibility enabled?")
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun updateFabAppearance(isRunning: Boolean) {
        fab.post {
            if (isRunning) {
                fab.setImageResource(R.drawable.ic_stop)
                fab.backgroundTintList =
                    getColorStateList(R.color.fab_stop)
            } else {
                fab.setImageResource(R.drawable.ic_play)
                fab.backgroundTintList =
                    getColorStateList(R.color.fab_start)
            }
        }
    }

    // ---------- Notification ----------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating overlay active"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
