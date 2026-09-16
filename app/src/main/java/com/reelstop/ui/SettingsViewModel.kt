package com.reelstop.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelstop.data.entity.SettingsEntity
import com.reelstop.data.repository.ReelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ReelRepository
) : ViewModel() {

    val settings: StateFlow<SettingsEntity?> = repository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsEntity())

    fun updateCounterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.updateSettings(current.copy(counterEnabled = enabled))
        }
    }

    fun updateCounterPosition(position: String) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.updateSettings(current.copy(counterPosition = position))
        }
    }

    fun updateDailyGoal(goal: Int) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.updateSettings(current.copy(dailyGoal = goal))
        }
    }

    fun updateSessionLimit(limitMinutes: Int) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.updateSettings(current.copy(sessionLimit = limitMinutes))
        }
    }

    fun updateMilestoneAlerts(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.updateSettings(current.copy(milestoneAlerts = enabled))
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = repository.getSettings()
            repository.updateSettings(current.copy(notificationsEnabled = enabled))
        }
    }
}
