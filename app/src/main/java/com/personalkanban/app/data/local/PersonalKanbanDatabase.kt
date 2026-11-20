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
    version = 2,
    exportSchema = true
)
abstract class PersonalKanbanDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN attachmentsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }
    }
    abstract fun taskDao(): TaskDao
    abstract fun commentDao(): CommentDao
    abstract fun customerDao(): CustomerDao
    abstract fun syncConflictDao(): SyncConflictDao
}
