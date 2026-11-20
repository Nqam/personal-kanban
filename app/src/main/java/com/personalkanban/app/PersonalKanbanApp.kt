package com.personalkanban.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.personalkanban.app.worker.DriveSyncWorker
import com.personalkanban.app.worker.UpdateCheckWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PersonalKanbanApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        val wm = WorkManager.getInstance(this)
        DriveSyncWorker.schedulePeriodic(wm)
        UpdateCheckWorker.schedulePeriodic(wm)
    }
}
