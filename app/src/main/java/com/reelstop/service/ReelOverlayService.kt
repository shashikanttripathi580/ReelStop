package com.reelstop.service

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.reelstop.R
import com.reelstop.data.repository.ReelRepository
import com.reelstop.domain.ReelCounterManager
import com.reelstop.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that renders a non-interactive top-center overlay badge:
 *
 *   [ REELS: 27 ]
 *
 * Window Specifications:
 * - TYPE_APPLICATION_OVERLAY
 * - FLAG_NOT_TOUCHABLE: 100% of touches and swipes pass through to Instagram.
 * - FLAG_NOT_FOCUSABLE: Never steals keyboard or system focus.
 * - Respects display cutouts and status bar safe insets.
 */
@AndroidEntryPoint
class ReelOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "reelstop_overlay_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_SHOW_PREVIEW = "com.reelstop.action.SHOW_PREVIEW"
        const val ACTION_HIDE_PREVIEW = "com.reelstop.action.HIDE_PREVIEW"

        var isServiceRunning = false
            private set
    }

    @Inject
    lateinit var reelCounterManager: ReelCounterManager

    @Inject
    lateinit var reelRepository: ReelRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null

    private var overlayContainer: LinearLayout? = null
    private var counterTextView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var isOverlayAttached = false
    private var previewJob: Job? = null
    private var isPreviewActive = false

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForegroundWithNotification()

        createOverlayView()
        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_PREVIEW -> {
                showPreviewCounter()
            }
            ACTION_HIDE_PREVIEW -> {
                hidePreviewCounter()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ReelStop Active Monitor",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Monitors Instagram Reels consumption in real-time"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ReelStop Active")
            .setContentText("Mindful awareness counter is ready for Instagram")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createOverlayView() {
        // Outer container: non-interactive floating pill
        overlayContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER

            // Subtle dark translucent background with elegant rounded pill shape
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(20f)
                setColor(Color.parseColor("#EE16181D")) // Deep sleek charcoal
                setStroke(dpToPx(1.2f).toInt(), Color.parseColor("#33FFFFFF")) // Subtle glass border
            }
            background = shape
            setPadding(dpToPx(16f).toInt(), dpToPx(7f).toInt(), dpToPx(16f).toInt(), dpToPx(7f).toInt())
            elevation = dpToPx(8f)
        }

        counterTextView = TextView(this).apply {
            text = "REELS: 1"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
        }

        overlayContainer?.addView(counterTextView)

        // WindowManager Layout Parameters
        val topMargin = dpToPx(48f).toInt()
        val windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = topMargin
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
    }

    private fun observeState() {
        serviceScope.launch {
            combine(
                reelCounterManager.counterState,
                reelRepository.observeSettings()
            ) { counterState, settings ->
                Pair(counterState, settings)
            }.collect { (counterState, settings) ->
                val counterEnabled = settings?.counterEnabled ?: true

                if (isPreviewActive) {
                    // Preview active overrides session visibility
                    return@collect
                }

                if (counterState.isSessionActive && counterState.overlayVisible && counterEnabled) {
                    updateCounterText(counterState.reelCount)
                    showOverlay()
                } else {
                    hideOverlay()
                }
            }
        }
    }

    private fun updateCounterText(count: Int) {
        val newText = "REELS: $count"
        counterTextView?.let { tv ->
            if (tv.text != newText) {
                tv.text = newText
                // Gentle pulse animation on count increment
                val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.14f, 1.0f)
                val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.14f, 1.0f)
                ObjectAnimator.ofPropertyValuesHolder(overlayContainer, scaleX, scaleY).apply {
                    duration = 320
                    interpolator = OvershootInterpolator(1.2f)
                    start()
                }
            }
        }
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        if (isOverlayAttached) {
            overlayContainer?.visibility = View.VISIBLE
            return
        }

        try {
            overlayContainer?.visibility = View.VISIBLE
            windowManager?.addView(overlayContainer, layoutParams)
            isOverlayAttached = true
        } catch (e: Exception) {
            // Window already attached or permission revoked
        }
    }

    private fun hideOverlay() {
        if (!isOverlayAttached) return
        try {
            overlayContainer?.visibility = View.GONE
            windowManager?.removeView(overlayContainer)
            isOverlayAttached = false
        } catch (e: Exception) {
            // Already removed
        }
    }

    private fun showPreviewCounter() {
        isPreviewActive = true
        updateCounterText(7)
        showOverlay()

        previewJob?.cancel()
        previewJob = serviceScope.launch {
            kotlinx.coroutines.delay(4500L)
            hidePreviewCounter()
        }
    }

    private fun hidePreviewCounter() {
        isPreviewActive = false
        previewJob?.cancel()
        previewJob = null

        // Return to normal session visibility
        val current = reelCounterManager.counterState.value
        if (current.isSessionActive && current.overlayVisible) {
            updateCounterText(current.reelCount)
        } else {
            hideOverlay()
        }
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        hideOverlay()
        serviceScope.cancel()
    }
}
