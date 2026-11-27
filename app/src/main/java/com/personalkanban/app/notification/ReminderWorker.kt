package com.personalkanban.app.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personalkanban.app.data.local.TaskDao
import com.personalkanban.app.data.local.toDomain
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskDao: TaskDao
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_OFFSET_MINUTES = "offset_minutes"
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val taskEntity = taskDao.getTaskById(taskId) ?: return Result.success()
        ReminderNotificationHelper.showTaskReminder(applicationContext, taskEntity.toDomain())
        return Result.success()
    }
}
