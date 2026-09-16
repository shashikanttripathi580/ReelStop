package com.reelstop.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the live Reel counter for the active session.
 *
 * Requirements:
 * - A Reel count begins at 1 when the first Reel is detected.
 * - Every successfully detected Reel transition increments the count.
 * - Overlay is notified immediately via StateFlow.
 */
@Singleton
class ReelCounterManager @Inject constructor() {

    private val _counterState = MutableStateFlow(CounterState())
    val counterState: StateFlow<CounterState> = _counterState.asStateFlow()

    /**
     * Starts a new session counter initialized to 1 Reel.
     */
    fun startSession(startTime: Long = System.currentTimeMillis()) {
        _counterState.update {
            CounterState(
                reelCount = 1,
                isSessionActive = true,
                sessionStartTime = startTime,
                sessionDurationMs = 0L,
                overlayVisible = true
            )
        }
    }

    /**
     * Increments the count upon confirmed Reel transition.
     *
     * @return The updated reel count.
     */
    fun incrementCount(): Int {
        var updatedCount = 1
        _counterState.update { current ->
            if (!current.isSessionActive) {
                // If an increment arrives before startSession was explicitly called, initialize
                updatedCount = 1
                CounterState(
                    reelCount = 1,
                    isSessionActive = true,
                    sessionStartTime = System.currentTimeMillis(),
                    sessionDurationMs = 0L,
                    overlayVisible = true
                )
            } else {
                updatedCount = current.reelCount + 1
                val elapsed = System.currentTimeMillis() - current.sessionStartTime
                current.copy(
                    reelCount = updatedCount,
                    sessionDurationMs = elapsed
                )
            }
        }
        return updatedCount
    }

    /**
     * Updates the duration while session is active.
     */
    fun updateDuration(currentTime: Long = System.currentTimeMillis()) {
        _counterState.update { current ->
            if (current.isSessionActive) {
                current.copy(sessionDurationMs = currentTime - current.sessionStartTime)
            } else {
                current
            }
        }
    }

    /**
     * Sets overlay visibility independently (e.g. if user toggles setting or for preview mode).
     */
    fun setOverlayVisible(visible: Boolean) {
        _counterState.update { it.copy(overlayVisible = visible) }
    }

    /**
     * Resets the counter when a session concludes.
     */
    fun endSession(): CounterState {
        val finalState = _counterState.value
        _counterState.value = CounterState(
            reelCount = 0,
            isSessionActive = false,
            sessionStartTime = 0L,
            sessionDurationMs = 0L,
            overlayVisible = false
        )
        return finalState
    }

    /**
     * Helper for test preview mode.
     */
    fun triggerTestIncrement() {
        if (!_counterState.value.isSessionActive) {
            startSession()
        } else {
            incrementCount()
        }
    }
}
