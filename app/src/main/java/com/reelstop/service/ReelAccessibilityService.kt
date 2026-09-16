package com.reelstop.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.reelstop.detection.InstagramDetector
import com.reelstop.detection.ReelTransitionDetector
import com.reelstop.detection.ReelsDetector
import com.reelstop.domain.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

/**
 * Core AccessibilityService responsible for observing Instagram window and scroll events.
 *
 * Adheres to strict privacy principles:
 * - Never logs or persists user screen content or text.
 * - Only detects window package name, Reel UI signatures, and scroll transitions.
 * - Zero network capabilities (no INTERNET permission in manifest).
 */
@AndroidEntryPoint
class ReelAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var instagramDetector: InstagramDetector

    @Inject
    lateinit var reelsDetector: ReelsDetector

    @Inject
    lateinit var transitionDetector: ReelTransitionDetector

    @Inject
    lateinit var sessionManager: SessionManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        var isServiceRunning = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        serviceInfo = info

        // Automatically start the overlay service if overlay permission is granted
        if (Settings.canDrawOverlays(this)) {
            val overlayIntent = Intent(this, ReelOverlayService::class.java)
            startForegroundService(overlayIntent)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 1. Process Instagram foreground status
        instagramDetector.onAccessibilityEvent(event, serviceScope)

        val isInstagramFg = instagramDetector.isInstagramForeground.value
        if (!isInstagramFg) {
            reelsDetector.reset()
            return
        }

        // 2. Evaluate if current screen is Instagram Reels
        val isReels = reelsDetector.evaluate(event) { rootInActiveWindow }

        // 3. If actively in Reels, process transition and scroll events
        if (isReels) {
            transitionDetector.processReelEvent(
                event = event,
                rootSupplier = { rootInActiveWindow },
                coroutineScope = serviceScope,
                onTransitionConfirmed = {
                    sessionManager.onTransitionDetected()
                }
            )
        }
    }

    override fun onInterrupt() {
        instagramDetector.reset()
        reelsDetector.reset()
        sessionManager.onReelsExited()
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceScope.cancel()
        sessionManager.stopSessionNow()
    }
}
