package com.example.localfirst.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "migration-${UUID.randomUUID()}.db"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationTwoToThreeBackfillsPendingNoticesInPreviousIdOrder() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE tasks (
                                id TEXT NOT NULL PRIMARY KEY,
                                serverDeletionNoticePending INTEGER NOT NULL DEFAULT 0
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            """
                            INSERT INTO tasks (id, serverDeletionNoticePending)
                            VALUES ('task-b', 1), ('task-a', 1), ('task-c', 0)
                            """.trimIndent(),
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                },
            )
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val database = helper.writableDatabase

        MIGRATION_2_3.migrate(database)

        val migratedRows = buildList {
            database.query(
                """
                SELECT id, serverDeletionNoticeSequence
                FROM tasks
                ORDER BY id
                """.trimIndent(),
            ).use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow("id")
                val sequenceColumn = cursor.getColumnIndexOrThrow("serverDeletionNoticeSequence")
                while (cursor.moveToNext()) {
                    add(
                        cursor.getString(idColumn) to
                            if (cursor.isNull(sequenceColumn)) null else cursor.getLong(sequenceColumn),
                    )
                }
            }
        }

        assertEquals(
            listOf(
                "task-a" to 1L,
                "task-b" to 2L,
                "task-c" to null,
            ),
            migratedRows,
        )
        helper.close()
    }

    @Test
    fun migrationThreeToFourBackfillsCreationAndEditOrderFromOutboxHistory() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE tasks (
                                id TEXT NOT NULL PRIMARY KEY,
                                serverDeletionNoticePending INTEGER NOT NULL DEFAULT 0,
                                serverDeletionNoticeSequence INTEGER
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            """
                            CREATE TABLE sync_operations (
                                operationId TEXT NOT NULL PRIMARY KEY,
                                taskId TEXT NOT NULL,
                                queueSequence INTEGER NOT NULL,
                                type TEXT NOT NULL
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            """
                            INSERT INTO tasks (id)
                            VALUES ('task-a'), ('task-b'), ('task-c')
                            """.trimIndent(),
                        )
                        db.execSQL(
                            """
                            INSERT INTO sync_operations (operationId, taskId, queueSequence, type)
                            VALUES
                                ('create-a', 'task-a', 10, 'CREATE'),
                                ('create-b', 'task-b', 20, 'CREATE'),
                                ('create-c', 'task-c', 30, 'CREATE'),
                                ('update-a', 'task-a', 40, 'UPDATE'),
                                ('status-b', 'task-b', 50, 'CHANGE_STATUS')
                            """.trimIndent(),
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                },
            )
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val database = helper.writableDatabase

        MIGRATION_3_4.migrate(database)

        val migratedRows = buildList {
            database.query(
                """
                SELECT id, createdSequence, lastModifiedSequence
                FROM tasks
                ORDER BY id
                """.trimIndent(),
            ).use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow("id")
                val createdColumn = cursor.getColumnIndexOrThrow("createdSequence")
                val modifiedColumn = cursor.getColumnIndexOrThrow("lastModifiedSequence")
                while (cursor.moveToNext()) {
                    add(
                        Triple(
                            cursor.getString(idColumn),
                            cursor.getLong(createdColumn),
                            if (cursor.isNull(modifiedColumn)) null else cursor.getLong(modifiedColumn),
                        ),
                    )
                }
            }
        }

        assertEquals(
            listOf(
                Triple("task-a", 10L, 40L),
                Triple("task-b", 20L, 50L),
                Triple("task-c", 30L, null),
            ),
            migratedRows,
        )
        helper.close()
    }

    @Test
    fun migrationFourToFiveAddsOptionalReminderColumnsWithoutChangingExistingRows() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE tasks (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)",
                        )
                        db.execSQL(
                            "CREATE TABLE sync_operations (operationId TEXT NOT NULL PRIMARY KEY)",
                        )
                        db.execSQL("INSERT INTO tasks (id, title) VALUES ('task-1', 'Existing')")
                        db.execSQL("INSERT INTO sync_operations (operationId) VALUES ('op-1')")
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                },
            )
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val database = helper.writableDatabase

        MIGRATION_4_5.migrate(database)

        database.query("SELECT reminderAtMillis FROM tasks WHERE id = 'task-1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(true, cursor.isNull(0))
        }
        database.query(
            "SELECT reminderAtMillis FROM sync_operations WHERE operationId = 'op-1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(true, cursor.isNull(0))
        }
        helper.close()
    }

    @Test
    fun migrationFiveToSixAddsPinColumnsWithExistingTasksUnpinned() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE tasks (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)",
                        )
                        db.execSQL(
                            "CREATE TABLE sync_operations (operationId TEXT NOT NULL PRIMARY KEY)",
                        )
                        db.execSQL("INSERT INTO tasks (id, title) VALUES ('task-1', 'Existing')")
                        db.execSQL("INSERT INTO sync_operations (operationId) VALUES ('op-1')")
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                },
            )
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val database = helper.writableDatabase

        MIGRATION_5_6.migrate(database)

        database.query("SELECT isPinned FROM tasks WHERE id = 'task-1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        database.query("SELECT isPinned FROM sync_operations WHERE operationId = 'op-1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(true, cursor.isNull(0))
        }
        helper.close()
    }

    @Test
    fun migrationSevenToEightAddsOptionalDateRangeWithoutChangingExistingTasks() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE tasks (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE sync_operations (operationId TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("INSERT INTO tasks (id, title) VALUES ('task-1', 'Existing')")
                        db.execSQL("INSERT INTO sync_operations (operationId) VALUES ('op-1')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val database = helper.writableDatabase

        MIGRATION_7_8.migrate(database)

        database.query("SELECT startDateMillis, dueDateMillis FROM tasks WHERE id = 'task-1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(true, cursor.isNull(0))
            assertEquals(true, cursor.isNull(1))
        }
        database.query("SELECT startDateMillis, dueDateMillis FROM sync_operations WHERE operationId = 'op-1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(true, cursor.isNull(0))
            assertEquals(true, cursor.isNull(1))
        }
        helper.close()
    }

    @Test
    fun migrationEightToNineKeepsExistingTrashVisible() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE tasks (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
                        db.execSQL("INSERT INTO tasks (id, title) VALUES ('task-1', 'Existing')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        val database = helper.writableDatabase

        MIGRATION_8_9.migrate(database)

        database.query("SELECT permanentDeletionRequested FROM tasks WHERE id = 'task-1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        helper.close()
    }
}
