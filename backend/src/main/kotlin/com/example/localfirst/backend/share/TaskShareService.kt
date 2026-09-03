package com.example.localfirst.backend.share

import com.example.localfirst.backend.auth.scopedTaskId
import com.example.localfirst.backend.auth.taskPrefix
import com.example.localfirst.backend.auth.unscopedTaskId
import com.example.localfirst.backend.sync.ServerTask
import com.example.localfirst.backend.sync.ServerTaskStatus
import com.example.localfirst.backend.sync.ServerTaskStore
import java.security.SecureRandom
import java.time.Clock
import java.util.UUID
import org.springframework.stereotype.Service

interface TaskShareStore {
    fun save(
        code: String,
        ownerUserId: String,
        tasks: List<ServerTask>,
        createdAtMillis: Long,
        expiresAtMillis: Long,
    )

    fun load(code: String, nowMillis: Long): List<ServerTask>?
}

@Service
class TaskShareService(
    private val tasks: ServerTaskStore,
    private val shares: TaskShareStore,
    private val clock: Clock,
    private val codeFactory: () -> String = ::shareCode,
    private val taskIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun upload(userId: String): String {
        val now = clock.millis()
        val code = codeFactory()
        val snapshot = tasks.listByPrefix(taskPrefix(userId)).map {
            it.copy(id = unscopedTaskId(userId, it.id))
        }
        shares.save(code, userId, snapshot, now, now + SHARE_VALIDITY_MILLIS)
        return code
    }

    fun download(userId: String, code: String): List<ServerTask> {
        val snapshot = shares.load(code.trim().uppercase(), clock.millis())
            ?: throw IllegalArgumentException("分享码无效或已过期")
        return snapshot.map { source ->
            val localId = taskIdFactory()
            val scopedId = scopedTaskId(userId, localId)
            var created = tasks.create(
                scopedId,
                source.title,
                source.reminderAtMillis,
                source.reminderRepeat,
                source.isPinned,
                source.startDateMillis,
                source.dueDateMillis,
            )
            if (source.status != ServerTaskStatus.TODO) {
                created = tasks.changeStatus(scopedId, source.status, created.version) ?: created
            }
            created.copy(id = localId)
        }
    }

    companion object {
        const val SHARE_VALIDITY_MILLIS = 604_800_000L
    }
}

private const val SHARE_CODE_LENGTH = 8
private const val SHARE_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

private fun shareCode(): String = buildString(SHARE_CODE_LENGTH) {
    val random = SecureRandom()
    repeat(SHARE_CODE_LENGTH) {
        append(SHARE_CODE_ALPHABET[random.nextInt(SHARE_CODE_ALPHABET.length)])
    }
}
