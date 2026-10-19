package me.kafuuneko.rpclient.libs.assistant

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 会话列表里的一条摘要。 */
data class AssistantSessionSummary(
    val id: String,
    /** 展示标题：取首条用户消息，取不到时用固定占位。 */
    val title: String,
    /** 消息条数。 */
    val messageCount: Int,
    /** 最后写入时间，用于排序。 */
    val updatedAt: Long,
    /** 是否已归档。 */
    val archived: Boolean
)

/** 新建会话的结果。 */
sealed class AssistantSessionCreateResult {
    data class Created(val id: String) : AssistantSessionCreateResult()

    /** 已达上限，[limit] 为允许的最大会话数。 */
    data class LimitReached(val limit: Int) : AssistantSessionCreateResult()
}

/**
 * 助手会话存储。
 *
 * 从单会话升级为多会话后，一个会话一个 JSON 文件，放在 `assistant/sessions/`：
 * - 归档是把文件挪进 `archived/` 子目录，不删除内容，随时可以恢复；
 * - 清理是真正删除文件；
 * - 会话总数上限 [MAX_SESSIONS]，由轻量化助手的定位决定，避免无限堆积占磁盘。
 *
 * 早期的单会话文件 `assistant/session.json` 会在首次访问时迁移成正式会话，
 * 用户升级后历史不会丢。
 */
class AssistantSessionStore(private val mContext: Context) {
    /** 沙箱根目录：助手唯一被允许读写的文件区域。 */
    val sandboxRoot: File by lazy {
        File(mContext.filesDir, "assistant/sandbox").apply { mkdirs() }
    }

    /** 活跃会话目录。 */
    private val mSessionDir: File by lazy {
        File(mContext.filesDir, "assistant/sessions").apply { mkdirs() }
    }

    /** 归档会话目录。 */
    private val mArchivedDir: File by lazy {
        File(mContext.filesDir, "assistant/archived").apply { mkdirs() }
    }

    /** 旧版单会话文件，仅用于迁移。 */
    private val mLegacyFile: File by lazy {
        File(mContext.filesDir, "assistant/session.json")
    }

    /**
     * 列出全部会话，按更新时间倒序；归档的排在最后。
     *
     * @param includeArchived 是否包含归档会话
     */
    suspend fun listSessions(includeArchived: Boolean = true): List<AssistantSessionSummary> =
        withContext(Dispatchers.IO) {
            migrateLegacyIfNeeded()
            val active = mSessionDir.listFiles { file -> file.isFile && file.extension == "json" }
                .orEmpty()
                .mapNotNull { summarize(it, archived = false) }
            val archived = if (includeArchived) {
                mArchivedDir.listFiles { file -> file.isFile && file.extension == "json" }
                    .orEmpty()
                    .mapNotNull { summarize(it, archived = true) }
            } else {
                emptyList()
            }
            // 归档的沉底，同级按更新时间倒序
            (active.sortedByDescending { it.updatedAt } + archived.sortedByDescending { it.updatedAt })
        }

    /**
     * 新建会话。
     *
     * 上限只统计活跃会话：归档的不占额度，否则用户想开新会话就必须删旧的。
     */
    suspend fun createSession(): AssistantSessionCreateResult = withContext(Dispatchers.IO) {
        migrateLegacyIfNeeded()
        val activeFiles = mSessionDir.listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy { it.lastModified() }
        // 满额时自动归档最久未更新的会话，腾出名额而不是拒绝创建；
        // 归档只搬目录不删内容，用户随时可以恢复。
        var archivedCount = 0
        while (activeFiles.size - archivedCount >= MAX_SESSIONS) {
            val oldest = activeFiles.getOrNull(archivedCount) ?: break
            if (!runCatching { oldest.renameTo(File(mArchivedDir, oldest.name)) }.getOrDefault(false)) break
            archivedCount++
        }
        val id = "session-" + System.currentTimeMillis()
        File(mSessionDir, "$id.json").writeText("[]")
        AssistantSessionCreateResult.Created(id)
    }

