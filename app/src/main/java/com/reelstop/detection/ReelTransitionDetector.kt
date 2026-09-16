package com.reelstop.detection

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects transitions between individual Instagram Reels.
 *
 * Inspects incoming AccessibilityEvents and delegates to [DebounceFilter]
 * to ensure that noisy bursts of scroll and content events are filtered
 * down to exactly one transition per user swipe.
 */
@Singleton
class ReelTransitionDetector @Inject constructor(
    private val debounceFilter: DebounceFilter
) {

    /**
     * Processes an accessibility event while inside Instagram Reels.
     *
     * @param event The accessibility event.
     * @param rootSupplier Provider for the current window root node (if available).
     * @param coroutineScope Scope for handling debounce timers.
     * @param onTransitionConfirmed Invoked when a valid Reel transition is detected.
     */
    fun processReelEvent(
        event: AccessibilityEvent,
        rootSupplier: () -> AccessibilityNodeInfo?,
        coroutineScope: CoroutineScope,
        onTransitionConfirmed: () -> Unit
    ) {
        val eventType = event.eventType

        // 1. Content change events: try to extract author or audio fingerprint
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val fingerprint = extractFingerprintFromEvent(event) ?: extractFingerprintFromNode(rootSupplier())
            if (fingerprint != null) {
                debounceFilter.onFingerprintObserved(
                    fingerprint = fingerprint,
                    onTransitionConfirmed = onTransitionConfirmed
                )
            }
        }

        // 2. View scrolled events: trigger debounce settle countdown
        if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            // Also try to check if content changed immediately with scroll
            val fingerprint = extractFingerprintFromEvent(event) ?: extractFingerprintFromNode(rootSupplier())
            val fingerprintTriggered = debounceFilter.onFingerprintObserved(
                fingerprint = fingerprint,
                onTransitionConfirmed = onTransitionConfirmed
            )

            if (!fingerprintTriggered) {
                debounceFilter.onScrollEvent(
                    coroutineScope = coroutineScope,
                    onTransitionConfirmed = onTransitionConfirmed
                )
            }
        }
    }

    /**
     * Extracts text snippets directly from the event (avoids tree traversal if present).
     */
    private fun extractFingerprintFromEvent(event: AccessibilityEvent): String? {
        val eventTextList = event.text
        if (!eventTextList.isNullOrEmpty()) {
            val combined = eventTextList.joinToString(" ") { it.toString().trim() }
            if (combined.isNotBlank() && combined.length > 3) {
                return combined.take(60)
            }
        }
        val desc = event.contentDescription?.toString()?.trim()
        if (!desc.isNullOrBlank() && desc.length > 3) {
            return desc.take(60)
        }
        return null
    }

    /**
     * Extracts a shallow fingerprint from the node hierarchy (author name or audio description).
     */
    private fun extractFingerprintFromNode(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        try {
            // Find clips author or sound label
            val authorNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_author_name")
            if (!authorNodes.isNullOrEmpty()) {
                val author = authorNodes[0].text?.toString()?.trim()
                if (!author.isNullOrBlank()) return "author:$author"
            }

            val audioNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_audio_track_title")
            if (!audioNodes.isNullOrEmpty()) {
                val audio = audioNodes[0].text?.toString()?.trim()
                if (!audio.isNullOrBlank()) return "audio:$audio"
            }
        } catch (e: Exception) {
            // Safe fallback if nodes are recycled or inaccessible
        }
        return null
    }

    /**
     * Resets detector state at the beginning or conclusion of a Reels session.
     */
    fun reset(initialFingerprint: String? = null) {
        debounceFilter.reset(initialFingerprint)
    }
}
