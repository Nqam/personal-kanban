package com.personalkanban.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TaskEntity::class,
        CommentEntity::class,
        CustomerEntity::class,
        SyncConflictEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class PersonalKanbanDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun commentDao(): CommentDao
    abstract fun customerDao(): CustomerDao
    abstract fun syncConflictDao(): SyncConflictDao
}
