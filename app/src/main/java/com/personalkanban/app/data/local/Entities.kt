package com.personalkanban.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalkanban.app.domain.model.Customer
import com.personalkanban.app.domain.model.SyncConflict
import com.personalkanban.app.domain.model.TaskAttachment
import com.personalkanban.app.domain.model.TaskComment
import com.personalkanban.app.domain.model.TaskItem
import com.personalkanban.app.domain.model.TaskPriority
import com.personalkanban.app.domain.model.TaskStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["status"]),
        Index(value = ["dueAt"]),
        Index(value = ["customerName"]),
        Index(value = ["isDeleted", "isArchived"])
    ]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val status: String,
    val priority: String,
    val dueAt: Long?,
    val hasTime: Boolean,
    val customerId: String?,
    val customerName: String?,
    val labelsJson: String,
    val reminderEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val lastSyncedAt: Long,
    val version: Long,
    val isArchived: Boolean,
    val isDeleted: Boolean,
    val deletedAt: Long?,
    val attachmentsJson: String = "[]"
)

@Entity(
    tableName = "comments",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["taskId"])]
)
data class CommentEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncedAt: Long,
    val version: Long,
    val isDeleted: Boolean
)

@Entity(
    tableName = "customers",
    indices = [Index(value = ["name"])]
)
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean
)

@Entity(tableName = "sync_conflicts")
data class SyncConflictEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val taskTitle: String,
    val localTaskJson: String,
    val remoteTaskJson: String,
    val conflictFieldsJson: String,
    val detectedAt: Long,
    val resolved: Boolean = false
)

private val internalJson = Json { ignoreUnknownKeys = true }

@Serializable
data class TaskSnapshotDto(
    val id: String,
    val title: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.INBOX,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueAt: Long? = null,
    val hasTime: Boolean = false,
    val customerId: String? = null,
    val customerName: String? = null,
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
    val attachments: List<TaskAttachment> = emptyList()
)

fun TaskItem.toSnapshotDto(): TaskSnapshotDto = TaskSnapshotDto(
    id = id,
    title = title,
    description = description,
    status = status,
    priority = priority,
    dueAt = dueAt,
    hasTime = hasTime,
    customerId = customerId,
    customerName = customerName,
    labels = labels,
    reminderEnabled = reminderEnabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    lastSyncedAt = lastSyncedAt,
    version = version,
    isArchived = isArchived,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    attachments = attachments
)

fun TaskSnapshotDto.toDomain(commentCount: Int = 0): TaskItem = TaskItem(
    id = id,
    title = title,
    description = description,
    status = status,
    priority = priority,
    dueAt = dueAt,
    hasTime = hasTime,
    customerId = customerId,
    customerName = customerName,
    labels = labels,
    reminderEnabled = reminderEnabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    lastSyncedAt = lastSyncedAt,
    version = version,
    isArchived = isArchived,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    commentCount = commentCount,
    attachments = attachments
)

fun TaskEntity.toDomain(commentCount: Int = 0): TaskItem {
    val parsedLabels = runCatching {
        internalJson.decodeFromString<List<String>>(labelsJson)
    }.getOrDefault(emptyList())
    val parsedAttachments = runCatching {
        internalJson.decodeFromString<List<TaskAttachment>>(attachmentsJson)
    }.getOrDefault(emptyList())
    return TaskItem(
        id = id,
        title = title,
        description = description,
        status = runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.INBOX),
        priority = runCatching { TaskPriority.valueOf(priority) }.getOrDefault(TaskPriority.MEDIUM),
        dueAt = dueAt,
        hasTime = hasTime,
        customerId = customerId,
        customerName = customerName,
        labels = parsedLabels,
        reminderEnabled = reminderEnabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
        lastSyncedAt = lastSyncedAt,
        version = version,
        isArchived = isArchived,
        isDeleted = isDeleted,
        deletedAt = deletedAt,
        commentCount = commentCount,
        attachments = parsedAttachments
    )
}

fun TaskItem.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    status = status.name,
    priority = priority.name,
    dueAt = dueAt,
    hasTime = hasTime,
    customerId = customerId,
    customerName = customerName,
    labelsJson = internalJson.encodeToString(labels),
    reminderEnabled = reminderEnabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    lastSyncedAt = lastSyncedAt,
    version = version,
    isArchived = isArchived,
    isDeleted = isDeleted,
    deletedAt = deletedAt,
    attachmentsJson = internalJson.encodeToString(attachments)
)

fun CommentEntity.toDomain(): TaskComment = TaskComment(
    id = id,
    taskId = taskId,
    text = text,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastSyncedAt = lastSyncedAt,
    version = version,
    isDeleted = isDeleted
)

fun TaskComment.toEntity(): CommentEntity = CommentEntity(
    id = id,
    taskId = taskId,
    text = text,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastSyncedAt = lastSyncedAt,
    version = version,
    isDeleted = isDeleted
)

fun CustomerEntity.toDomain(): Customer = Customer(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun Customer.toEntity(): CustomerEntity = CustomerEntity(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun SyncConflictEntity.toDomain(): SyncConflict = SyncConflict(
    id = id,
    taskId = taskId,
    taskTitle = taskTitle,
    localTask = internalJson.decodeFromString<TaskSnapshotDto>(localTaskJson).toDomain(),
    remoteTask = internalJson.decodeFromString<TaskSnapshotDto>(remoteTaskJson).toDomain(),
    conflictFields = internalJson.decodeFromString<List<String>>(conflictFieldsJson),
    detectedAt = detectedAt
)

fun SyncConflict.toEntity(): SyncConflictEntity = SyncConflictEntity(
    id = id,
    taskId = taskId,
    taskTitle = taskTitle,
    localTaskJson = internalJson.encodeToString(localTask.toSnapshotDto()),
    remoteTaskJson = internalJson.encodeToString(remoteTask.toSnapshotDto()),
    conflictFieldsJson = internalJson.encodeToString(conflictFields),
    detectedAt = detectedAt,
    resolved = false
)
