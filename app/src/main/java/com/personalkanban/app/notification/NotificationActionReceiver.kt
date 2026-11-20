package com.personalkanban.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.personalkanban.app.data.repository.TaskRepository
import com.personalkanban.app.domain.model.TaskStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_DONE = "com.personalkanban.app.ACTION_MARK_DONE"
        const val ACTION_SNOOZE = "com.personalkanban.app.ACTION_SNOOZE"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, taskId.hashCode())
        NotificationManagerCompat.from(context).cancel(notificationId)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_MARK_DONE -> {
                        taskRepository.changeStatus(taskId, TaskStatus.DONE)
                    }
                    ACTION_SNOOZE -> {
                        reminderScheduler.scheduleSnooze(taskId, snoozeMinutes = 30)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
