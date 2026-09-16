package com.reelstop

import com.google.common.truth.Truth.assertThat
import com.reelstop.data.dao.DailyStatsDao
import com.reelstop.data.dao.SessionDao
import com.reelstop.data.dao.SettingsDao
import com.reelstop.data.entity.DailyStatsEntity
import com.reelstop.data.repository.ReelRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyStatsAggregationTest {

    private lateinit var repository: ReelRepository
    private val sessionDao: SessionDao = mockk(relaxed = true)
    private val dailyStatsDao: DailyStatsDao = mockk(relaxed = true)
    private val settingsDao: SettingsDao = mockk(relaxed = true)

    @Before
    fun setup() {
        repository = ReelRepository(
            sessionDao = sessionDao,
            dailyStatsDao = dailyStatsDao,
            settingsDao = settingsDao
        )
    }

    @Test
    fun `date string format produces YYYY-MM-DD`() {
        val testTime = 1726480000000L
        val dateString = repository.getTodayDateString(testTime)
        val expected = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(testTime))
        assertThat(dateString).isEqualTo(expected)
        assertThat(dateString).matches("\\d{4}-\\d{2}-\\d{2}")
    }

    @Test
    fun `recording first session creates new daily stats record`() = runTest {
        coEvery { sessionDao.insertSession(any()) } returns 1L
        coEvery { dailyStatsDao.getStatsForDate(any()) } returns null

        val startTime = 10000L
        val endTime = 70000L // 60s
        val reelCount = 5

        val upsertSlot = slot<DailyStatsEntity>()
        coEvery { dailyStatsDao.upsert(capture(upsertSlot)) } returns Unit

        repository.recordCompletedSession(startTime, endTime, reelCount)

        val captured = upsertSlot.captured
        assertThat(captured.reelCount).isEqualTo(5)
        assertThat(captured.sessionCount).isEqualTo(1)
        assertThat(captured.totalDuration).isEqualTo(60000L)
        assertThat(captured.longestSessionDuration).isEqualTo(60000L)
    }

    @Test
    fun `recording second session updates existing daily stats and longest duration`() = runTest {
        coEvery { sessionDao.insertSession(any()) } returns 2L

        val existingStats = DailyStatsEntity(
            date = repository.getTodayDateString(),
            reelCount = 10,
            sessionCount = 2,
            totalDuration = 120000L, // 2 mins
            longestSessionDuration = 90000L // 1.5 mins
        )
        coEvery { dailyStatsDao.getStatsForDate(any()) } returns existingStats

        val upsertSlot = slot<DailyStatsEntity>()
        coEvery { dailyStatsDao.upsert(capture(upsertSlot)) } returns Unit

        val startTime = 10000L
        val endTime = 160000L // 150 seconds (longer than previous longest 90s)
        val reelCount = 7

        repository.recordCompletedSession(startTime, endTime, reelCount)

        val captured = upsertSlot.captured
        assertThat(captured.reelCount).isEqualTo(17) // 10 + 7
        assertThat(captured.sessionCount).isEqualTo(3) // 2 + 1
        assertThat(captured.totalDuration).isEqualTo(270000L) // 120000 + 150000
        assertThat(captured.longestSessionDuration).isEqualTo(150000L) // new longest
    }
}
