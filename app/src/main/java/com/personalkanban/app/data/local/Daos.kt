package com.personalkanban.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND isArchived = 0 ORDER BY updatedAt DESC")
    fun observeActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND isArchived = 1 ORDER BY updatedAt DESC")
    fun observeArchivedTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun observeDeletedTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    fun observeTaskById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksIncludingDeleted(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND isArchived = 0 AND dueAt IS NOT NULL AND reminderEnabled = 1 AND status != 'DONE'")
    suspend fun getTasksWithPendingReminders(): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE updatedAt > lastSyncedAt")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE updatedAt > lastSyncedAt")
    suspend fun getUnsyncedCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun hardDeleteTask(id: String)

    @Query("DELETE FROM tasks WHERE isDeleted = 1")
    suspend fun purgeDeletedTasks()
}

@Dao
interface CommentDao {
    @Query("SELECT * FROM comments WHERE taskId = :taskId AND isDeleted = 0 ORDER BY createdAt ASC")
    fun observeCommentsForTask(taskId: String): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments WHERE isDeleted = 0")
    fun observeAllActiveComments(): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments")
    suspend fun getAllCommentsIncludingDeleted(): List<CommentEntity>

    @Query("SELECT * FROM comments WHERE id = :id LIMIT 1")
    suspend fun getCommentById(id: String): CommentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComment(comment: CommentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComments(comments: List<CommentEntity>)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeActiveCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers")
    suspend fun getAllCustomersIncludingDeleted(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE LOWER(name) = LOWER(:name) AND isDeleted = 0 LIMIT 1")
    suspend fun findByName(name: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomer(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomers(customers: List<CustomerEntity>)
}

@Dao
interface SyncConflictDao {
    @Query("SELECT * FROM sync_conflicts WHERE resolved = 0 ORDER BY detectedAt DESC")
    fun observeUnresolvedConflicts(): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflicts WHERE resolved = 0")
    suspend fun getUnresolvedConflicts(): List<SyncConflictEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConflicts(conflicts: List<SyncConflictEntity>)

    @Query("UPDATE sync_conflicts SET resolved = 1 WHERE id = :conflictId")
    suspend fun markResolved(conflictId: String)

    @Query("DELETE FROM sync_conflicts")
    suspend fun clearAll()
}
