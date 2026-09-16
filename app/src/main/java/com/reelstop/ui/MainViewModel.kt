package com.reelstop.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelstop.data.entity.DailyStatsEntity
import com.reelstop.data.entity.SessionEntity
import com.reelstop.data.entity.SettingsEntity
import com.reelstop.data.repository.ReelRepository
import com.reelstop.domain.CounterState
import com.reelstop.domain.ReelCounterManager
import com.reelstop.service.ReelAccessibilityService
import com.reelstop.service.ReelOverlayService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiStats(
    val todayReels: Int = 0,
    val todaySessions: Int = 0,
    val totalReelTimeFormatted: String = "0m",
    val longestSessionFormatted: String = "0m"
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val reelRepository: ReelRepository,
    private val reelCounterManager: ReelCounterManager
) : ViewModel() {

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _isOverlayPermissionGranted = MutableStateFlow(false)
    val isOverlayPermissionGranted: StateFlow<Boolean> = _isOverlayPermissionGranted.asStateFlow()

    val todayStats: StateFlow<DailyStatsEntity?> = reelRepository.getTodayStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recentSessions: StateFlow<List<SessionEntity>> = reelRepository.getRecentSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<SettingsEntity?> = reelRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val liveCounterState: StateFlow<CounterState> = reelCounterManager.counterState

    fun checkPermissions(context: Context) {
        _isOverlayPermissionGranted.value = Settings.canDrawOverlays(context)
        _isAccessibilityEnabled.value = isAccessibilityServiceEnabled(context)
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val expectedComponentName = "${context.packageName}/${ReelAccessibilityService::class.java.name}"
        return enabledServices.any { service ->
            val id = service.id
            id.contains(context.packageName) && id.contains("ReelAccessibilityService")
        } || ReelAccessibilityService.isServiceRunning
    }

    fun triggerTestOverlay(context: Context) {
        reelCounterManager.triggerTestIncrement()
        if (Settings.canDrawOverlays(context)) {
            val intent = Intent(context, ReelOverlayService::class.java).apply {
                action = ReelOverlayService.ACTION_SHOW_PREVIEW
            }
            context.startService(intent)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            val currentSettings = reelRepository.getSettings()
            reelRepository.updateSettings(currentSettings.copy(onboardingCompleted = true))
        }
    }

    fun formatDuration(durationMs: Long): String {
        val totalMinutes = (durationMs / 60000).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }
}
