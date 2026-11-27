package com.personalkanban.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personalkanban.app.data.local.SettingsDataStore
import com.personalkanban.app.data.local.TaskDao
import com.personalkanban.app.data.local.toDomain
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REMINDER_ALARM = "com.personalkanban.app.ACTION_REMINDER_ALARM"
        const val EXTRA_TASK_ID = "extra_task_id"
    }

    @Inject
    lateinit var taskDao: TaskDao

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = settingsDataStore.getSettings()
                if (!settings.notificationsEnabled) return@launch
                val entity = taskDao.getTaskById(taskId) ?: return@launch
                ReminderNotificationHelper.showTaskReminder(context, entity.toDomain())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
