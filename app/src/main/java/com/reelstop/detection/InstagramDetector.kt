package com.reelstop.detection

import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors whether Instagram is currently the foreground application.
 *
 * Implements a grace period to prevent false session terminations when
 * transient UI elements (e.g. system notification pull-down, share sheet,
 * or permission dialogs) momentarily change window focus.
 */
@Singleton
class InstagramDetector @Inject constructor() {

    companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val EXIT_GRACE_PERIOD_MS = 1500L
    }

    private val _isInstagramForeground = MutableStateFlow(false)
    val isInstagramForeground: StateFlow<Boolean> = _isInstagramForeground.asStateFlow()

    private var exitGraceJob: Job? = null

    /**
     * Process an incoming AccessibilityEvent to evaluate package foreground status.
     *
     * @param event The accessibility event received by the service.
     * @param coroutineScope Scope to run the delayed exit check.
     */
    fun onAccessibilityEvent(event: AccessibilityEvent, coroutineScope: CoroutineScope) {
        val packageName = event.packageName?.toString() ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {

            if (packageName == INSTAGRAM_PACKAGE) {
                // Cancel any pending exit grace job
                exitGraceJob?.cancel()
                exitGraceJob = null
                if (!_isInstagramForeground.value) {
                    _isInstagramForeground.value = true
                }
            } else {
                // User navigated away from Instagram; start grace timer before concluding exit
                if (_isInstagramForeground.value && exitGraceJob == null) {
                    exitGraceJob = coroutineScope.launch {
                        delay(EXIT_GRACE_PERIOD_MS)
                        _isInstagramForeground.value = false
                        exitGraceJob = null
                    }
                }
            }
        }
    }

    /**
     * Explicitly reset status (e.g. when accessibility service disconnects).
     */
    fun reset() {
        exitGraceJob?.cancel()
        exitGraceJob = null
        _isInstagramForeground.value = false
    }

    /**
     * Direct test setter for unit testing without full accessibility events.
     */
    fun setForegroundForTesting(isForeground: Boolean) {
        exitGraceJob?.cancel()
        exitGraceJob = null
        _isInstagramForeground.value = isForeground
    }
}
