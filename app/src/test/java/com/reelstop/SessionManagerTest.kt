package com.reelstop

import com.google.common.truth.Truth.assertThat
import com.reelstop.data.repository.ReelRepository
import com.reelstop.detection.InstagramDetector
import com.reelstop.detection.ReelTransitionDetector
import com.reelstop.detection.ReelsDetector
import com.reelstop.domain.ReelCounterManager
import com.reelstop.domain.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    private lateinit var sessionManager: SessionManager
    private lateinit var counterManager: ReelCounterManager
    private val repository: ReelRepository = mockk(relaxed = true)
    private val instagramDetector: InstagramDetector = mockk(relaxed = true)
    private val reelsDetector: ReelsDetector = mockk(relaxed = true)
    private val transitionDetector: ReelTransitionDetector = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        counterManager = ReelCounterManager()
        sessionManager = SessionManager(
            reelCounterManager = counterManager,
            reelRepository = repository,
            instagramDetector = instagramDetector,
            reelsDetector = reelsDetector,
            transitionDetector = transitionDetector
        )
    }

    @Test
    fun `entering reels starts session with initial count 1`() {
        sessionManager.onReelsEntered()

        val state = counterManager.counterState.value
        assertThat(state.isSessionActive).isTrue()
        assertThat(state.reelCount).isEqualTo(1)
        assertThat(state.overlayVisible).isTrue()
    }

    @Test
    fun `detecting transitions increments count`() {
        sessionManager.onReelsEntered()
        sessionManager.onTransitionDetected()
        sessionManager.onTransitionDetected()

        assertThat(counterManager.counterState.value.reelCount).isEqualTo(3)
    }

    @Test
    fun `finalizing session persists record to database repository`() = testScope.runTest {
        coEvery { repository.recordCompletedSession(any(), any(), any()) } returns 1L

        sessionManager.onReelsEntered()
        sessionManager.onTransitionDetected() // count = 2
        sessionManager.finalizeCurrentSession()

        // Verify repository recorded the session with reelCount = 2
        coVerify(exactly = 1) {
            repository.recordCompletedSession(
                startTime = any(),
                endTime = any(),
                reelCount = 2
            )
        }

        // Counter should be reset
        assertThat(counterManager.counterState.value.isSessionActive).isFalse()
        assertThat(counterManager.counterState.value.reelCount).isEqualTo(0)
    }

    @Test
    fun `milestone at 25 reels emits awareness event`() = testScope.runTest {
        sessionManager.onReelsEntered()

        var milestoneTriggered = 0
        // Simulate 24 transitions so count reaches 25
        for (i in 1..24) {
            sessionManager.onTransitionDetected()
        }

        assertThat(counterManager.counterState.value.reelCount).isEqualTo(25)
    }
}
