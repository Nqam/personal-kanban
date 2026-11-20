package com.personalkanban.app.data.remote

import com.personalkanban.app.data.local.TaskSnapshotDto
import com.personalkanban.app.domain.model.Customer
import com.personalkanban.app.domain.model.TaskComment
import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
data class BackupPayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAt: Long,
    val appVersionName: String,
    val tasks: List<TaskSnapshotDto>,
    val comments: List<BackupCommentDto>,
    val customers: List<BackupCustomerDto>,
    val checksumSha256: String = ""
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun computeDataChecksum(
            tasks: List<TaskSnapshotDto>,
            comments: List<BackupCommentDto>,
            customers: List<BackupCustomerDto>,
            jsonSerializer: kotlinx.serialization.json.Json
        ): String {
            val canonical = buildString {
                append(jsonSerializer.encodeToString(kotlinx.serialization.builtins.ListSerializer(TaskSnapshotDto.serializer()), tasks.sortedBy { it.id }))
                append("|")
                append(jsonSerializer.encodeToString(kotlinx.serialization.builtins.ListSerializer(BackupCommentDto.serializer()), comments.sortedBy { it.id }))
                append("|")
                append(jsonSerializer.encodeToString(kotlinx.serialization.builtins.ListSerializer(BackupCustomerDto.serializer()), customers.sortedBy { it.id }))
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

@Serializable
data class BackupCommentDto(
    val id: String,
    val taskId: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncedAt: Long = 0L,
    val version: Long = 1L,
    val isDeleted: Boolean = false
)

@Serializable
data class BackupCustomerDto(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

fun TaskComment.toBackupDto(): BackupCommentDto = BackupCommentDto(
    id = id,
    taskId = taskId,
    text = text,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastSyncedAt = lastSyncedAt,
    version = version,
    isDeleted = isDeleted
)

fun BackupCommentDto.toDomain(): TaskComment = TaskComment(
    id = id,
    taskId = taskId,
    text = text,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastSyncedAt = lastSyncedAt,
    version = version,
    isDeleted = isDeleted
)

fun Customer.toBackupDto(): BackupCustomerDto = BackupCustomerDto(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun BackupCustomerDto.toDomain(): Customer = Customer(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)
