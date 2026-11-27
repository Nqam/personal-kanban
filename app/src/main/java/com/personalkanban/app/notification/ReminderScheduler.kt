package com.personalkanban.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.work.WorkManager
import com.personalkanban.app.data.local.SettingsDataStore
import com.personalkanban.app.data.local.TaskDao
import com.personalkanban.app.data.local.toDomain
import com.personalkanban.app.domain.model.ReminderPresets
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
    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

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
            if (triggerAt > now) {
                scheduleAlarm(task.id, offsetMinutes, triggerAt)
            }
        }
    }

    fun scheduleSnooze(taskId: String, snoozeMinutes: Int = 30) {
        val triggerAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(snoozeMinutes.toLong())
        scheduleAlarm(taskId, offsetMinutes = 0, triggerAt = triggerAt)
    }

    suspend fun cancelForTask(taskId: String) {
        // Cancel any legacy WorkManager tasks
        workManager.cancelAllWorkByTag("reminder_task_$taskId")

        val settings = settingsDataStore.getSettings()
        val allOffsets = buildSet {
            add(0)
            addAll(ReminderPresets.defaultOptions.map { it.first })
            addAll(settings.reminderIntervalsMinutes)
            settings.customReminderMinutes?.let { if (it > 0) add(it) }
        }
        allOffsets.forEach { offset ->
            val pending = buildAlarmPendingIntent(taskId, offset, PendingIntent.FLAG_NO_CREATE)
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
    }

    suspend fun rescheduleAll() {
        val pending = taskDao.getTasksWithPendingReminders()
        pending.forEach { entity ->
            scheduleForTask(entity.toDomain())
        }
    }

    private fun scheduleAlarm(taskId: String, offsetMinutes: Int, triggerAt: Long) {
        val pending = buildAlarmPendingIntent(taskId, offsetMinutes, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun buildAlarmPendingIntent(taskId: String, offsetMinutes: Int, flags: Int): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_REMINDER_ALARM
            data = Uri.parse("taskflow://reminder/$taskId/$offsetMinutes")
            putExtra(ReminderAlarmReceiver.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            (taskId to offsetMinutes).hashCode(),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
