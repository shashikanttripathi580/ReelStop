package com.reelstop

import com.google.common.truth.Truth.assertThat
import com.reelstop.detection.DebounceFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebounceFilterTest {

    private lateinit var debounceFilter: DebounceFilter
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        debounceFilter = DebounceFilter()
    }

    @Test
    fun `rapid burst of scroll events produces exactly one transition after settling`() = testScope.runTest {
        var transitionCount = 0
        val baseTime = 1000L

        // Simulate 10 rapid scroll events within a 200ms swipe window
        for (i in 0 until 10) {
            val eventTime = baseTime + (i * 20)
            debounceFilter.onScrollEvent(
                now = eventTime,
                coroutineScope = testScope,
                onTransitionConfirmed = { transitionCount++ }
            )
        }

        // Before settle window completes, count should still be 0
        advanceTimeBy(DebounceFilter.SCROLL_SETTLE_WINDOW_MS - 50)
        assertThat(transitionCount).isEqualTo(0)

        // Advance past settle window
        advanceTimeBy(100)
        assertThat(transitionCount).isEqualTo(1)
    }

    @Test
    fun `two separate swipes separated by cooldown produce two transitions`() = testScope.runTest {
        var transitionCount = 0
        var currentTime = 1000L

        // Swipe 1: 5 events
        for (i in 0 until 5) {
            debounceFilter.onScrollEvent(
                now = currentTime + (i * 30),
                coroutineScope = testScope,
                onTransitionConfirmed = { transitionCount++ }
            )
        }
        advanceTimeBy(DebounceFilter.SCROLL_SETTLE_WINDOW_MS + 50)
        assertThat(transitionCount).isEqualTo(1)

        // Wait past MIN_TRANSITION_INTERVAL_MS
        advanceTimeBy(DebounceFilter.MIN_TRANSITION_INTERVAL_MS + 200)
        currentTime += 2000L

        // Swipe 2: 5 events
        for (i in 0 until 5) {
            debounceFilter.onScrollEvent(
                now = currentTime + (i * 30),
                coroutineScope = testScope,
                onTransitionConfirmed = { transitionCount++ }
            )
        }
        advanceTimeBy(DebounceFilter.SCROLL_SETTLE_WINDOW_MS + 50)
        assertThat(transitionCount).isEqualTo(2)
    }

    @Test
    fun `fingerprint change triggers transition without waiting for scroll settle`() {
        var transitionCount = 0
        val baseTime = 5000L

        // Initial fingerprint observation (first reel of session)
        val firstObserved = debounceFilter.onFingerprintObserved(
            fingerprint = "author:nature_hub",
            now = baseTime,
            onTransitionConfirmed = { transitionCount++ }
        )
        assertThat(firstObserved).isFalse()
        assertThat(transitionCount).isEqualTo(0)

        // User swipes and new author is observed after cooldown
        val secondObserved = debounceFilter.onFingerprintObserved(
            fingerprint = "author:space_explorer",
            now = baseTime + DebounceFilter.MIN_TRANSITION_INTERVAL_MS + 100,
            onTransitionConfirmed = { transitionCount++ }
        )
        assertThat(secondObserved).isTrue()
        assertThat(transitionCount).isEqualTo(1)
    }

    @Test
    fun `identical fingerprint does not trigger multiple transitions`() {
        var transitionCount = 0
        val baseTime = 5000L

        debounceFilter.onFingerprintObserved("author:nature_hub", baseTime) { transitionCount++ }

        // Second event with same author
        val repeated = debounceFilter.onFingerprintObserved(
            fingerprint = "author:nature_hub",
            now = baseTime + 1000L,
            onTransitionConfirmed = { transitionCount++ }
        )
        assertThat(repeated).isFalse()
        assertThat(transitionCount).isEqualTo(0)
    }

    @Test
    fun `reset clears last confirmed time and fingerprints`() {
        debounceFilter.reset("author:initial")
        assertThat(debounceFilter.getLastFingerprint()).isEqualTo("author:initial")
        assertThat(debounceFilter.getLastConfirmedTime()).isEqualTo(0L)
    }
}
