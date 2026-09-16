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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AccessibilityServiceEntryPoint {
    fun instagramDetector(): InstagramDetector
    fun reelsDetector(): ReelsDetector
    fun transitionDetector(): ReelTransitionDetector
    fun sessionManager(): SessionManager
}

/**
 * Core AccessibilityService responsible for observing Instagram window and scroll events.
 *
 * Uses Hilt's EntryPoint pattern since AccessibilityService is a specialized system service.
 * Adheres to strict privacy principles:
 * - Never logs or persists user screen content or text.
 * - Only detects window package name, Reel UI signatures, and scroll transitions.
 * - Zero network capabilities (no INTERNET permission in manifest).
 */
class ReelAccessibilityService : AccessibilityService() {

    lateinit var instagramDetector: InstagramDetector
    lateinit var reelsDetector: ReelsDetector
    lateinit var transitionDetector: ReelTransitionDetector
    lateinit var sessionManager: SessionManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        var isServiceRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            AccessibilityServiceEntryPoint::class.java
        )
        instagramDetector = entryPoint.instagramDetector()
        reelsDetector = entryPoint.reelsDetector()
        transitionDetector = entryPoint.transitionDetector()
        sessionManager = entryPoint.sessionManager()
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
        if (::instagramDetector.isInitialized) {
            instagramDetector.reset()
        }
        if (::reelsDetector.isInitialized) {
            reelsDetector.reset()
        }
        if (::sessionManager.isInitialized) {
            sessionManager.onReelsExited()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceScope.cancel()
        if (::sessionManager.isInitialized) {
            sessionManager.stopSessionNow()
        }
    }
}
