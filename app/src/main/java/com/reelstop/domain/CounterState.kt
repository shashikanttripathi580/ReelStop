package com.reelstop.domain

/**
 * Represents the live state of the Reel counter overlay.
 */
data class CounterState(
    val reelCount: Int = 0,
    val isSessionActive: Boolean = false,
    val sessionStartTime: Long = 0L,
    val sessionDurationMs: Long = 0L,
    val overlayVisible: Boolean = false
)
