package com.reelstop.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.reelstop.data.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY startTime DESC LIMIT :limit")
    fun getRecentSessions(limit: Int = 10): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE startTime >= :startOfDayTimestamp ORDER BY startTime DESC")
    fun getTodaySessions(startOfDayTimestamp: Long): Flow<List<SessionEntity>>

    @Query("SELECT MAX(duration) FROM sessions WHERE startTime >= :startOfDayTimestamp")
    suspend fun getLongestSessionToday(startOfDayTimestamp: Long): Long?

    @Query("SELECT COUNT(*) FROM sessions WHERE startTime >= :startOfDayTimestamp")
    suspend fun getTodaySessionCount(startOfDayTimestamp: Long): Int

    @Query("SELECT SUM(reelCount) FROM sessions WHERE startTime >= :startOfDayTimestamp")
    suspend fun getTodayTotalReels(startOfDayTimestamp: Long): Int?

    @Query("SELECT SUM(duration) FROM sessions WHERE startTime >= :startOfDayTimestamp")
    suspend fun getTodayTotalDuration(startOfDayTimestamp: Long): Long?
}
