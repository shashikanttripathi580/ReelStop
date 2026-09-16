package com.reelstop.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Aggregated daily statistics for Reels consumption.
 * Date format: YYYY-MM-DD (e.g. 2026-09-16)
 */
@Entity(tableName = "daily_stats")
data class DailyStatsEntity(
    @PrimaryKey
    val date: String,
    val reelCount: Int = 0,
    val sessionCount: Int = 0,
    val totalDuration: Long = 0L, // in milliseconds
    val longestSessionDuration: Long = 0L // in milliseconds
)
