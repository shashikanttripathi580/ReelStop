package com.reelstop.domain

import com.reelstop.data.repository.ReelRepository
import com.reelstop.detection.InstagramDetector
import com.reelstop.detection.ReelTransitionDetector
import com.reelstop.detection.ReelsDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the lifecycle of an Instagram Reels session.
 *
 * Rules:
 * - A session begins when user enters detected Instagram Reels.
 * - Initial reel count is set to 1.
 * - Reel transitions increment the count.
 * - Exiting Reels or Instagram initiates a grace period before finalizing
 *   the session to avoid abrupt termination on quick app switches or notifications.
 * - Completed sessions are persisted to the Room database.
 */
@Singleton
class SessionManager @Inject constructor(
    val reelCounterManager: ReelCounterManager,
    private val reelRepository: ReelRepository,
    private val instagramDetector: InstagramDetector,
    private val reelsDetector: ReelsDetector,
    private val transitionDetector: ReelTransitionDetector
) {
    companion object {
        const val SESSION_EXIT_GRACE_PERIOD_MS = 2000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var sessionJob: Job? = null
    private var exitGraceJob: Job? = null
    private var durationTickerJob: Job? = null

    private val _milestoneEvents = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val milestoneEvents: SharedFlow<Int> = _milestoneEvents.asSharedFlow()

    init {
        observeDetectionState()
    }

    private fun observeDetectionState() {
        scope.launch {
            combine(
                instagramDetector.isInstagramForeground,
                reelsDetector.isInsideReels
            ) { isFg, isReels ->
                isFg && isReels
            }.collect { shouldBeActive ->
                if (shouldBeActive) {
                    onReelsEntered()
                } else {
                    onReelsExited()
                }
            }
        }
    }

    /**
     * User has entered Reels.
     */
    @Synchronized
    fun onReelsEntered() {
        // Cancel any pending exit grace job
        exitGraceJob?.cancel()
        exitGraceJob = null

        val current = reelCounterManager.counterState.value
        if (!current.isSessionActive) {
            val startTime = System.currentTimeMillis()
            reelCounterManager.startSession(startTime)
            transitionDetector.reset()

            // Start duration ticker
            durationTickerJob?.cancel()
            durationTickerJob = scope.launch {
                while (true) {
                    delay(1000L)
                    reelCounterManager.updateDuration()
                }
            }
        }
    }

    /**
     * Reel transition detected by [ReelTransitionDetector].
     */
    fun onTransitionDetected() {
        val newCount = reelCounterManager.incrementCount()
        checkMilestones(newCount)
    }

    private fun checkMilestones(count: Int) {
        if (count == 25 || count == 50 || count == 100 || (count > 100 && count % 50 == 0)) {
            _milestoneEvents.tryEmit(count)
        }
    }

    /**
     * User has navigated away from Reels or Instagram.
     * Initiates a grace period before finalizing session.
     */
    @Synchronized
    fun onReelsExited() {
        if (!reelCounterManager.counterState.value.isSessionActive) return
        if (exitGraceJob != null) return // Already counting down

        exitGraceJob = scope.launch {
            delay(SESSION_EXIT_GRACE_PERIOD_MS)
            finalizeCurrentSession()
        }
    }

    /**
     * Finalizes the current session, records it in Room database, and resets counter.
     */
    @Synchronized
    suspend fun finalizeCurrentSession() {
        durationTickerJob?.cancel()
        durationTickerJob = null
        exitGraceJob = null

        val finalState = reelCounterManager.endSession()
        if (finalState.isSessionActive && finalState.reelCount > 0) {
            val endTime = System.currentTimeMillis()
            reelRepository.recordCompletedSession(
                startTime = finalState.sessionStartTime,
                endTime = endTime,
                reelCount = finalState.reelCount
            )
        }
    }

    /**
     * Manually end session (e.g. from service unbind or app stop).
     */
    fun stopSessionNow() {
        scope.launch {
            finalizeCurrentSession()
        }
    }
}
