package com.personalkanban.app

import com.personalkanban.app.domain.model.Customer
import com.personalkanban.app.domain.model.SyncMergeEngine
import com.personalkanban.app.domain.model.TaskComment
import com.personalkanban.app.domain.model.TaskItem
import com.personalkanban.app.domain.model.TaskPriority
import com.personalkanban.app.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeEngineTest {

    private fun baseTask(
        id: String,
        title: String = "Task $id",
        updatedAt: Long = 1000L,
        lastSyncedAt: Long = 1000L,
        isDeleted: Boolean = false,
        status: TaskStatus = TaskStatus.TODO,
        priority: TaskPriority = TaskPriority.MEDIUM
    ) = TaskItem(
        id = id,
        title = title,
        status = status,
        priority = priority,
        createdAt = 500L,
        updatedAt = updatedAt,
        lastSyncedAt = lastSyncedAt,
        isDeleted = isDeleted
    )

    @Test
    fun `offline created task and remote only task both survive merge`() {
        val localOnly = baseTask("local-1", updatedAt = 2000L, lastSyncedAt = 0L)
        val remoteOnly = baseTask("remote-1", updatedAt = 1500L, lastSyncedAt = 1500L)

        val result = SyncMergeEngine.merge(
            localTasks = listOf(localOnly),
            remoteTasks = listOf(remoteOnly),
            localComments = emptyList(),
            remoteComments = emptyList(),
            localCustomers = emptyList(),
            remoteCustomers = emptyList(),
            syncTimestamp = 3000L
        )

        assertEquals(setOf("local-1", "remote-1"), result.mergedTasks.map { it.id }.toSet())
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `offline deletion tombstone propagates when remote was not modified`() {
        val localDeleted = baseTask("t1", updatedAt = 2000L, lastSyncedAt = 1000L, isDeleted = true)
        val remoteOld = baseTask("t1", updatedAt = 1000L, lastSyncedAt = 1000L, isDeleted = false)

        val result = SyncMergeEngine.merge(
            localTasks = listOf(localDeleted),
            remoteTasks = listOf(remoteOld),
            localComments = emptyList(),
            remoteComments = emptyList(),
            localCustomers = emptyList(),
            remoteCustomers = emptyList(),
            syncTimestamp = 3000L
        )

        assertEquals(1, result.mergedTasks.size)
        assertTrue(result.mergedTasks.first().isDeleted)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `concurrent edits on both sides create conflict and preserve local`() {
        val localEdited = baseTask("t1", title = "Local Title", updatedAt = 2000L, lastSyncedAt = 1000L)
        val remoteEdited = baseTask("t1", title = "Remote Title", updatedAt = 2500L, lastSyncedAt = 1000L)

        val result = SyncMergeEngine.merge(
            localTasks = listOf(localEdited),
            remoteTasks = listOf(remoteEdited),
            localComments = listOf(TaskComment("c1", "t1", "Local comment", 1100L, 2100L)),
            remoteComments = listOf(TaskComment("c2", "t1", "Remote comment", 1200L, 2200L)),
            localCustomers = listOf(Customer("cust1", "Acme", 1000L, 1000L)),
            remoteCustomers = emptyList(),
            syncTimestamp = 3000L
        )

        assertEquals(1, result.conflicts.size)
        assertEquals("Local Title", result.mergedTasks.first().title)
        assertEquals(2, result.mergedComments.size)
        assertEquals(1, result.mergedCustomers.size)
    }
}
