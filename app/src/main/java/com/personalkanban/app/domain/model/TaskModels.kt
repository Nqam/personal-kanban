package com.personalkanban.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class TaskStatus(val displayName: String, val order: Int) {
    TODO("К выполнению", 0),
    IN_PROGRESS("В работе", 1),
    DONE("Выполнено", 2)
}

@Serializable
enum class TaskPriority(val displayName: String, val weight: Int) {
    CRITICAL("Критический", 4),
    HIGH("Высокий", 3),
    MEDIUM("Средний", 2),
    LOW("Низкий", 1)
}

enum class TaskViewTab(val displayName: String) {
    ALL("Все"),
    TODO("К выполнению"),
    IN_PROGRESS("В работе"),
    DONE("Выполнено")
}

enum class DueDateFilter(val displayName: String) {
    ALL("Любой срок"),
    TODAY("На сегодня"),
    TOMORROW("На завтра"),
    THIS_WEEK("На этой неделе"),
    OVERDUE("Просроченные"),
    NO_DATE("Без срока")
}

enum class SortOption(val displayName: String) {
    PRIORITY_DESC("По приоритету"),
    DUE_DATE_ASC("По сроку"),
    CREATED_DESC("По дате создания"),
    UPDATED_DESC("По изменению"),
    CUSTOMER_ASC("По заказчику")
}

enum class ThemeMode(val displayName: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная")
}

enum class SyncStatusType(val displayName: String) {
    NOT_CONNECTED("Google Drive не подключён"),
    SYNCED("Синхронизировано"),
    SYNCING("Идёт синхронизация"),
    UNSYNCED("Есть несинхронизированные изменения"),
    ERROR("Ошибка синхронизации"),
    OFFLINE("Нет подключения к интернету")
}

@Serializable
data class TaskAttachment(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val localPath: String,
    val sizeBytes: Long,
    val addedAt: Long
)

data class TaskItem(
    val id: String,
    val title: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.TODO,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueAt: Long? = null,
    val hasTime: Boolean = false,
    val customerId: String? = null,
    val customerName: String? = null,
    val epicName: String? = null,
    val labels: List<String> = emptyList(),
    val reminderEnabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val lastSyncedAt: Long = 0L,
    val version: Long = 1L,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val commentCount: Int = 0,
    val attachments: List<TaskAttachment> = emptyList()
)

data class TaskComment(
    val id: String,
    val taskId: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncedAt: Long = 0L,
    val version: Long = 1L,
    val isDeleted: Boolean = false
)

data class Customer(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

data class Epic(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

data class SyncConflict(
    val id: String,
    val taskId: String,
    val taskTitle: String,
    val localTask: TaskItem,
    val remoteTask: TaskItem,
    val conflictFields: List<String>,
    val detectedAt: Long
)

data class SyncState(
    val status: SyncStatusType = SyncStatusType.NOT_CONNECTED,
    val lastSyncAt: Long? = null,
    val errorMessage: String? = null,
    val accountEmail: String? = null,
    val pendingChangesCount: Int = 0
)

data class TaskFilterState(
    val query: String = "",
    val selectedTab: TaskViewTab = TaskViewTab.ALL,
    val statuses: Set<TaskStatus> = emptySet(),
    val priorities: Set<TaskPriority> = emptySet(),
    val customerName: String? = null,
    val epicName: String? = null,
    val dueDateFilter: DueDateFilter = DueDateFilter.ALL,
    val onlyOverdue: Boolean = false,
    val onlyWithComments: Boolean = false,
    val selectedLabel: String? = null,
    val sortOption: SortOption = SortOption.PRIORITY_DESC
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val compactCards: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val reminderIntervalsMinutes: Set<Int> = setOf(30, 1440),
    val customReminderMinutes: Int? = null,
    val googleAccountEmail: String? = null,
    val lastSyncAt: Long? = null,
    val lastSyncStatus: SyncStatusType = SyncStatusType.NOT_CONNECTED,
    val lastSyncError: String? = null,
    val driveFolderId: String? = null
)

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
    val sha256DownloadUrl: String?,
    val publishedAt: String
)

data class BackupSnapshotInfo(
    val fileId: String,
    val fileName: String,
    val modifiedTime: String,
    val sizeBytes: Long
)

object ReminderPresets {
    val defaultOptions: List<Pair<Int, String>> = listOf(
        15 to "За 15 минут",
        30 to "За 30 минут",
        60 to "За 1 час",
        180 to "За 3 часа",
        720 to "За 12 часов",
        1440 to "За 1 день",
        2880 to "За 2 дня",
        4320 to "За 3 дня",
        10080 to "За неделю"
    )
}
