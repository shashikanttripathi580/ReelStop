package com.reelstop.detection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Robust event debouncer and aggregator for Instagram Reel transitions.
 *
 * Solves the critical problem where a single swipe gesture produces
 * 10 to 30 accessibility scroll and content change events in rapid succession.
 *
 * Guarantees:
 * 1. Exactly ONE confirmed transition per swipe gesture.
 * 2. Minimum cooldown window (prevents micro-jitter or duplicated settle triggers).
 * 3. Settling timer: waits for scroll events to cease before confirming fallback transitions.
 * 4. Fingerprint-based fast confirmation: if content text/author changes, confirms immediately
 *    (subject to cooldown).
 */
@Singleton
class DebounceFilter @Inject constructor() {

    companion object {
        /** Minimum time between consecutive Reel transitions (milliseconds). */
        const val MIN_TRANSITION_INTERVAL_MS = 750L

        /** Quiescent settling time after the last scroll event before triggering a settled transition. */
        const val SCROLL_SETTLE_WINDOW_MS = 300L
    }

    private var lastConfirmedTransitionTime = 0L
    private var lastScrollEventTime = 0L
    private var pendingSettleJob: Job? = null
    private var lastObservedFingerprint: String? = null

    /**
     * Called when a scroll event occurs inside Reels.
     *
     * @param now Current timestamp in milliseconds.
     * @param coroutineScope Scope for launching settle delay timer.
     * @param onTransitionConfirmed Callback invoked when a transition is verified.
     */
    @Synchronized
    fun onScrollEvent(
        now: Long = System.currentTimeMillis(),
        coroutineScope: CoroutineScope,
        onTransitionConfirmed: () -> Unit
    ) {
        lastScrollEventTime = now

        // If we are currently in cooldown from a recently confirmed transition, ignore jitter
        if (now - lastConfirmedTransitionTime < MIN_TRANSITION_INTERVAL_MS) {
            return
        }

        // Cancel existing pending settle job and start a new settle countdown
        pendingSettleJob?.cancel()
        pendingSettleJob = coroutineScope.launch {
            delay(SCROLL_SETTLE_WINDOW_MS)
            synchronized(this@DebounceFilter) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastConfirmedTransitionTime >= MIN_TRANSITION_INTERVAL_MS) {
                    lastConfirmedTransitionTime = currentTime
                    onTransitionConfirmed()
                }
            }
        }
    }

    /**
     * Called when a content change or node inspection yields a content fingerprint
     * (e.g. author name, audio title, or caption hash).
     *
     * @param fingerprint Unique string representation of visible Reel content.
     * @param now Current timestamp in milliseconds.
     * @param onTransitionConfirmed Callback invoked when a transition is verified.
     * @return true if this fingerprint triggered a transition, false otherwise.
     */
    @Synchronized
    fun onFingerprintObserved(
        fingerprint: String?,
        now: Long = System.currentTimeMillis(),
        onTransitionConfirmed: () -> Unit
    ): Boolean {
        if (fingerprint.isNullOrBlank()) return false

        // If fingerprint hasn't changed, this is not a new Reel
        if (fingerprint == lastObservedFingerprint) {
            return false
        }

        val previousFingerprint = lastObservedFingerprint
        lastObservedFingerprint = fingerprint

        // If this is the very first fingerprint observed in a session, record it without incrementing
        if (previousFingerprint == null) {
            return false
        }

        // Check cooldown
        if (now - lastConfirmedTransitionTime < MIN_TRANSITION_INTERVAL_MS) {
            return false
        }

        // Cancel any pending scroll settle job since content fingerprint confirmed the transition
        pendingSettleJob?.cancel()
        pendingSettleJob = null

        lastConfirmedTransitionTime = now
        onTransitionConfirmed()
        return true
    }

    /**
     * Reset filter state (e.g. at the start or end of a Reels session).
     */
    @Synchronized
    fun reset(initialFingerprint: String? = null) {
        pendingSettleJob?.cancel()
        pendingSettleJob = null
        lastScrollEventTime = 0L
        lastConfirmedTransitionTime = 0L
        lastObservedFingerprint = initialFingerprint
    }

    fun getLastConfirmedTime(): Long = lastConfirmedTransitionTime
    fun getLastFingerprint(): String? = lastObservedFingerprint
}
