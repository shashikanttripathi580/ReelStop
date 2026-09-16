package com.reelstop

import com.google.common.truth.Truth.assertThat
import com.reelstop.domain.ReelCounterManager
import org.junit.Before
import org.junit.Test

class ReelCounterManagerTest {

    private lateinit var counterManager: ReelCounterManager

    @Before
    fun setup() {
        counterManager = ReelCounterManager()
    }

    @Test
    fun `initial state is inactive with zero count`() {
        val state = counterManager.counterState.value
        assertThat(state.isSessionActive).isFalse()
        assertThat(state.reelCount).isEqualTo(0)
        assertThat(state.overlayVisible).isFalse()
    }

    @Test
    fun `startSession initializes count to 1 and activates session`() {
        val startTime = 1700000000000L
        counterManager.startSession(startTime)

        val state = counterManager.counterState.value
        assertThat(state.isSessionActive).isTrue()
        assertThat(state.reelCount).isEqualTo(1)
        assertThat(state.sessionStartTime).isEqualTo(startTime)
        assertThat(state.overlayVisible).isTrue()
    }

    @Test
    fun `incrementCount sequentially increases reel count`() {
        counterManager.startSession()
        assertThat(counterManager.counterState.value.reelCount).isEqualTo(1)

        val count2 = counterManager.incrementCount()
        assertThat(count2).isEqualTo(2)
        assertThat(counterManager.counterState.value.reelCount).isEqualTo(2)

        val count3 = counterManager.incrementCount()
        assertThat(count3).isEqualTo(3)
        assertThat(counterManager.counterState.value.reelCount).isEqualTo(3)
    }

    @Test
    fun `endSession resets counter state and returns final snapshot`() {
        counterManager.startSession()
        counterManager.incrementCount()
        counterManager.incrementCount()

        val finalState = counterManager.endSession()
        assertThat(finalState.reelCount).isEqualTo(3)
        assertThat(finalState.isSessionActive).isTrue()

        val resetState = counterManager.counterState.value
        assertThat(resetState.isSessionActive).isFalse()
        assertThat(resetState.reelCount).isEqualTo(0)
        assertThat(resetState.overlayVisible).isFalse()
    }

    @Test
    fun `setOverlayVisible toggles visibility flag`() {
        counterManager.startSession()
        assertThat(counterManager.counterState.value.overlayVisible).isTrue()

        counterManager.setOverlayVisible(false)
        assertThat(counterManager.counterState.value.overlayVisible).isFalse()

        counterManager.setOverlayVisible(true)
        assertThat(counterManager.counterState.value.overlayVisible).isTrue()
    }
}
