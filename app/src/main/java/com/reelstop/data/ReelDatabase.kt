package com.reelstop.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.reelstop.data.dao.DailyStatsDao
import com.reelstop.data.dao.SessionDao
import com.reelstop.data.dao.SettingsDao
import com.reelstop.data.entity.DailyStatsEntity
import com.reelstop.data.entity.SessionEntity
import com.reelstop.data.entity.SettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        SessionEntity::class,
        DailyStatsEntity::class,
        SettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ReelDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun dailyStatsDao(): DailyStatsDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val DATABASE_NAME = "reel_stop.db"

        fun buildDatabase(context: Context, scope: CoroutineScope): ReelDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ReelDatabase::class.java,
                DATABASE_NAME
            ).addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    scope.launch(Dispatchers.IO) {
                        // Populate default settings row
                        val initialSettings = SettingsEntity()
                        db.execSQL(
                            """
                            INSERT OR IGNORE INTO settings (id, counterEnabled, counterPosition, dailyGoal, sessionLimit, milestoneAlerts, notificationsEnabled, onboardingCompleted)
                            VALUES (1, 1, 'TOP_CENTER', 50, 20, 1, 1, 0)
                            """.trimIndent()
                        )
                    }
                }
            }).build()
        }
    }
}
