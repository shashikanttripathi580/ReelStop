package com.reelstop.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists an individual Instagram Reels session.
 *
 * A session begins when the user enters detected Instagram Reels (initial reelCount = 1).
 * Every successfully detected Reel transition increments the count.
 * Leaving Instagram/Reels ends the session and records duration.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val reelCount: Int,
    val duration: Long // duration in milliseconds
)
