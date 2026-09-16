package com.reelstop

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ReelStopApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
