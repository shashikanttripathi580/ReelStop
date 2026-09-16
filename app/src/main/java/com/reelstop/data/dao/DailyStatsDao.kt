package com.reelstop.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.reelstop.data.entity.DailyStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStatsDao {
    @Upsert
    suspend fun upsert(dailyStats: DailyStatsEntity)

    @Query("SELECT * FROM daily_stats WHERE date = :date LIMIT 1")
    suspend fun getStatsForDate(date: String): DailyStatsEntity?

    @Query("SELECT * FROM daily_stats WHERE date = :date LIMIT 1")
    fun observeStatsForDate(date: String): Flow<DailyStatsEntity?>

    @Query("SELECT * FROM daily_stats ORDER BY date DESC LIMIT :days")
    fun getRecentDailyStats(days: Int = 7): Flow<List<DailyStatsEntity>>
}
