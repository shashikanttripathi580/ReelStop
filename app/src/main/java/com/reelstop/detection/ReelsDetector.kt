package com.reelstop.detection

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Determines whether the user is currently viewing Instagram Reels.
 *
 * Employs multi-layer detection heuristics:
 * 1. Window / Activity class names associated with Clips/Reels.
 * 2. Resource ID detection for vertical clips view pagers.
 * 3. Text and content-description markers characteristic of Reels
 *    (e.g., "Original audio", "Remix", "Reels", "Use audio").
 *
 * Implements strict traversal bounds (max depth 5, max nodes 40)
 * to keep UI latency negligible and prevent accessibility lag.
 */
@Singleton
class ReelsDetector @Inject constructor() {

    companion object {
        // Resource ID substrings used by Instagram for Reels / Clips
        private val REELS_RESOURCE_KEYWORDS = listOf(
            "clips_viewer",
            "clips_view_pager",
            "reel_viewer",
            "clips_media_item",
            "clips_video_container",
            "clips_swipe_refresh_container",
            "clips_author_name"
        )

        // Text & Content Description indicators specific to Reels
        private val REELS_TEXT_KEYWORDS = listOf(
            "reels",
            "original audio",
            "remix",
            "use audio",
            "reel by",
            "watch more reels"
        )

        // Class names known to host Reels
        private val REELS_CLASS_KEYWORDS = listOf(
            "clips",
            "reelviewer"
        )

        private const val MAX_NODE_INSPECTION_DEPTH = 6
        private const val MAX_NODES_TO_INSPECT = 45
    }

    private val _isInsideReels = MutableStateFlow(false)
    val isInsideReels: StateFlow<Boolean> = _isInsideReels.asStateFlow()

    private var cachedRootNode: AccessibilityNodeInfo? = null
    private var lastInspectionTimestamp = 0L
    private const val MIN_INSPECTION_INTERVAL_MS = 250L

    /**
     * Evaluates window/node state to detect if currently inside Reels.
     */
    fun evaluate(event: AccessibilityEvent, rootNodeSupplier: () -> AccessibilityNodeInfo?): Boolean {
        val now = System.currentTimeMillis()

        // Check event-level indicators first
        val eventClassName = event.className?.toString()?.lowercase() ?: ""
        val eventContentDesc = event.contentDescription?.toString()?.lowercase() ?: ""

        if (eventClassName.contains("clips") || eventClassName.contains("reel")) {
            _isInsideReels.value = true
            return true
        }

        // Throttle full node tree traversal
        if (now - lastInspectionTimestamp < MIN_INSPECTION_INTERVAL_MS) {
            return _isInsideReels.value
        }
        lastInspectionTimestamp = now

        val rootNode = rootNodeSupplier() ?: return _isInsideReels.value
        val detected = inspectNodeHierarchy(rootNode)
        _isInsideReels.value = detected
        return detected
    }

    /**
     * Bounded BFS/DFS traversal of the node tree searching for Reels signatures.
     */
    private fun inspectNodeHierarchy(root: AccessibilityNodeInfo): Boolean {
        var inspectedCount = 0
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(Pair(root, 0))

        while (queue.isNotEmpty() && inspectedCount < MAX_NODES_TO_INSPECT) {
            val (node, depth) = queue.removeFirst()
            inspectedCount++

            // Check view resource ID
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            for (keyword in REELS_RESOURCE_KEYWORDS) {
                if (viewId.contains(keyword)) {
                    return true
                }
            }

            // Check content description
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            for (keyword in REELS_TEXT_KEYWORDS) {
                if (contentDesc.contains(keyword)) {
                    return true
                }
            }

            // Check visible text
            val text = node.text?.toString()?.lowercase() ?: ""
            for (keyword in REELS_TEXT_KEYWORDS) {
                if (text.contains(keyword)) {
                    return true
                }
            }

            // Traverse children if within depth bound
            if (depth < MAX_NODE_INSPECTION_DEPTH) {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        queue.add(Pair(child, depth + 1))
                    }
                }
            }
        }

        return false
    }

    fun setInsideReels(isInside: Boolean) {
        _isInsideReels.value = isInside
    }

    fun reset() {
        _isInsideReels.value = false
    }
}
