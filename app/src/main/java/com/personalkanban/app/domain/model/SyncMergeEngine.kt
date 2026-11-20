package com.personalkanban.app.domain.model

import java.util.UUID

data class SyncMergeResult(
    val mergedTasks: List<TaskItem>,
    val mergedComments: List<TaskComment>,
    val mergedCustomers: List<Customer>,
    val conflicts: List<SyncConflict>
)

object SyncMergeEngine {

    fun merge(
        localTasks: List<TaskItem>,
        remoteTasks: List<TaskItem>,
        localComments: List<TaskComment>,
        remoteComments: List<TaskComment>,
        localCustomers: List<Customer>,
        remoteCustomers: List<Customer>,
        syncTimestamp: Long = System.currentTimeMillis()
    ): SyncMergeResult {
        val conflicts = mutableListOf<SyncConflict>()
        val mergedTasks = mergeTasks(localTasks, remoteTasks, syncTimestamp, conflicts)
        val mergedComments = mergeComments(localComments, remoteComments, syncTimestamp)
        val mergedCustomers = mergeCustomers(localCustomers, remoteCustomers)
        return SyncMergeResult(
            mergedTasks = mergedTasks,
            mergedComments = mergedComments,
            mergedCustomers = mergedCustomers,
            conflicts = conflicts
        )
    }

    private fun mergeTasks(
        localTasks: List<TaskItem>,
        remoteTasks: List<TaskItem>,
        syncTimestamp: Long,
        conflictsOut: MutableList<SyncConflict>
    ): List<TaskItem> {
        val localMap = localTasks.associateBy { it.id }
        val remoteMap = remoteTasks.associateBy { it.id }
        val allIds = (localMap.keys + remoteMap.keys).distinct()

        return allIds.map { id ->
            val local = localMap[id]
            val remote = remoteMap[id]

            when {
                local != null && remote == null -> {
                    local.copy(lastSyncedAt = syncTimestamp)
                }
                local == null && remote != null -> {
                    remote.copy(lastSyncedAt = syncTimestamp)
                }
                local != null && remote != null -> {
                    mergeExistingTask(local, remote, syncTimestamp, conflictsOut)
                }
                else -> error("Unreachable")
            }
        }
    }

    private fun mergeExistingTask(
        local: TaskItem,
        remote: TaskItem,
        syncTimestamp: Long,
        conflictsOut: MutableList<SyncConflict>
    ): TaskItem {
        val localModified = local.updatedAt > local.lastSyncedAt
        val remoteModified = remote.updatedAt > local.lastSyncedAt

        // Neither changed since last sync
        if (!localModified && !remoteModified) {
            return if (remote.updatedAt >= local.updatedAt) {
                remote.copy(lastSyncedAt = syncTimestamp)
            } else {
                local.copy(lastSyncedAt = syncTimestamp)
            }
        }

        // Only local changed
        if (localModified && !remoteModified) {
            return local.copy(lastSyncedAt = syncTimestamp)
        }

        // Only remote changed
        if (!localModified && remoteModified) {
            return remote.copy(lastSyncedAt = syncTimestamp)
        }

        // Both modified: check tombstones first
        if (local.isDeleted != remote.isDeleted) {
            // If one side deleted and the other modified after last sync, record conflict unless identical
            val newer = if (local.updatedAt >= remote.updatedAt) local else remote
            conflictsOut += SyncConflict(
                id = UUID.randomUUID().toString(),
                taskId = local.id,
                taskTitle = local.title,
                localTask = local,
                remoteTask = remote,
                conflictFields = listOf("Удаление / восстановление"),
                detectedAt = syncTimestamp
            )
            return newer.copy(lastSyncedAt = syncTimestamp, version = maxOf(local.version, remote.version) + 1)
        }

        // Both modified and neither deleted (or both deleted):
        // Detect field-level differences. If the exact same fields differ, we flag a conflict
        // while preserving local as active until user resolves (or auto-merging if identical).
        val conflictFields = mutableListOf<String>()
        if (local.title != remote.title) conflictFields += "Название"
        if (local.description != remote.description) conflictFields += "Описание"
        if (local.status != remote.status) conflictFields += "Статус"
        if (local.priority != remote.priority) conflictFields += "Приоритет"
        if (local.dueAt != remote.dueAt || local.hasTime != remote.hasTime) conflictFields += "Срок"
        if (local.customerName != remote.customerName) conflictFields += "Заказчик"
        if (local.labels != remote.labels) conflictFields += "Метки"
        if (local.isArchived != remote.isArchived) conflictFields += "Архив"

        if (conflictFields.isEmpty()) {
            return local.copy(
                lastSyncedAt = syncTimestamp,
                version = maxOf(local.version, remote.version)
            )
        }

        // Preserve local changes immediately (never silently lose user's offline work)
        // and surface SyncConflict so user can inspect or pick remote/merge.
        conflictsOut += SyncConflict(
            id = UUID.randomUUID().toString(),
            taskId = local.id,
            taskTitle = local.title,
            localTask = local,
            remoteTask = remote,
            conflictFields = conflictFields,
            detectedAt = syncTimestamp
        )

        return local.copy(
            lastSyncedAt = syncTimestamp,
            version = maxOf(local.version, remote.version) + 1
        )
    }

    private fun mergeComments(
        localComments: List<TaskComment>,
        remoteComments: List<TaskComment>,
        syncTimestamp: Long
    ): List<TaskComment> {
        val localMap = localComments.associateBy { it.id }
        val remoteMap = remoteComments.associateBy { it.id }
        val allIds = (localMap.keys + remoteMap.keys).distinct()

        return allIds.map { id ->
            val local = localMap[id]
            val remote = remoteMap[id]
            when {
                local != null && remote == null -> local.copy(lastSyncedAt = syncTimestamp)
                local == null && remote != null -> remote.copy(lastSyncedAt = syncTimestamp)
                local != null && remote != null -> {
                    val winner = if (local.updatedAt >= remote.updatedAt) local else remote
                    winner.copy(
                        lastSyncedAt = syncTimestamp,
                        version = maxOf(local.version, remote.version)
                    )
                }
                else -> error("Unreachable")
            }
        }
    }

    private fun mergeCustomers(
        localCustomers: List<Customer>,
        remoteCustomers: List<Customer>
    ): List<Customer> {
        val localMap = localCustomers.associateBy { it.id }
        val remoteMap = remoteCustomers.associateBy { it.id }
        val allIds = (localMap.keys + remoteMap.keys).distinct()

        return allIds.map { id ->
            val local = localMap[id]
            val remote = remoteMap[id]
            when {
                local != null && remote == null -> local
                local == null && remote != null -> remote
                local != null && remote != null -> if (local.updatedAt >= remote.updatedAt) local else remote
                else -> error("Unreachable")
            }
        }
    }
}
