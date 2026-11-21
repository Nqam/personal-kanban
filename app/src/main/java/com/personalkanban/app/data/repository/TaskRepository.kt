package com.personalkanban.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.WorkManager
import com.personalkanban.app.BuildConfig
import com.personalkanban.app.data.local.CommentDao
import com.personalkanban.app.data.local.CustomerDao
import com.personalkanban.app.data.local.EpicDao
import com.personalkanban.app.data.local.SettingsDataStore
import com.personalkanban.app.data.local.TaskDao
import com.personalkanban.app.data.local.toDomain
import com.personalkanban.app.data.local.toEntity
import com.personalkanban.app.data.local.toSnapshotDto
import com.personalkanban.app.data.remote.BackupPayload
import com.personalkanban.app.data.remote.toBackupDto
import com.personalkanban.app.data.remote.toDomain
import com.personalkanban.app.domain.model.Customer
import com.personalkanban.app.domain.model.Epic
import com.personalkanban.app.domain.model.SyncStatusType
import com.personalkanban.app.domain.model.TaskAttachment
import com.personalkanban.app.domain.model.TaskComment
import com.personalkanban.app.domain.model.TaskItem
import com.personalkanban.app.domain.model.TaskPriority
import com.personalkanban.app.domain.model.TaskStatus
import com.personalkanban.app.notification.ReminderScheduler
import com.personalkanban.app.widget.KanbanWidgetProvider
import com.personalkanban.app.worker.DriveSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
    private val commentDao: CommentDao,
    private val customerDao: CustomerDao,
    private val epicDao: EpicDao,
    private val settingsDataStore: SettingsDataStore,
    private val reminderScheduler: ReminderScheduler,
    private val workManager: WorkManager,
    private val json: Json
) {
    fun observeActiveTasks(): Flow<List<TaskItem>> = combine(
        taskDao.observeActiveTasks(),
        commentDao.observeAllActiveComments()
    ) { tasks, comments ->
        val countByTask = comments.groupingBy { it.taskId }.eachCount()
        tasks.map { it.toDomain(commentCount = countByTask[it.id] ?: 0) }
    }

    fun observeAllCommentsByTask(): Flow<Map<String, List<TaskComment>>> =
        commentDao.observeAllActiveComments().map { list ->
            list.map { it.toDomain() }.groupBy { it.taskId }
        }

    fun observeArchivedTasks(): Flow<List<TaskItem>> =
        taskDao.observeArchivedTasks().map { list -> list.map { it.toDomain() } }

    fun observeDeletedTasks(): Flow<List<TaskItem>> =
        taskDao.observeDeletedTasks().map { list -> list.map { it.toDomain() } }

    fun observeTaskById(taskId: String): Flow<TaskItem?> =
        taskDao.observeTaskById(taskId).map { it?.toDomain() }

    fun observeCommentsForTask(taskId: String): Flow<List<TaskComment>> =
        commentDao.observeCommentsForTask(taskId).map { list -> list.map { it.toDomain() } }

    fun observeCustomers(): Flow<List<Customer>> =
        customerDao.observeActiveCustomers().map { list -> list.map { it.toDomain() } }

    fun observeEpics(): Flow<List<Epic>> =
        epicDao.observeActiveEpics().map { list -> list.map { it.toDomain() } }

    fun observeUnsyncedCount(): Flow<Int> = taskDao.observeUnsyncedCount()

    suspend fun createTask(
        title: String,
        description: String = "",
        status: TaskStatus = TaskStatus.INBOX,
        priority: TaskPriority = TaskPriority.MEDIUM,
        dueAt: Long? = null,
        hasTime: Boolean = false,
        customerName: String? = null,
        epicName: String? = null,
        labels: List<String> = emptyList(),
        reminderEnabled: Boolean = true,
        initialComment: String? = null,
        attachmentUris: List<Uri> = emptyList()
    ): TaskItem {
        val now = System.currentTimeMillis()
        val trimmedCustomer = customerName?.trim()?.takeIf { it.isNotEmpty() }
        val customerId = trimmedCustomer?.let { ensureCustomerExists(it, now) }
        val trimmedEpic = epicName?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmedEpic != null) ensureEpicExists(trimmedEpic, now)
        val taskId = UUID.randomUUID().toString()
        val savedAttachments = attachmentUris.mapNotNull { copyUriToAttachment(taskId, it, now) }

        val task = TaskItem(
            id = taskId,
            title = title.trim(),
            description = description.trim(),
            status = status,
            priority = priority,
            dueAt = dueAt,
            hasTime = hasTime,
            customerId = customerId,
            customerName = trimmedCustomer,
            epicName = trimmedEpic,
            labels = labels.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            reminderEnabled = reminderEnabled,
            createdAt = now,
            updatedAt = now,
            completedAt = if (status == TaskStatus.DONE) now else null,
            lastSyncedAt = 0L,
            version = 1L,
            attachments = savedAttachments
        )
        taskDao.upsertTask(task.toEntity())

        if (!initialComment.isNullOrBlank()) {
            addComment(task.id, initialComment.trim())
        }

        reminderScheduler.scheduleForTask(task)
        markLocalDirty()
        KanbanWidgetProvider.requestUpdate(context)
        return task
    }

    suspend fun updateTask(updated: TaskItem) {
        val now = System.currentTimeMillis()
        val trimmedCustomer = updated.customerName?.trim()?.takeIf { it.isNotEmpty() }
        val customerId = trimmedCustomer?.let { ensureCustomerExists(it, now) }
        val trimmedEpic = updated.epicName?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmedEpic != null) ensureEpicExists(trimmedEpic, now)
        val completedAt = when {
            updated.status == TaskStatus.DONE && updated.completedAt == null -> now
            updated.status != TaskStatus.DONE -> null
            else -> updated.completedAt
        }
        val toSave = updated.copy(
            title = updated.title.trim(),
            description = updated.description.trim(),
            customerId = customerId,
            customerName = trimmedCustomer,
            epicName = trimmedEpic,
            labels = updated.labels.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            updatedAt = now,
            completedAt = completedAt,
            version = updated.version + 1
        )
        taskDao.upsertTask(toSave.toEntity())
        reminderScheduler.scheduleForTask(toSave)
        markLocalDirty()
        KanbanWidgetProvider.requestUpdate(context)
    }

    suspend fun changeStatus(taskId: String, newStatus: TaskStatus): TaskItem? {
        val existing = taskDao.getTaskById(taskId)?.toDomain() ?: return null
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            status = newStatus,
            completedAt = if (newStatus == TaskStatus.DONE) now else null,
            updatedAt = now,
            version = existing.version + 1
        )
        taskDao.upsertTask(updated.toEntity())
        if (newStatus == TaskStatus.DONE) {
            reminderScheduler.cancelForTask(taskId)
        } else {
            reminderScheduler.scheduleForTask(updated)
        }
        markLocalDirty()
        KanbanWidgetProvider.requestUpdate(context)
        return existing
    }

    suspend fun restoreSnapshot(previousTask: TaskItem) {
        val now = System.currentTimeMillis()
        val restored = previousTask.copy(
            updatedAt = now,
            version = previousTask.version + 1
        )
        taskDao.upsertTask(restored.toEntity())
        reminderScheduler.scheduleForTask(restored)
        markLocalDirty()
        KanbanWidgetProvider.requestUpdate(context)
    }

    suspend fun bulkUpdateStatus(taskIds: Set<String>, status: TaskStatus) {
        taskIds.forEach { id -> changeStatus(id, status) }
    }

    suspend fun bulkUpdatePriority(taskIds: Set<String>, priority: TaskPriority) {
        val now = System.currentTimeMillis()
        taskIds.forEach { id ->
            val existing = taskDao.getTaskById(id)?.toDomain() ?: return@forEach
            val updated = existing.copy(
                priority = priority,
                updatedAt = now,
                version = existing.version + 1
            )
            taskDao.upsertTask(updated.toEntity())
        }
        markLocalDirty()
        KanbanWidgetProvider.requestUpdate(context)
    }

    suspend fun duplicateTask(taskId: String): TaskItem? {
        val original = taskDao.getTaskById(taskId)?.toDomain() ?: return null
        return createTask(
            title = "${original.title} (копия)",
            description = original.description,
            status = TaskStatus.INBOX,
            priority = original.priority,
            dueAt = original.dueAt,
            hasTime = original.hasTime,
            customerName = original.customerName,
            epicName = original.epicName,
            labels = original.labels,
            reminderEnabled = original.reminderEnabled
        )
    }

    suspend fun archiveTask(taskId: String, archived: Boolean = true) {
        val existing = taskDao.getTaskById(taskId)?.toDomain() ?: return
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            isArchived = archived,
            updatedAt = now,
            version = existing.version + 1
        )
        taskDao.upsertTask(updated.toEntity())
        if (archived) {
            reminderScheduler.cancelForTask(taskId)
        } else {
            reminderScheduler.scheduleForTask(updated)
        }
        markLocalDirty()
        KanbanWidgetProvider.requestUpdate(context)
    }

    suspend fun autoArchiveOldCompletedTasks(
        nowMillis: Long = System.currentTimeMillis(),
        maxAgeDays: Long = 7L
    ): Int {
        val cutoff = nowMillis - java.util.concurrent.TimeUnit.DAYS.toMillis(maxAgeDays)
        val candidates = taskDao.getAllTasksIncludingDeleted()
            .map { it.toDomain() }
            .filter { task ->
                !task.isDeleted &&
                    !task.isArchived &&
                    task.status == TaskStatus.DONE &&
                    (task.completedAt ?: task.updatedAt) <= cutoff
            }
        if (candidates.isEmpty()) return 0

        val updatedEntities = candidates.map { task ->
            task.copy(
                isArchived = true,
                updatedAt = nowMillis,
                version = task.version + 1
            ).toEntity()
        }
        taskDao.upsertTasks(updatedEntities)
        candidates.forEach { reminderScheduler.cancelForTask(it.id) }
        markLocalDirty()
        KanbanWidgetProvider.requestUpdate(context)
        return candidates.size
    }

    suspend fun softDeleteTask(taskId: String) {
        val existing = taskDao.getTaskById(taskId)?.toDomain() ?: return
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            isDeleted = true,
            deletedAt = now,
            updatedAt = now,
            version = existing.version + 1
        )
        taskDao.upsertTask(updated.toEntity())
        reminderScheduler.cancelForTask(taskId)
        markLocalDirty()
        KanbanWidgetProvider.requestUpdate(context)
    }

    suspend fun restoreFromTrash(taskId: String) {
        val existing = taskDao.getTaskById(taskId)?.toDomain() ?: return
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            isDeleted = false,
            deletedAt = null,
            updatedAt = now,
            version = existing.version + 1
        )
        taskDao.upsertTask(updated.toEntity())
        reminderScheduler.scheduleForTask(updated)
        markLocalDirty()
        KanbanWidgetProvider.requestUpdate(context)
    }

    suspend fun emptyTrash() {
        taskDao.purgeDeletedTasks()
        KanbanWidgetProvider.requestUpdate(context)
    }

    suspend fun addComment(taskId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val now = System.currentTimeMillis()
        val comment = TaskComment(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            text = clean,
            createdAt = now,
            updatedAt = now,
            lastSyncedAt = 0L,
            version = 1L
        )
        commentDao.upsertComment(comment.toEntity())
        touchTaskUpdatedAt(taskId, now)
        markLocalDirty()
    }

    suspend fun editComment(commentId: String, newText: String) {
        val clean = newText.trim()
        if (clean.isEmpty()) return
        val existing = commentDao.getCommentById(commentId)?.toDomain() ?: return
        val now = System.currentTimeMillis()
        commentDao.upsertComment(
            existing.copy(
                text = clean,
                updatedAt = now,
                version = existing.version + 1
            ).toEntity()
        )
        touchTaskUpdatedAt(existing.taskId, now)
        markLocalDirty()
    }

    suspend fun deleteComment(commentId: String) {
        val existing = commentDao.getCommentById(commentId)?.toDomain() ?: return
        val now = System.currentTimeMillis()
        commentDao.upsertComment(
            existing.copy(
                isDeleted = true,
                updatedAt = now,
                version = existing.version + 1
            ).toEntity()
        )
        touchTaskUpdatedAt(existing.taskId, now)
        markLocalDirty()
    }

    suspend fun addCustomer(name: String): Customer? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val existing = customerDao.findByName(clean)
        if (existing != null) return existing.toDomain()
        val now = System.currentTimeMillis()
        val customer = Customer(
            id = UUID.randomUUID().toString(),
            name = clean,
            createdAt = now,
            updatedAt = now
        )
        customerDao.upsertCustomer(customer.toEntity())
        markLocalDirty()
        return customer
    }

    suspend fun deleteCustomer(customerId: String) {
        val all = customerDao.getAllCustomersIncludingDeleted()
        val target = all.firstOrNull { it.id == customerId }?.toDomain() ?: return
        val now = System.currentTimeMillis()
        customerDao.upsertCustomer(target.copy(isDeleted = true, updatedAt = now).toEntity())
        markLocalDirty()
    }

    suspend fun addEpic(name: String): Epic? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val existing = epicDao.findByName(clean)
        if (existing != null) return existing.toDomain()
        val now = System.currentTimeMillis()
        val epic = Epic(
            id = UUID.randomUUID().toString(),
            name = clean,
            createdAt = now,
            updatedAt = now
        )
        epicDao.upsertEpic(epic.toEntity())
        markLocalDirty()
        return epic
    }

    suspend fun deleteEpic(epicId: String) {
        val all = epicDao.getAllEpicsIncludingDeleted()
        val target = all.firstOrNull { it.id == epicId }?.toDomain() ?: return
        val now = System.currentTimeMillis()
        epicDao.upsertEpic(target.copy(isDeleted = true, updatedAt = now).toEntity())
        markLocalDirty()
    }

    private suspend fun ensureEpicExists(name: String, now: Long) {
        if (epicDao.findByName(name) != null) return
        val epic = Epic(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAt = now,
            updatedAt = now
        )
        epicDao.upsertEpic(epic.toEntity())
    }

    suspend fun exportToJson(): String {
        val tasks = taskDao.getAllTasksIncludingDeleted().map { it.toDomain().toSnapshotDto() }
        val comments = commentDao.getAllCommentsIncludingDeleted().map { it.toDomain().toBackupDto() }
        val customers = customerDao.getAllCustomersIncludingDeleted().map { it.toDomain().toBackupDto() }
        val checksum = BackupPayload.computeDataChecksum(tasks, comments, customers, json)
        val payload = BackupPayload(
            exportedAt = System.currentTimeMillis(),
            appVersionName = BuildConfig.VERSION_NAME,
            tasks = tasks,
            comments = comments,
            customers = customers,
            checksumSha256 = checksum
        )
        return json.encodeToString(BackupPayload.serializer(), payload)
    }

    suspend fun createDumpFileForShare(): java.io.File {
        val content = exportToJson()
        val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        val file = java.io.File(dir, "personal_kanban_backup.json")
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    suspend fun exportToCsv(): String {
        val tasks = taskDao.getAllTasksIncludingDeleted()
            .map { it.toDomain() }
            .filter { !it.isDeleted }
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine("ID,Title,Status,Priority,Customer,Epic,DueAt,Labels,CreatedAt,CompletedAt,Archived")
        tasks.forEach { t ->
            fun esc(s: String?) = "\"${(s ?: "").replace("\"", "\"\"")}\""
            val dueStr = t.dueAt?.let { dateFmt.format(Date(it)) } ?: ""
            val createdStr = dateFmt.format(Date(t.createdAt))
            val completedStr = t.completedAt?.let { dateFmt.format(Date(it)) } ?: ""
            sb.appendLine(
                listOf(
                    esc(t.id),
                    esc(t.title),
                    esc(t.status.displayName),
                    esc(t.priority.displayName),
                    esc(t.customerName),
                    esc(t.epicName),
                    esc(dueStr),
                    esc(t.labels.joinToString(";")),
                    esc(createdStr),
                    esc(completedStr),
                    t.isArchived.toString()
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    suspend fun importFromJson(rawJson: String): Result<Int> = runCatching {
        val payload = json.decodeFromString(BackupPayload.serializer(), rawJson)
        if (payload.checksumSha256.isNotBlank()) {
            val expected = BackupPayload.computeDataChecksum(
                payload.tasks, payload.comments, payload.customers, json
            )
            require(expected.equals(payload.checksumSha256, ignoreCase = true)) {
                "Контрольная сумма файла импорта не совпадает"
            }
        }
        taskDao.upsertTasks(payload.tasks.map { it.toDomain().toEntity() })
        commentDao.upsertComments(payload.comments.map { it.toDomain().toEntity() })
        customerDao.upsertCustomers(payload.customers.map { it.toDomain().toEntity() })
        val now = System.currentTimeMillis()
        payload.tasks.forEach { dto ->
            dto.customerName?.trim()?.takeIf { it.isNotEmpty() }?.let { ensureCustomerExists(it, now) }
            dto.epicName?.trim()?.takeIf { it.isNotEmpty() }?.let { ensureEpicExists(it, now) }
        }
        reminderScheduler.rescheduleAll()
        markLocalDirty()
        KanbanWidgetProvider.requestUpdate(context)
        payload.tasks.size
    }

    suspend fun addAttachment(taskId: String, uri: Uri): TaskAttachment? {
        val existing = taskDao.getTaskById(taskId)?.toDomain() ?: return null
        val now = System.currentTimeMillis()
        val attachment = copyUriToAttachment(taskId, uri, now) ?: return null
        val updated = existing.copy(
            attachments = existing.attachments + attachment,
            updatedAt = now,
            version = existing.version + 1
        )
        taskDao.upsertTask(updated.toEntity())
        markLocalDirty()
        return attachment
    }

    suspend fun removeAttachment(taskId: String, attachmentId: String) {
        val existing = taskDao.getTaskById(taskId)?.toDomain() ?: return
        val target = existing.attachments.firstOrNull { it.id == attachmentId } ?: return
        runCatching { java.io.File(target.localPath).delete() }
        val now = System.currentTimeMillis()
        val updated = existing.copy(
            attachments = existing.attachments.filterNot { it.id == attachmentId },
            updatedAt = now,
            version = existing.version + 1
        )
        taskDao.upsertTask(updated.toEntity())
        markLocalDirty()
    }

    private fun copyUriToAttachment(taskId: String, uri: Uri, now: Long): TaskAttachment? {
        return runCatching {
            val cr = context.contentResolver
            val mimeType = cr.getType(uri) ?: "application/octet-stream"
            var displayName = "attachment_${now}"
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        cursor.getString(idx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                }
            }
            val safeName = displayName.replace(Regex("[^a-zA-Zа-яА-Я0-9._-]"), "_")
            val attId = UUID.randomUUID().toString()
            val dir = java.io.File(context.filesDir, "attachments/$taskId").apply { mkdirs() }
            val destFile = java.io.File(dir, "${attId}_$safeName")

            cr.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@runCatching null

            TaskAttachment(
                id = attId,
                displayName = displayName,
                mimeType = mimeType,
                localPath = destFile.absolutePath,
                sizeBytes = destFile.length(),
                addedAt = now
            )
        }.getOrNull()
    }

    private suspend fun ensureCustomerExists(name: String, now: Long): String {
        val existing = customerDao.findByName(name)
        if (existing != null) return existing.id
        val customer = Customer(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAt = now,
            updatedAt = now
        )
        customerDao.upsertCustomer(customer.toEntity())
        return customer.id
    }

    private suspend fun touchTaskUpdatedAt(taskId: String, now: Long) {
        val existing = taskDao.getTaskById(taskId)?.toDomain() ?: return
        taskDao.upsertTask(
            existing.copy(
                updatedAt = now,
                version = existing.version + 1
            ).toEntity()
        )
    }

    private suspend fun markLocalDirty() {
        val settings = settingsDataStore.getSettings()
        if (settings.googleAccountEmail != null && settings.lastSyncStatus != SyncStatusType.SYNCING) {
            settingsDataStore.updateSyncState(SyncStatusType.UNSYNCED)
            DriveSyncWorker.scheduleDebouncedSync(workManager)
        }
        val backupUriStr = settingsDataStore.backupFileUriFlow.first()
        if (!backupUriStr.isNullOrBlank()) {
            writeBackupToSafUri(Uri.parse(backupUriStr))
        }
    }

    suspend fun writeBackupToSafUri(uri: Uri): Result<Unit> = runCatching {
        val content = exportToJson()
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
        } ?: error("Не удалось открыть файл резервной копии")
        settingsDataStore.updateSyncState(
            SyncStatusType.SYNCED,
            lastSyncAt = System.currentTimeMillis()
        )
    }
}
