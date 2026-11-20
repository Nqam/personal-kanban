package com.personalkanban.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.personalkanban.app.BuildConfig
import com.personalkanban.app.data.local.CommentDao
import com.personalkanban.app.data.local.CustomerDao
import com.personalkanban.app.data.local.SettingsDataStore
import com.personalkanban.app.data.local.SyncConflictDao
import com.personalkanban.app.data.local.TaskDao
import com.personalkanban.app.data.local.toDomain
import com.personalkanban.app.data.local.toEntity
import com.personalkanban.app.data.local.toSnapshotDto
import com.personalkanban.app.data.remote.BackupPayload
import com.personalkanban.app.data.remote.DriveAuthException
import com.personalkanban.app.data.remote.GoogleDriveService
import com.personalkanban.app.data.remote.toBackupDto
import com.personalkanban.app.data.remote.toDomain
import com.personalkanban.app.domain.model.BackupSnapshotInfo
import com.personalkanban.app.domain.model.SyncConflict
import com.personalkanban.app.domain.model.SyncMergeEngine
import com.personalkanban.app.domain.model.SyncStatusType
import com.personalkanban.app.notification.ReminderScheduler
import com.personalkanban.app.widget.KanbanWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

enum class ConflictResolutionChoice {
    KEEP_LOCAL,
    USE_REMOTE
}

