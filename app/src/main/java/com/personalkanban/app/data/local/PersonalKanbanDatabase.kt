package com.personalkanban.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        CommentEntity::class,
        CustomerEntity::class,
        EpicEntity::class,
        SyncConflictEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class PersonalKanbanDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN attachmentsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN epicName TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS epics (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_epics_name ON epics (name)")
            }
        }
    }

    abstract fun taskDao(): TaskDao
    abstract fun commentDao(): CommentDao
    abstract fun customerDao(): CustomerDao
    abstract fun epicDao(): EpicDao
    abstract fun syncConflictDao(): SyncConflictDao
}
