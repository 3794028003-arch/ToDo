package com.example.localfirst.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE tasks ADD COLUMN serverDeletionNoticePending INTEGER NOT NULL DEFAULT 0",
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE tasks ADD COLUMN serverDeletionNoticeSequence INTEGER",
        )
        db.execSQL(
            """
            UPDATE tasks
            SET serverDeletionNoticeSequence = (
                SELECT COUNT(*)
                FROM tasks AS earlier
                WHERE earlier.serverDeletionNoticePending = 1
                  AND earlier.id <= tasks.id
            )
            WHERE serverDeletionNoticePending = 1
            """.trimIndent(),
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE tasks ADD COLUMN createdSequence INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE tasks ADD COLUMN lastModifiedSequence INTEGER",
        )
        db.execSQL(
            """
            UPDATE tasks
            SET createdSequence = COALESCE(
                (
                    SELECT MIN(queueSequence)
                    FROM sync_operations
                    WHERE sync_operations.taskId = tasks.id
                      AND sync_operations.type = 'CREATE'
                ),
                (
                    SELECT MIN(queueSequence)
                    FROM sync_operations
                    WHERE sync_operations.taskId = tasks.id
                ),
                tasks.rowid
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            UPDATE tasks
            SET lastModifiedSequence = (
                SELECT MAX(queueSequence)
                FROM sync_operations
                WHERE sync_operations.taskId = tasks.id
                  AND sync_operations.type IN ('UPDATE', 'CHANGE_STATUS')
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN reminderAtMillis INTEGER")
        db.execSQL("ALTER TABLE sync_operations ADD COLUMN reminderAtMillis INTEGER")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sync_operations ADD COLUMN isPinned INTEGER")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE tasks ADD COLUMN reminderRepeat TEXT NOT NULL DEFAULT 'NONE'",
        )
        db.execSQL("ALTER TABLE sync_operations ADD COLUMN reminderRepeat TEXT")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN startDateMillis INTEGER")
        db.execSQL("ALTER TABLE tasks ADD COLUMN dueDateMillis INTEGER")
        db.execSQL("ALTER TABLE sync_operations ADD COLUMN startDateMillis INTEGER")
        db.execSQL("ALTER TABLE sync_operations ADD COLUMN dueDateMillis INTEGER")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE tasks ADD COLUMN permanentDeletionRequested INTEGER NOT NULL DEFAULT 0",
        )
    }
}
