package com.reelstop.data.repository

import com.reelstop.data.dao.DailyStatsDao
import com.reelstop.data.dao.SessionDao
import com.reelstop.data.dao.SettingsDao
import com.reelstop.data.entity.DailyStatsEntity
import com.reelstop.data.entity.SessionEntity
import com.reelstop.data.entity.SettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReelRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val dailyStatsDao: DailyStatsDao,
    private val settingsDao: SettingsDao
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun getTodayDateString(timestamp: Long = System.currentTimeMillis()): String {
        return dateFormat.format(Date(timestamp))
    }

    fun getStartOfDayTimestamp(timestamp: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    suspend fun recordCompletedSession(
        startTime: Long,
        endTime: Long,
        reelCount: Int
    ): Long = withContext(Dispatchers.IO) {
        val duration = (endTime - startTime).coerceAtLeast(0L)
        val session = SessionEntity(
            startTime = startTime,
            endTime = endTime,
            reelCount = reelCount,
            duration = duration
        )
        val sessionId = sessionDao.insertSession(session)

        // Aggregate into DailyStats
        val dateStr = getTodayDateString(startTime)
        val existingStats = dailyStatsDao.getStatsForDate(dateStr)

        val updatedStats = if (existingStats != null) {
            existingStats.copy(
                reelCount = existingStats.reelCount + reelCount,
                sessionCount = existingStats.sessionCount + 1,
                totalDuration = existingStats.totalDuration + duration,
                longestSessionDuration = maxOf(existingStats.longestSessionDuration, duration)
            )
        } else {
            DailyStatsEntity(
                date = dateStr,
                reelCount = reelCount,
                sessionCount = 1,
                totalDuration = duration,
                longestSessionDuration = duration
            )
        }
        dailyStatsDao.upsert(updatedStats)

        sessionId
    }

    fun getTodayStats(timestamp: Long = System.currentTimeMillis()): Flow<DailyStatsEntity?> {
        val dateStr = getTodayDateString(timestamp)
        return dailyStatsDao.observeStatsForDate(dateStr).flowOn(Dispatchers.IO)
    }

    suspend fun getTodayStatsDirect(timestamp: Long = System.currentTimeMillis()): DailyStatsEntity? =
        withContext(Dispatchers.IO) {
            dailyStatsDao.getStatsForDate(getTodayDateString(timestamp))
        }

    fun getRecentSessions(limit: Int = 10): Flow<List<SessionEntity>> {
        return sessionDao.getRecentSessions(limit).flowOn(Dispatchers.IO)
    }

    fun getTodaySessions(timestamp: Long = System.currentTimeMillis()): Flow<List<SessionEntity>> {
        return sessionDao.getTodaySessions(getStartOfDayTimestamp(timestamp)).flowOn(Dispatchers.IO)
    }

    fun observeSettings(): Flow<SettingsEntity?> {
        return settingsDao.observeSettings().flowOn(Dispatchers.IO)
    }

    suspend fun getSettings(): SettingsEntity = withContext(Dispatchers.IO) {
        settingsDao.getSettings() ?: run {
            val defaultSettings = SettingsEntity()
            settingsDao.insertInitial(defaultSettings)
            defaultSettings
        }
    }

    suspend fun updateSettings(settings: SettingsEntity) = withContext(Dispatchers.IO) {
        settingsDao.updateSettings(settings)
    }
}
