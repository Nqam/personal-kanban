package com.personalkanban.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.personalkanban.app.MainActivity
import com.personalkanban.app.R
import com.personalkanban.app.data.local.PersonalKanbanDatabase
import com.personalkanban.app.data.local.toDomain
import com.personalkanban.app.domain.model.TaskFilterEngine
import com.personalkanban.app.domain.model.TaskStatus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KanbanWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun database(): PersonalKanbanDatabase
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    WidgetEntryPoint::class.java
                )
                val tasks = entryPoint.database().taskDao()
                    .getAllTasksIncludingDeleted()
                    .map { it.toDomain() }
                    .filter { !it.isDeleted && !it.isArchived && it.status != TaskStatus.DONE }

                val now = System.currentTimeMillis()
                val todayCount = tasks.count { TaskFilterEngine.isDueToday(it, now) }
                val inProgressCount = tasks.count { it.status == TaskStatus.IN_PROGRESS }

                val topTasks = tasks
                    .sortedWith(compareByDescending<com.personalkanban.app.domain.model.TaskItem> { it.priority.weight }.thenBy { it.dueAt ?: Long.MAX_VALUE })
                    .take(4)

                val tasksText = if (topTasks.isEmpty()) {
                    "Нет активных задач"
                } else {
                    topTasks.joinToString("\n") { "• ${it.title}" }
                }

                for (widgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_kanban)
                    views.setTextViewText(
                        R.id.widget_summary,
                        "Сегодня: $todayCount · В работе: $inProgressCount · Всего: ${tasks.size}"
                    )
                    views.setTextViewText(R.id.widget_tasks, tasksText)

                    val openAppIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val openPending = PendingIntent.getActivity(
                        context, 100, openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, openPending)

                    val quickAddIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(MainActivity.EXTRA_QUICK_ADD, true)
                    }
                    val addPending = PendingIntent.getActivity(
                        context, 101, quickAddIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_add_btn, addPending)

                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, KanbanWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, KanbanWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
