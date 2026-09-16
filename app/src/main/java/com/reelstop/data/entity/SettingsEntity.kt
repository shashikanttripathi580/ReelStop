package com.reelstop.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User configuration and wellbeing preferences.
 * Uses a fixed ID = 1 for single-row settings storage.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val counterEnabled: Boolean = true,
    val counterPosition: String = "TOP_CENTER", // "TOP_CENTER", "TOP_LEFT", "TOP_RIGHT"
    val dailyGoal: Int = 50, // Gentle awareness threshold (number of Reels)
    val sessionLimit: Int = 20, // Gentle reminder threshold per session (minutes)
    val milestoneAlerts: Boolean = true, // Subtle notifications at 25, 50, 100
    val notificationsEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false
)
