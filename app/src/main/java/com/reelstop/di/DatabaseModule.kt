package com.reelstop.di

import android.content.Context
import com.reelstop.data.ReelDatabase
import com.reelstop.data.dao.DailyStatsDao
import com.reelstop.data.dao.SessionDao
import com.reelstop.data.dao.SettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ReelDatabase {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return ReelDatabase.buildDatabase(context, appScope)
    }

    @Provides
    fun provideSessionDao(database: ReelDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideDailyStatsDao(database: ReelDatabase): DailyStatsDao = database.dailyStatsDao()

    @Provides
    fun provideSettingsDao(database: ReelDatabase): SettingsDao = database.settingsDao()
}
