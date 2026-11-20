package com.personalkanban.app.notification

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.personalkanban.app.data.local.SettingsDataStore
import com.personalkanban.app.data.local.TaskDao
import com.personalkanban.app.data.local.toDomain
import com.personalkanban.app.domain.model.TaskItem
import com.personalkanban.app.domain.model.TaskStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val settingsDataStore: SettingsDataStore,
    private val taskDao: TaskDao
) {
    suspend fun scheduleForTask(task: TaskItem) {
        cancelForTask(task.id)
        val settings = settingsDataStore.getSettings()
        if (!settings.notificationsEnabled || !task.reminderEnabled || task.isDeleted || task.isArchived || task.status == TaskStatus.DONE) {
            return
        }
        val dueAt = task.dueAt ?: return
        val now = System.currentTimeMillis()
        if (dueAt <= now) return

        val intervals = buildSet {
            addAll(settings.reminderIntervalsMinutes)
            settings.customReminderMinutes?.let { if (it > 0) add(it) }
        }

        intervals.forEach { offsetMinutes ->
            val triggerAt = dueAt - TimeUnit.MINUTES.toMillis(offsetMinutes.toLong())
            val delayMs = triggerAt - now
            if (delayMs > 0) {
                val input = Data.Builder()
                    .putString(ReminderWorker.KEY_TASK_ID, task.id)
                    .putInt(ReminderWorker.KEY_OFFSET_MINUTES, offsetMinutes)
                    .build()

                val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .setInputData(input)
                    .addTag(taskTag(task.id))
                    .build()

                workManager.enqueueUniqueWork(
                    uniqueWorkName(task.id, offsetMinutes),
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }
        }
    }

    fun scheduleSnooze(taskId: String, snoozeMinutes: Int = 30) {
        val input = Data.Builder()
            .putString(ReminderWorker.KEY_TASK_ID, taskId)
            .putInt(ReminderWorker.KEY_OFFSET_MINUTES, 0)
            .build()

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(snoozeMinutes.toLong(), TimeUnit.MINUTES)
            .setInputData(input)
            .addTag(taskTag(taskId))
            .build()

        workManager.enqueueUniqueWork(
            "reminder_snooze_$taskId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelForTask(taskId: String) {
        workManager.cancelAllWorkByTag(taskTag(taskId))
    }

    suspend fun rescheduleAll() {
        val pending = taskDao.getTasksWithPendingReminders()
        pending.forEach { entity ->
            scheduleForTask(entity.toDomain())
        }
    }

    private fun taskTag(taskId: String) = "reminder_task_$taskId"
    private fun uniqueWorkName(taskId: String, offsetMinutes: Int) = "reminder_${taskId}_$offsetMinutes"
}