    /** 读取指定会话的消息；缺失或损坏时返回空列表。 */
    suspend fun loadSession(sessionId: String): List<AssistantMessage> = withContext(Dispatchers.IO) {
        migrateLegacyIfNeeded()
        val file = resolveFile(sessionId) ?: return@withContext emptyList()
        runCatching {
            val type = object : TypeToken<List<AssistantMessage>>() {}.type
            val parsed: List<AssistantMessage>? = Gson().fromJson(file.readText(), type)
            parsed ?: emptyList()
        }.getOrElse { emptyList() }
    }

    /** 覆盖写入指定会话。 */
    suspend fun saveSession(sessionId: String, messages: List<AssistantMessage>) =
        withContext(Dispatchers.IO) {
            migrateLegacyIfNeeded()
            val file = resolveFile(sessionId) ?: File(mSessionDir, "$sessionId.json")
            file.parentFile?.mkdirs()
            file.writeText(Gson().toJson(messages))
        }

    /** 归档会话：移到归档目录，内容保留。 */
    suspend fun archiveSession(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        val source = File(mSessionDir, "$sessionId.json")
        if (!source.isFile) return@withContext false
        runCatching {
            source.renameTo(File(mArchivedDir, "$sessionId.json"))
        }.getOrDefault(false)
    }

    /** 恢复归档会话：移回活跃目录。 */
    suspend fun restoreSession(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        val source = File(mArchivedDir, "$sessionId.json")
        if (!source.isFile) return@withContext false
        val activeCount = mSessionDir.listFiles { file -> file.isFile && file.extension == "json" }
            ?.size ?: 0
        if (activeCount >= MAX_SESSIONS) return@withContext false
        runCatching {
            source.renameTo(File(mSessionDir, "$sessionId.json"))
        }.getOrDefault(false)
    }

    /** 彻底删除会话（含归档中的）。 */
    suspend fun deleteSession(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        val active = File(mSessionDir, "$sessionId.json")
        val archived = File(mArchivedDir, "$sessionId.json")
        val removedActive = active.isFile && runCatching { active.delete() }.getOrDefault(false)
        val removedArchived = archived.isFile && runCatching { archived.delete() }.getOrDefault(false)
        removedActive || removedArchived
    }

    /** 取当前会话数，供界面展示额度。 */
    suspend fun activeSessionCount(): Int = withContext(Dispatchers.IO) {
        mSessionDir.listFiles { file -> file.isFile && file.extension == "json" }?.size ?: 0
    }

    /** 按 ID 找文件，先活跃、后归档。 */
    private fun resolveFile(sessionId: String): File? {
        val active = File(mSessionDir, "$sessionId.json")
        if (active.isFile) return active
        val archived = File(mArchivedDir, "$sessionId.json")
        return if (archived.isFile) archived else null
    }

    /** 生成摘要；第一次读取时把内容解析出来取标题。 */
    private fun summarize(file: File, archived: Boolean): AssistantSessionSummary? {
        val messages = runCatching {
            val type = object : TypeToken<List<AssistantMessage>>() {}.type
            Gson().fromJson<List<AssistantMessage>>(file.readText(), type).orEmpty()
        }.getOrElse { emptyList() }
        val firstUser = messages.firstOrNull { it.role == ROLE_USER }?.content.orEmpty()
        return AssistantSessionSummary(
            id = file.nameWithoutExtension,
            title = firstUser.take(24).ifBlank { EMPTY_TITLE },
            messageCount = messages.size,
            updatedAt = file.lastModified(),
            archived = archived
        )
    }

    /** 把旧版单会话迁移成正式会话，只做一次。 */
    private fun migrateLegacyIfNeeded() {
        if (!mLegacyFile.isFile) return
        runCatching {
            val target = File(mSessionDir, "session-legacy.json")
            if (!target.exists()) {
                mLegacyFile.renameTo(target)
            } else {
                // 目标已存在说明迁移过了，旧文件直接清掉避免重复迁移
                mLegacyFile.delete()
            }
        }
    }

    private companion object {
        /** 活跃会话数量上限：轻量化助手不需要无限堆历史。 */
        const val MAX_SESSIONS = 8

        /** 空会话的标题占位。 */
        const val EMPTY_TITLE = "新会话"

        /** 用户消息在存档里的角色标记。 */
        const val ROLE_USER = "user"
    }
}
