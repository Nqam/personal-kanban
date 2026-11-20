package com.personalkanban.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.personalkanban.app.data.local.SettingsDataStore
import com.personalkanban.app.data.remote.DriveAuthException
import com.personalkanban.app.data.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class DriveSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settings = settingsDataStore.getSettings()
        if (settings.googleAccountEmail.isNullOrBlank()) {
            return Result.success()
        }
        val result = syncRepository.syncNow(createSnapshot = false)
        return if (result.isSuccess) {
            Result.success()
        } else {
            val error = result.exceptionOrNull()
            if (error is DriveAuthException) {
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        private const val UNIQUE_PERIODIC_WORK = "periodic_drive_sync"
        private const val UNIQUE_DEBOUNCED_WORK = "debounced_drive_sync"

        fun schedulePeriodic(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<DriveSyncWorker>(4, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun scheduleDebouncedSync(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
                .setInitialDelay(30, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniqueWork(
                UNIQUE_DEBOUNCED_WORK,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