@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
    private val commentDao: CommentDao,
    private val customerDao: CustomerDao,
    private val syncConflictDao: SyncConflictDao,
    private val settingsDataStore: SettingsDataStore,
    private val driveService: GoogleDriveService,
    private val reminderScheduler: ReminderScheduler,
    private val json: Json
) {
    private val syncMutex = Mutex()

    fun observeUnresolvedConflicts(): Flow<List<SyncConflict>> =
        syncConflictDao.observeUnresolvedConflicts().map { list -> list.map { it.toDomain() } }

    suspend fun syncNow(createSnapshot: Boolean = false): Result<Int> = syncMutex.withLock {
        val settings = settingsDataStore.getSettings()
        val email = settings.googleAccountEmail
        if (email.isNullOrBlank()) {
            settingsDataStore.updateSyncState(SyncStatusType.NOT_CONNECTED)
            return Result.failure(IllegalStateException("Google-аккаунт не подключён"))
        }

        if (!isNetworkAvailable()) {
            settingsDataStore.updateSyncState(
                SyncStatusType.OFFLINE,
                error = "Нет подключения к интернету"
            )
            return Result.failure(IllegalStateException("Нет подключения к интернету"))
        }

        settingsDataStore.updateSyncState(SyncStatusType.SYNCING)

        return try {
            val folderId = driveService.ensureAppFolder(email, settings.driveFolderId)
            if (folderId != settings.driveFolderId) {
                settingsDataStore.setDriveFolderId(folderId)
            }

            val remotePayload = driveService.downloadLatestBackup(email, folderId)
            if (remotePayload != null) {
                verifyPayloadIntegrity(remotePayload)
            }

            val localTasks = taskDao.getAllTasksIncludingDeleted().map { it.toDomain() }
            val localComments = commentDao.getAllCommentsIncludingDeleted().map { it.toDomain() }
            val localCustomers = customerDao.getAllCustomersIncludingDeleted().map { it.toDomain() }

            val syncTime = System.currentTimeMillis()
            val mergeResult = if (remotePayload != null) {
                SyncMergeEngine.merge(
                    localTasks = localTasks,
                    remoteTasks = remotePayload.tasks.map { it.toDomain() },
                    localComments = localComments,
                    remoteComments = remotePayload.comments.map { it.toDomain() },
                    localCustomers = localCustomers,
                    remoteCustomers = remotePayload.customers.map { it.toDomain() },
                    syncTimestamp = syncTime
                )
            } else {
                SyncMergeEngine.merge(
                    localTasks = localTasks,
                    remoteTasks = emptyList(),
                    localComments = localComments,
                    remoteComments = emptyList(),
                    localCustomers = localCustomers,
                    remoteCustomers = emptyList(),
                    syncTimestamp = syncTime
                )
            }

            taskDao.upsertTasks(mergeResult.mergedTasks.map { it.toEntity() })
            commentDao.upsertComments(mergeResult.mergedComments.map { it.toEntity() })
            customerDao.upsertCustomers(mergeResult.mergedCustomers.map { it.toEntity() })

            if (mergeResult.conflicts.isNotEmpty()) {
                syncConflictDao.upsertConflicts(mergeResult.conflicts.map { it.toEntity() })
            }

            val mergedTaskDtos = mergeResult.mergedTasks.map { it.toSnapshotDto() }
            val mergedCommentDtos = mergeResult.mergedComments.map { it.toBackupDto() }
            val mergedCustomerDtos = mergeResult.mergedCustomers.map { it.toBackupDto() }
            val checksum = BackupPayload.computeDataChecksum(
                mergedTaskDtos, mergedCommentDtos, mergedCustomerDtos, json
            )

            val uploadPayload = BackupPayload(
                exportedAt = syncTime,
                appVersionName = BuildConfig.VERSION_NAME,
                tasks = mergedTaskDtos,
                comments = mergedCommentDtos,
                customers = mergedCustomerDtos,
                checksumSha256 = checksum
            )

            driveService.uploadBackup(
                accountEmail = email,
                folderId = folderId,
                payload = uploadPayload,
                createSnapshot = createSnapshot
            )

            reminderScheduler.rescheduleAll()
            KanbanWidgetProvider.requestUpdate(context)
            settingsDataStore.updateSyncState(SyncStatusType.SYNCED, lastSyncAt = syncTime)
            Result.success(mergeResult.conflicts.size)
        } catch (e: DriveAuthException) {
            settingsDataStore.updateSyncState(
                SyncStatusType.ERROR,
                error = e.message ?: "Ошибка авторизации Google Drive"
            )
            Result.failure(e)
        } catch (e: Exception) {
            settingsDataStore.updateSyncState(
                SyncStatusType.ERROR,
                error = e.localizedMessage ?: "Ошибка синхронизации"
            )
            Result.failure(e)
        }
    }

    suspend fun listSnapshots(): Result<List<BackupSnapshotInfo>> {
        val settings = settingsDataStore.getSettings()
        val email = settings.googleAccountEmail ?: return Result.failure(IllegalStateException("Google-аккаунт не подключён"))
        return runCatching {
            val folderId = driveService.ensureAppFolder(email, settings.driveFolderId)
            driveService.listBackupSnapshots(email, folderId)
        }
    }

    suspend fun verifyLatestBackupIntegrity(): Result<String> {
        val settings = settingsDataStore.getSettings()
        val email = settings.googleAccountEmail ?: return Result.failure(IllegalStateException("Google-аккаунт не подключён"))
        return runCatching {
            val folderId = driveService.ensureAppFolder(email, settings.driveFolderId)
            val payload = driveService.downloadLatestBackup(email, folderId)
                ?: throw IllegalStateException("Резервная копия в Google Drive не найдена")
            verifyPayloadIntegrity(payload)
            "Резервная копия цела: задач ${payload.tasks.size}, комментариев ${payload.comments.size}, схема v${payload.schemaVersion}"
        }
    }

    suspend fun restoreFromDriveFile(fileId: String?, forceOverwriteNewerLocal: Boolean = false): Result<Int> {
        val settings = settingsDataStore.getSettings()
        val email = settings.googleAccountEmail ?: return Result.failure(IllegalStateException("Google-аккаунт не подключён"))
        return runCatching {
            val folderId = driveService.ensureAppFolder(email, settings.driveFolderId)
            val payload = if (fileId != null) {
                driveService.downloadBackupByFileId(email, fileId)
            } else {
                driveService.downloadLatestBackup(email, folderId)
                    ?: throw IllegalStateException("Резервная копия не найдена")
            }
            verifyPayloadIntegrity(payload)

            if (!forceOverwriteNewerLocal) {
                val unsynced = taskDao.getUnsyncedCount()
                if (unsynced > 0) {
                    throw IllegalStateException("LOCAL_NEWER_EXISTS:$unsynced")
                }
            }

            val syncTime = System.currentTimeMillis()
            taskDao.upsertTasks(payload.tasks.map { it.toDomain().copy(lastSyncedAt = syncTime).toEntity() })
            commentDao.upsertComments(payload.comments.map { it.toDomain().copy(lastSyncedAt = syncTime).toEntity() })
            customerDao.upsertCustomers(payload.customers.map { it.toDomain().toEntity() })
            syncConflictDao.clearAll()
            reminderScheduler.rescheduleAll()
            KanbanWidgetProvider.requestUpdate(context)
            settingsDataStore.updateSyncState(SyncStatusType.SYNCED, lastSyncAt = syncTime)
            payload.tasks.size
        }
    }

    suspend fun resolveConflict(conflict: SyncConflict, choice: ConflictResolutionChoice) {
        val now = System.currentTimeMillis()
        val chosen = when (choice) {
            ConflictResolutionChoice.KEEP_LOCAL -> conflict.localTask
            ConflictResolutionChoice.USE_REMOTE -> conflict.remoteTask
        }.copy(
            updatedAt = now,
            version = maxOf(conflict.localTask.version, conflict.remoteTask.version) + 1
        )
        taskDao.upsertTask(chosen.toEntity())
        syncConflictDao.markResolved(conflict.id)
        reminderScheduler.scheduleForTask(chosen)
        KanbanWidgetProvider.requestUpdate(context)
    }

    private fun verifyPayloadIntegrity(payload: BackupPayload) {
        if (payload.schemaVersion > BackupPayload.CURRENT_SCHEMA_VERSION) {
            throw IllegalStateException("Неподдерживаемая версия схемы резервной копии: v${payload.schemaVersion}")
        }
        if (payload.checksumSha256.isNotBlank()) {
            val expected = BackupPayload.computeDataChecksum(
                payload.tasks, payload.comments, payload.customers, json
            )
            if (!expected.equals(payload.checksumSha256, ignoreCase = true)) {
                throw IllegalStateException("Повреждение резервной копии: контрольная сумма SHA-256 не совпадает")
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
